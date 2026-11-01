package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * 本地朗读：逐段调用系统 TTS 合成音频文件，再用 ExoPlayer 按播放列表播放。
 * 时长与进度直接来自真实音频（与在线朗读同构），系统媒体卡片时间轴精确稳定；
 * 合成结果按 章节+引擎+语速+文本 缓存，重复朗读不重复合成。
 */
@SuppressLint("UnsafeOptInUsageError")
class TTSReadAloudService : BaseReadAloudService(), TextToSpeech.OnInitListener, Player.Listener {

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false
    private var synthesizeJob: Coroutine<*>? = null
    private var playErrorNo = 0
    private var synthErrorNo = 0
    private val synthSeq = AtomicInteger()
    private val TAG = "TTSReadAloudService"

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }
    private val ttsFolderPath: String by lazy {
        val path = cacheDir.absolutePath + File.separator + "sysTTS" + File.separator
        // 合成目标目录必须先建好，否则引擎写文件直接失败
        File(path).mkdirs()
        path
    }

    // 串行合成：同一时刻只有一个待完成的合成回调
    @Volatile
    private var synthPendingId: String? = null

    @Volatile
    private var synthContinuation: kotlin.coroutines.Continuation<Boolean>? = null

    private val ttsEngineName: String?
        get() = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value

    override fun onCreate() {
        super.onCreate()
        initTts()
        exoPlayer.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        synthesizeJob?.cancel()
        clearTTS()
        exoPlayer.release()
        Coroutine.async {
            removeCacheFile()
        }
    }

    @Synchronized
    fun clearTTS() {
        textToSpeech?.runCatching {
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitFinish = false
    }

    @Synchronized
    private fun initTts() {
        ttsInitFinish = false
        val engine = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
        LogUtils.d(TAG, "initTts engine:$engine")
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this, this)
        } else {
            TextToSpeech(this, this, engine)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let {
                it.setOnUtteranceProgressListener(synthUtteranceListener)
                ttsInitFinish = true
                upSpeechRate()
                play()
            }
        } else {
            toastOnUi(R.string.tts_init_failed)
            // 初始化失败继续挂着只会僵尸运行，停止服务
            stopSelf()
        }
    }

    @Synchronized
    override fun play() {
        if (!ttsInitFinish) return
        pageChanged = false
        exoPlayer.stop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
            return
        }
        super.play()
        synthesizeJob?.cancel()
        exoPlayer.clearMediaItems()
        synthesizeJob = execute {
            val tts = textToSpeech ?: throw NoStackTraceException("tts is null")
            LogUtils.d(TAG, "合成列表大小 ${contentList.size}")
            contentList.forEachIndexed { index, content ->
                ensureActive()
                if (index < nowSpeak) return@forEachIndexed
                var text = content
                if (paragraphStartPos > 0 && index == nowSpeak) {
                    text = text.substring(paragraphStartPos)
                }
                val speakText = text.replace(AppPattern.notReadAloudRegex, "")
                val fileName = md5SpeakFileName(text)
                if (!hasSpeakFile(fileName)) {
                    if (speakText.isEmpty()) {
                        createSilentSound(fileName)
                    } else {
                        val ok = synthesizeAwait(tts, speakText, createSynthTempFile(fileName))
                        if (!ok || !synthTempToTarget(fileName)) {
                            synthErrorNo++
                            if (synthErrorNo >= 5) {
                                AppLog.put("TTS合成连续5次错误，已暂停朗读")
                                toastOnUi("TTS合成连续5次错误，已暂停朗读")
                                pauseReadAloud()
                                return@execute
                            } else {
                                AppLog.put("TTS合成出错，本段将静音跳过。\n朗读文本：$speakText")
                                toastOnUi("TTS合成出错，本段将静音跳过")
                                createSilentSound(fileName)
                            }
                        } else {
                            synthErrorNo = 0
                        }
                    }
                }
                val file = getSpeakFile(fileName)
                launch(Main) {
                    exoPlayer.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                }
            }
            LogUtils.d(TAG, "合成内容添加完成")
        }.onError {
            AppLog.put("tts合成出错\n${it.localizedMessage}", it, true)
        }
    }

    override fun playStop() {
        synthesizeJob?.cancel()
        exoPlayer.stop()
    }

    /**
     * 合成一段文本到临时文件并等待完成；超时/出错返回 false
     */
    private suspend fun synthesizeAwait(
        tts: TextToSpeech,
        text: String,
        file: File
    ): Boolean {
        val ok = withTimeoutOrNull(60_000L) {
            suspendCancellableCoroutine { cont ->
                val id = AppConst.APP_TAG + synthSeq.incrementAndGet()
                synthPendingId = id
                synthContinuation = cont
                val result = tts.runCatching {
                    synthesizeToFile(text, Bundle(), file, id)
                }.getOrDefault(TextToSpeech.ERROR)
                if (result != TextToSpeech.SUCCESS) {
                    synthPendingId = null
                    synthContinuation = null
                    cont.resume(false)
                }
                cont.invokeOnCancellation {
                    synthPendingId = null
                    synthContinuation = null
                }
            }
        }
        if (ok == null) {
            // 超时：清理挂起状态，临时文件留给失败路径删除
            synthPendingId = null
            synthContinuation = null
            file.delete()
        }
        return ok ?: false
    }

    /**
     * 合成监听：按 utteranceId 唤醒等待的合成协程
     */
    private val synthUtteranceListener = object : UtteranceProgressListener() {

        private fun settle(id: String?, success: Boolean) {
            if (id == null || id != synthPendingId) return
            synthPendingId = null
            val cont = synthContinuation
            synthContinuation = null
            cont?.runCatching { resume(success) }
        }

        override fun onStart(s: String?) = Unit

        override fun onDone(s: String?) {
            settle(s, true)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(s: String?) {
            settle(s, false)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            settle(utteranceId, false)
        }
    }

    private fun updateNextPos() {
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        paragraphStartPos = 0
        if (nowSpeak < contentList.lastIndex) {
            nowSpeak++
        } else {
            nextChapter()
        }
    }

    /**
     * 段内进度跟踪：按音频时长比例估算字符位，跟随翻页并上报阅读位置
     */
    private fun upPlayPos() {
        val textChapter = textChapter ?: return
        lifecycleScope.launch {
            upTtsProgress(readAloudNumber + 1)
            if (exoPlayer.duration <= 0) {
                return@launch
            }
            val speakTextLength = contentList[nowSpeak].length
            if (speakTextLength <= 0) {
                return@launch
            }
            val sleep = exoPlayer.duration / speakTextLength
            val start = speakTextLength * exoPlayer.currentPosition / exoPlayer.duration
            for (i in start..contentList[nowSpeak].length) {
                if (readAloudNumber + i > textChapter.getReadLength(pageIndex + 1)) {
                    pageIndex++
                    if (pageIndex < textChapter.pageSize) {
                        ReadBook.moveToNextPage()
                        upTtsProgress(readAloudNumber + i.toInt())
                    }
                }
                delay(sleep)
            }
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        if (AppConfig.ttsFlowSys) {
            if (reset) {
                clearTTS()
                initTts()
                return
            }
        } else {
            textToSpeech?.setSpeechRate(speechRate)
        }
        // 语速参与合成缓存键，重建播放队列以应用新语速
        if (isRun) {
            synthesizeJob?.cancel()
            exoPlayer.stop()
            play()
        }
    }

    /**
     * 暂停朗读（合成继续后台预生成）
     */
    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        kotlin.runCatching {
            exoPlayer.pause()
        }
    }

    /**
     * 恢复朗读
     */
    override fun resumeReadAloud() {
        super.resumeReadAloud()
        kotlin.runCatching {
            if (pageChanged) {
                play()
            } else {
                exoPlayer.play()
                upPlayPos()
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_READY -> {
                if (pause) return
                exoPlayer.play()
                upPlayPos()
            }

            Player.STATE_ENDED -> {
                playErrorNo = 0
                updateNextPos()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }

            else -> Unit
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        when (reason) {
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> {
                if (!timeline.isEmpty && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
            }

            else -> {}
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorNo = 0
        }
        updateNextPos()
        upPlayPos()
        // 每段音频切换同步媒体进度
        trackReadingPace()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AppLog.put("朗读错误\n${contentList[nowSpeak]}", error)
        deleteCurrentSpeakFile()
        playErrorNo++
        if (playErrorNo >= 5) {
            toastOnUi("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})")
            AppLog.put("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})", error)
            pauseReadAloud()
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.clearMediaItems()
                updateNextPos()
            }
        }
    }

    private fun deleteCurrentSpeakFile() {
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val filePath = mediaItem.localConfiguration?.uri?.path ?: return
        FileUtils.delete(filePath)
    }

    private fun md5SpeakFileName(content: String): String {
        return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                MD5Utils.md5Encode16("sysTts-|-${ttsEngineName}-|-$speechRate-|-$content")
    }

    private val speechRate: Float
        get() = (AppConfig.ttsSpeechRate + 5) / 10f

    private fun hasSpeakFile(name: String): Boolean {
        return FileUtils.exist("${ttsFolderPath}$name.wav")
    }

    private fun getSpeakFile(name: String): File {
        return File("${ttsFolderPath}$name.wav")
    }

    private fun createSynthTempFile(name: String): File {
        return File("${ttsFolderPath}$name.wav.tmp")
    }

    /** 合成完成后临时文件转正，避免引擎中断留下半截文件被当成完整音频 */
    private fun synthTempToTarget(name: String): Boolean {
        val tmp = createSynthTempFile(name)
        return if (tmp.exists() && tmp.length() > 44) {
            val target = getSpeakFile(name)
            target.delete()
            tmp.renameTo(target)
        } else {
            tmp.delete()
            false
        }
    }

    private fun createSilentSound(name: String) {
        getSpeakFile(name).writeBytes(resources.openRawResource(R.raw.silent_sound).readBytes())
    }

    /**
     * 移除缓存文件
     */
    private fun removeCacheFile() {
        val titleMd5 = MD5Utils.md5Encode16(textChapter?.title ?: "")
        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            val isSilentSound = it.length() == 2160L
            if ((!it.name.startsWith(titleMd5)
                        && System.currentTimeMillis() - it.lastModified() > 600000)
                || isSilentSound
            ) {
                FileUtils.delete(it.absolutePath)
            }
        }
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<TTSReadAloudService>(actionStr)
    }

}
