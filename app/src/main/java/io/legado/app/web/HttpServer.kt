package io.legado.app.web

import android.graphics.Bitmap
import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookController
import io.legado.app.api.controller.BookSourceController
import io.legado.app.api.controller.ReplaceRuleController
import io.legado.app.api.controller.RssSourceController
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.service.WebService
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.stackTraceStr
import io.legado.app.web.utils.AssetsWeb
import kotlinx.coroutines.runBlocking
import okio.Pipe
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpServer(port: Int) : NanoHTTPD(port) {
    private val assetsWeb = AssetsWeb("web")

    override fun serve(session: IHTTPSession): Response {
        WebService.serve()
        var returnData: ReturnData? = null
        val ct = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers["content-type"] = ct.contentTypeHeader
        var uri = session.uri

        val startAt = System.currentTimeMillis()
        LogUtils.d(TAG) {
            "${session.method.name} - $uri - ${session.queryParameterString} - Start($startAt)"
        }

        try {
            when (session.method) {
                Method.OPTIONS -> {
                    val response = newFixedLengthResponse("")
                    // 与 addCommonHeaders 的实际响应保持一致，避免带自定义头的 GET 预检被浏览器拒绝
                    response.addHeader("Access-Control-Allow-Methods", ALLOW_METHODS)
                    response.addHeader("Access-Control-Allow-Headers", "content-type")
                    // 不回显任意 Origin；仅在持有有效令牌时允许跨域
                    val token = AppConfig.webToken
                    if (token.isNotBlank() && checkToken(session, token)) {
                        response.addHeader("Access-Control-Allow-Origin", session.headers["origin"])
                    }
                    //response.addHeader("Access-Control-Max-Age", "3600");
                    return response
                }

                Method.POST -> {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val handler = postRoutes[uri]
                    if (handler != null) {
                        checkAuth(session)?.let { return it }
                        val postData = files["postData"]
                        returnData = runBlocking { handler(session, postData, files) }
                    }
                }

                Method.GET -> {
                    val handler = getRoutes[uri]
                    if (handler != null) {
                        checkAuth(session)?.let { return it }
                        returnData = handler(session)
                    }
                }

                else -> Unit
            }

            if (returnData == null) {
                if (uri.endsWith("/"))
                    uri += "index.html"
                val response = assetsWeb.getResponse(uri)
                addCommonHeaders(response, session)
                return response
            }

            val response = if (returnData.data is Bitmap) {
                val outputStream = ByteArrayOutputStream()
                // 封面类图片用 JPEG 编码，体积与编码速度远优于 PNG
                (returnData.data as Bitmap).compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val byteArray = outputStream.toByteArray()
                outputStream.close()
                val inputStream = ByteArrayInputStream(byteArray)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    inputStream,
                    byteArray.size.toLong()
                )
            } else {
                val data = returnData.data
                if (data is List<*> && data.size > 3000) {
                    val pipe = Pipe(16 * 1024)
                    Coroutine.async {
                        pipe.sink.buffer().outputStream().bufferedWriter(Charsets.UTF_8).use {
                            GSON.toJson(returnData, it)
                        }
                    }
                    newChunkedResponse(
                        Response.Status.OK,
                        "application/json",
                        pipe.source.buffer().inputStream()
                    )
                } else {
                    newFixedLengthResponse(GSON.toJson(returnData))
                }
            }
            addCommonHeaders(response, session)
            LogUtils.d(TAG) {
                "${session.method.name} - $uri - ${session.queryParameterString} - End($startAt)"
            }
            return response
        } catch (e: Exception) {
            LogUtils.d(TAG) {
                "${session.method.name} - $uri - ${session.queryParameterString} - Error End($startAt)\n$e\n${e.stackTraceStr}"
            }
            // 500 + JSON 错误体：只回受限摘要（异常类名），不回传原始异常消息以免泄露内部信息；
            // 完整堆栈已在上方服务端日志
            val response = newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                GSON.toJson(ReturnData().setErrorMsg(e.javaClass.simpleName))
            )
            addCommonHeaders(response, session)
            return response
        }

    }

    /**
     * 数据接口鉴权：配置了访问令牌后必须携带令牌。
     * 通过 ?token= 访问时由 addCommonHeaders 种下 Cookie，浏览器后续请求自动携带。
     * 校验通过返回 null，否则返回 401 响应。
     */
    private fun checkAuth(session: IHTTPSession): Response? {
        val token = AppConfig.webToken
        if (token.isBlank() || checkToken(session, token)) {
            return null
        }
        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "unauthorized")
    }

    private fun checkToken(session: IHTTPSession, token: String): Boolean {
        if (token.isBlank()) {
            return true
        }
        return session.parameters["token"]?.firstOrNull() == token
            || session.headers["authorization"] == "Bearer $token"
            || session.cookies.read("authToken") == token
    }

    private fun addCommonHeaders(response: Response, session: IHTTPSession) {
        val token = AppConfig.webToken
        if (token.isBlank()) {
            return
        }
        val tokenValid = checkToken(session, token)
        if (tokenValid) {
            response.addHeader("Access-Control-Allow-Methods", ALLOW_METHODS)
            response.addHeader("Access-Control-Allow-Origin", session.headers["origin"])
            // 通过 ?token= 访问时种下 Cookie，浏览器后续请求自动携带
            if (session.parameters["token"]?.firstOrNull() == token) {
                response.addHeader("Set-Cookie", "authToken=$token; Path=/")
            }
        }
    }

    companion object {
        private const val TAG = "HttpServer"

        /** CORS 允许的方法：OPTIONS 预检与实际响应共用，防止两者不一致 */
        private const val ALLOW_METHODS = "GET, POST"

        /**
         * POST 数据接口路由表，键集合即鉴权范围，新增接口无需另行登记
         */
        private val postRoutes: Map<String, suspend (IHTTPSession, String?, HashMap<String, String>) -> ReturnData?> =
            mapOf(
                "/saveBookSource" to { _, postData, _ -> BookSourceController.saveSource(postData) },
                "/saveBookSources" to { _, postData, _ -> BookSourceController.saveSources(postData) },
                "/deleteBookSources" to { _, postData, _ -> BookSourceController.deleteSources(postData) },
                "/saveBook" to { _, postData, _ -> BookController.saveBook(postData) },
                "/deleteBook" to { _, postData, _ -> BookController.deleteBook(postData) },
                "/saveBookProgress" to { _, postData, _ -> BookController.saveBookProgress(postData) },
                "/addLocalBook" to { session, _, files -> BookController.addLocalBook(session.parameters, files) },
                "/saveReadConfig" to { _, postData, _ -> BookController.saveWebReadConfig(postData) },
                "/saveRssSource" to { _, postData, _ -> RssSourceController.saveSource(postData) },
                "/saveRssSources" to { _, postData, _ -> RssSourceController.saveSources(postData) },
                "/deleteRssSources" to { _, postData, _ -> RssSourceController.deleteSources(postData) },
                "/saveReplaceRule" to { _, postData, _ -> ReplaceRuleController.saveRule(postData) },
                "/deleteReplaceRule" to { _, postData, _ -> ReplaceRuleController.delete(postData) },
                "/testReplaceRule" to { _, postData, _ -> ReplaceRuleController.testRule(postData) }
            )

        /**
         * GET 数据接口路由表
         */
        private val getRoutes: Map<String, (IHTTPSession) -> ReturnData?> = mapOf(
            "/getBookSource" to { session -> BookSourceController.getSource(session.parameters) },
            "/getBookSources" to { _ -> BookSourceController.sources },
            "/getBookshelf" to { _ -> BookController.bookshelf },
            "/getChapterList" to { session -> BookController.getChapterList(session.parameters) },
            "/refreshToc" to { session -> BookController.refreshToc(session.parameters) },
            "/getBookContent" to { session -> BookController.getBookContent(session.parameters) },
            "/cover" to { session -> BookController.getCover(session.parameters) },
            "/image" to { session -> BookController.getImg(session.parameters) },
            "/getReadConfig" to { _ -> BookController.getWebReadConfig() },
            "/getRssSource" to { session -> RssSourceController.getSource(session.parameters) },
            "/getRssSources" to { _ -> RssSourceController.sources },
            "/getReplaceRules" to { _ -> ReplaceRuleController.allRules }
        )
    }

}
