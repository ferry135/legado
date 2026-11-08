package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
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
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
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
    private var synthRebuildCount = 0
    private val synthSeq = AtomicInteger()
    private val TAG = "TTSReadAloudService"

    /** synthesizeToFile 等待结果：Ok=成功，Error=引擎报错/入队失败，Timeout=回调丢失超时 */
    private enum class SynthResult { Ok, Error, Timeout }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build().apply {
            // 媒体用途+语音内容类型：与音乐播放器同走媒体音量曲线，系统DSP按语音处理
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false // 音频焦点由 BaseReadAloudService 统一管理
            )
        }
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
                if (!AppConfig.ttsFlowSys) {
                    it.setSpeechRate(speechRate)
                }
                // 重建引擎自愈可能发生在暂停中：暂停态只做标记，恢复时再续播
                if (!pause) play() else pageChanged = true
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
                val file: File = when {
                    // 过滤后无内容：本就该静音，结果确定，可按文本缓存
                    speakText.isEmpty() -> {
                        if (!hasSpeakFile(fileName)) createSilentSound(fileName)
                        getSpeakFile(fileName)
                    }

                    hasSpeakFile(fileName) -> getSpeakFile(fileName)

                    else -> {
                        val result = synthesizeAwait(tts, speakText, createSynthTempFile(fileName))
                        val target = getSpeakFile(fileName)
                        val ok = result == SynthResult.Ok &&
                                synthTempToTarget(fileName) &&
                                normalizeWavLoudness(target).also { audible ->
                                    if (!audible) target.delete()
                                }
                        if (ok) {
                            synthErrorNo = 0
                            synthRebuildCount = 0
                            // 单段音频精确时长 → 基准节奏校准（首段即准，后续持续精化）
                            onExactPaceSample(wavDurationMs(target), speakText.length)
                            target
                        } else {
                            synthErrorNo++
                            if (result == SynthResult.Timeout) {
                                AppLog.put("TTS合成超时，疑似引擎假死。\n朗读文本：$speakText")
                            } else {
                                AppLog.put("TTS合成出错，本段将静音跳过。\n朗读文本：$speakText")
                                toastOnUi("TTS合成出错，本段将静音跳过")
                            }
                            if (synthErrorNo >= 5) {
                                AppLog.put("TTS合成连续5次错误，已暂停朗读")
                                toastOnUi("TTS合成连续5次错误，已暂停朗读")
                                pauseReadAloud()
                                return@execute
                            }
                            if (result == SynthResult.Timeout && synthRebuildCount < 2) {
                                // 超时=引擎回调链路挂了（第三方引擎长会话后常见）：
                                // 清队列重建引擎自愈，onInit 完成后自动从当前段续播
                                synthRebuildCount++
                                AppLog.put("重建TTS引擎自愈($synthRebuildCount/2)")
                                toastOnUi("TTS合成超时，正在重建朗读引擎")
                                kotlin.runCatching { tts.stop() }
                                clearTTS()
                                runCatching { initTts() }.onFailure {
                                    AppLog.put("重建TTS引擎失败\n${it.localizedMessage}", it, true)
                                    toastOnUi("重建TTS引擎失败，已停止朗读")
                                    stopSelf()
                                }
                                return@execute
                            }
                            // 失败段复用共享静音占位，不写按文本命名的缓存，
                            // 避免引擎恢复前整章命中静音缓存「假朗读」（进度走、没声音）
                            ensureSilentFile()
                        }
                    }
                }
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
     * 合成一段文本到临时文件并等待完成；返回三态结果（超时/出错供上层区分处理）
     */
    private suspend fun synthesizeAwait(
        tts: TextToSpeech,
        text: String,
        file: File
    ): SynthResult {
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
        return when {
            ok == null -> {
                // 超时：回调链路疑似挂死，清理挂起状态并冲掉引擎队列，
                // 临时文件留给失败路径删除（不入缓存）
                synthPendingId = null
                synthContinuation = null
                file.delete()
                kotlin.runCatching { tts.stop() }
                SynthResult.Timeout
            }

            ok -> SynthResult.Ok
            else -> SynthResult.Error
        }
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
                // 列表被合成速度拖干/自然结束：空档墙钟时间不得计入节奏采样
                invalidatePaceAnchor()
                if (exoPlayer.currentMediaItemIndex < exoPlayer.mediaItemCount - 1) {
                    // ENDED 与合成协程排入新段交错：ENDED 态不会自动续播，
                    // 此时也不能 stop+clear（会把刚排入的新段静默清掉造成跳段）；
                    // 段落计数交由 onMediaItemTransition 的 updateNextPos 处理
                    exoPlayer.seekToNextMediaItem()
                    exoPlayer.prepare()
                } else {
                    updateNextPos()
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
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
                // 出错跳段：跳过的墙钟时间不计入节奏采样
                invalidatePaceAnchor()
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.clearMediaItems()
                updateNextPos()
            }
        }
    }

    /** 系统TTS引擎各自节奏不同，按引擎持久化基准节奏 */
    override fun paceStoreKey(): String = "sysTts-" + (ttsEngineName ?: "default")

    /**
     * 响度归一化：TTS 引擎输出电平远低于音乐母带（峰值常见 -6~-12dBFS），
     * 同音量条下朗读明显偏小；且各段峰值/波峰系数不同，逐段必须按「响度」拉齐，
     * 否则段间响度差在低音量下被放大为忽大忽小。峰值只用于防削波：
     * - 活跃区 = |样本| ≥ 峰值/8（约-18dB 相对门限），排除首尾/段内停顿对 RMS 的稀释
     * - 增益 = 目标RMS / 活跃RMS，最多放大 12 倍、最多衰减 6dB，并受防削波上界
     *   （增益后峰值 ≤ ~0.92 满幅）约束，动态再大的段也不削波
     * - 近静音（峰值过低/活跃样本过少/活跃RMS过低）返回 false：真实文本段的合成输出不应近静音，
     *   多为引擎输出异常（空音频），上层视为合成失败，不写入缓存
     * - 非 RIFF/非 16bit PCM 等无法解析的格式返回 true，原样交由播放器解码
     * - 对已归一文件幂等（增益≈1 直接跳过）
     */
    private fun normalizeWavLoudness(file: File): Boolean {
        try {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return false // 小于有效wav头，不可能是有效音频
            if (!String(bytes, 0, 4, Charsets.US_ASCII).startsWith("RIFF")) return true
            // 遍历 RIFF 块定位 data 块真实偏移（部分引擎在 fmt 后附加 LIST 等块）
            var pos = 12
            var dataStart = -1
            var dataSize = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4, Charsets.US_ASCII)
                val size = ((bytes[pos + 4].toInt() and 0xFF)) or
                        ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[pos + 7].toInt() and 0xFF) shl 24)
                if (id == "data") {
                    dataStart = pos + 8
                    dataSize = size
                    break
                }
                pos += 8 + size + (size and 1) // RIFF 块按字对齐
            }
            if (dataStart <= 0) return true
            if (dataStart + dataSize > bytes.size || dataSize <= 0) dataSize = bytes.size - dataStart
            // 仅处理 16bit PCM：22-23声道，34-35位深（fmt 块固定起始于 12+8）
            val channels = ((bytes[23].toInt() and 0xFF) shl 8) or (bytes[22].toInt() and 0xFF)
            val bits = ((bytes[35].toInt() and 0xFF) shl 8) or (bytes[34].toInt() and 0xFF)
            if (bits != 16 || channels < 1) return true
            val end = dataStart + dataSize
            // 第一遍：峰值（定活跃门限与防削波上界）
            var peak = 0
            var i = dataStart
            while (i + 1 < end) {
                val raw = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                val v = if (raw >= 0x8000) raw - 0x10000 else raw
                val a = if (v < 0) -v else v
                if (a > peak) peak = a
                i += 2
            }
            if (peak < 200) return false // 近静音（≈-44dBFS 以下）：真实文本段不该如此，判定合成无效
            // 第二遍：语音活跃区 RMS（排除停顿稀释，保证段间可比）
            val gate = peak / 8
            var sumSq = 0.0
            var activeCount = 0
            i = dataStart
            while (i + 1 < end) {
                val raw = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                val v = if (raw >= 0x8000) raw - 0x10000 else raw
                val a = if (v < 0) -v else v
                if (a >= gate) {
                    sumSq += v.toDouble() * v
                    activeCount++
                }
                i += 2
            }
            if (activeCount < 32) return false // 活跃样本过少（近纯静音），判定合成无效
            val activeRms = sqrt(sumSq / activeCount)
            if (activeRms < 80) return false // 活跃区也接近静音（≈-52dBFS），判定合成无效
            val targetRms = 6000.0 // ≈-14.8dBFS：对齐上次峰值归一的实际响度（音乐App级听感），段间拉齐的目标
            var gain = targetRms / activeRms
            gain = gain.coerceAtMost(30100.0 / peak) // 防削波：增益后峰值 ≤ ~0.92 满幅
            gain = gain.coerceIn(0.5, 12.0)          // 最多衰减 6dB / 最多放大 12 倍
            if (gain in 0.97..1.03) return true // 已在目标响度（含幂等二次处理）
            i = dataStart
            while (i + 1 < end) {
                val raw = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                val v = if (raw >= 0x8000) raw - 0x10000 else raw
                val scaled = (v * gain).toInt().coerceIn(-32768, 32767)
                bytes[i] = (scaled and 0xFF).toByte()
                bytes[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                i += 2
            }
            file.writeBytes(bytes)
            LogUtils.d(TAG, "响度归一化: peak=$peak activeRms=${activeRms.toInt()} gain=%.2fx %s".format(gain, file.name))
            return true
        } catch (e: Exception) {
            AppLog.put("朗读音频响度归一化失败", e)
            return true // 处理异常不动文件，按可用音频对待，交由播放器验证
        }
    }

    /** 逐段合成到 wav 文件，具备单段精确时长校准能力 */
    override fun exactPaceAvailable(): Boolean = true

    /** 解析标准44字节头的wav精确时长（毫秒），异常或非标准头返回0 */
    private fun wavDurationMs(file: File): Long {
        return try {
            if (file.length() <= 44L) return 0L
            FileInputStream(file).use { ins ->
                val header = ByteArray(44)
                if (ins.read(header) != 44) return 0L
                if (!String(header, 0, 4, Charsets.US_ASCII).startsWith("RIFF")) return 0L
                // 标准头：24-27采样率，28-31字节率(=采样率×块对齐)；时长按字节率换算
                val byteRate = ((header[31].toInt() and 0xFF) shl 24) or
                        ((header[30].toInt() and 0xFF) shl 16) or
                        ((header[29].toInt() and 0xFF) shl 8) or
                        (header[28].toInt() and 0xFF)
                if (byteRate < 4000 || byteRate > 1_000_000) return 0L
                (file.length() - 44L) * 1000L / byteRate
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun deleteCurrentSpeakFile() {
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val filePath = mediaItem.localConfiguration?.uri?.path ?: return
        FileUtils.delete(filePath)
    }

    private fun md5SpeakFileName(content: String): String {
        // 前缀 loudv2：响度归一化算法版本标记，升级后旧缓存（峰值归一化版）自动失效重合成
        return "loudv2_" + MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
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
     * 失败段共享的静音占位文件：不按文本缓存（断掉「静音毒缓存」），
     * 引擎恢复后重读该段即正常合成
     */
    private fun ensureSilentFile(): File {
        val file = File(ttsFolderPath, "silent_skip.mp3")
        if (!file.exists()) {
            file.writeBytes(resources.openRawResource(R.raw.silent_sound).readBytes())
        }
        return file
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
