package build.kargo.ktor.client.native

import co.touchlab.kermit.Logger
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.readByteArray
import platform.posix.*
import libcurl.*

class NativeEngineConfig : HttpClientEngineConfig() {
    var forceHttp1: Boolean = false
}

object Native : HttpClientEngineFactory<NativeEngineConfig> {
    override fun create(block: NativeEngineConfig.() -> Unit): HttpClientEngine {
        val config = NativeEngineConfig().apply(block)
        return NativeEngine(config)
    }
}

class RequestContext(
    val headerStream: Channel<String>,
    val bodyStream: Channel<ByteArray>
) {
    var isPaused = false
}

@OptIn(ExperimentalForeignApi::class)
val writeCallback = staticCFunction { ptr: CPointer<ByteVar>?, size: size_t, nmemb: size_t, userdata: COpaquePointer? ->
    val bytes = (size * nmemb).toInt()
    if (bytes == 0) return@staticCFunction 0uL
    
    val ctx = userdata!!.asStableRef<RequestContext>().get()
    val data = ptr!!.readBytes(bytes)
    
    val result = ctx.bodyStream.trySend(data)
    if (result.isSuccess) {
        (size * nmemb)
    } else {
        ctx.isPaused = true
        CURL_WRITEFUNC_PAUSE.toULong()
    }
}

@OptIn(ExperimentalForeignApi::class)
val headerCallback = staticCFunction { ptr: CPointer<ByteVar>?, size: size_t, nmemb: size_t, userdata: COpaquePointer? ->
    val bytes = (size * nmemb).toInt()
    if (bytes > 0 && ptr != null && userdata != null) {
        val ctx = userdata.asStableRef<RequestContext>().get()
        val data = ptr.readBytes(bytes).decodeToString()
        ctx.headerStream.trySend(data)
    }
    (size * nmemb)
}

@OptIn(ExperimentalForeignApi::class)
class NativeEngine(
    override val config: NativeEngineConfig = NativeEngineConfig()
) : HttpClientEngineBase("ktor-native-curl") {

    init {
        Logger.i { "Initializing libcurl: ${curl_version()?.toKString()}" }
        curl_global_init(CURL_GLOBAL_ALL.toLong())
    }

    override val dispatcher = Dispatchers.Default
    private val forceHttp1: Boolean get() = config.forceHttp1
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = emptySet()

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestScope = CoroutineScope(callContext)
        
        val deferredResponse = CompletableDeferred<HttpResponseData>()
        val outChannel = ByteChannel(autoFlush = true)
        
        requestScope.launch {
            memScoped {
                val easyHandle = curl_easy_init() ?: throw Exception("Failed to init curl easy handle")
                val multiHandle = curl_multi_init() ?: throw Exception("Failed to init curl multi handle")
                
                // Capacity 64 implies buffering reasonable chunks before pausing
                val reqCtx = RequestContext(Channel(Channel.UNLIMITED), Channel(64))
                val ctxRef = StableRef.create(reqCtx)
                var slist: CPointer<curl_slist>? = null
                
                try {
                    val urlStr = data.url.toString()
                    Logger.i { "cURL E2E fetching: $urlStr" }
                    curl_easy_setopt(easyHandle, CURLOPT_URL, urlStr)
                    curl_easy_setopt(easyHandle, CURLOPT_CUSTOMREQUEST, data.method.value)
                    
                    if (forceHttp1) {
                        curl_easy_setopt(easyHandle, CURLOPT_HTTP_VERSION, CURL_HTTP_VERSION_1_1)
                    }
                    
                    data.headers.forEach { name, values ->
                        values.forEach { value ->
                            slist = curl_slist_append(slist, "$name: $value")
                        }
                    }
                    // Include body Content-Type in headers (not set automatically by libcurl for raw posts)
                    data.body.contentType?.let { ct ->
                        slist = curl_slist_append(slist, "Content-Type: $ct")
                    }

                    val bodyBytes = data.body.toByteArray()
                    if (bodyBytes.isNotEmpty()) {
                        val cArray = allocArrayOf(bodyBytes)
                        // IMPORTANT: set size BEFORE COPYPOSTFIELDS so libcurl treats data as raw bytes
                        // and does NOT auto-set Content-Type: application/x-www-form-urlencoded
                        curl_easy_setopt(easyHandle, CURLOPT_POSTFIELDSIZE_LARGE, bodyBytes.size.toLong())
                        curl_easy_setopt(easyHandle, CURLOPT_COPYPOSTFIELDS, cArray)
                    }

                    if (slist != null) {
                        curl_easy_setopt(easyHandle, CURLOPT_HTTPHEADER, slist)
                    }
                    
                    curl_easy_setopt(easyHandle, CURLOPT_WRITEFUNCTION, writeCallback)
                    curl_easy_setopt(easyHandle, CURLOPT_WRITEDATA, ctxRef.asCPointer())
                    curl_easy_setopt(easyHandle, CURLOPT_HEADERFUNCTION, headerCallback)
                    curl_easy_setopt(easyHandle, CURLOPT_HEADERDATA, ctxRef.asCPointer())
                    
                    curl_multi_add_handle(multiHandle, easyHandle)
                    
                    var running = alloc<IntVar>()
                    var headersString = ""
                    var headerFinished = false
                    
                    while (isActive) {
                        curl_multi_perform(multiHandle, running.ptr)
                        
                        // Drain headers
                        while (true) {
                            val h = reqCtx.headerStream.tryReceive().getOrNull() ?: break
                            headersString += h
                            if (h == "\r\n" || h == "\n") {
                                headerFinished = true
                            }
                        }
                        
                        if (headerFinished && !deferredResponse.isCompleted) {
                            Logger.i { "Headers finished smoothly. Completing deferred." }
                            val (status, headers) = parseHttpResponseHeaders(headersString)
                            val response = HttpResponseData(
                                statusCode = HttpStatusCode.fromValue(status),
                                requestTime = GMTDate(),
                                headers = headers,
                                version = HttpProtocolVersion.HTTP_1_1,
                                body = outChannel,
                                callContext = callContext
                            )
                            deferredResponse.complete(response)
                        }
                        
                        // Drain body
                        while (true) {
                            val chunk = reqCtx.bodyStream.tryReceive().getOrNull() ?: break
                            outChannel.writeFully(chunk)
                        }
                        
                        // Handle backpressure
                        if (reqCtx.isPaused) {
                            reqCtx.isPaused = false
                            curl_easy_pause(easyHandle, CURLPAUSE_CONT)
                        }
                        
                        if (running.value == 0) break
                        
                        if (running.value > 0) {
                            // Wait up to 100ms for activity
                            curl_multi_wait(multiHandle, null, 0u, 100, null)
                        }
                    }
                    
                    // Final drain just in case
                    while (true) {
                        val chunk = reqCtx.bodyStream.tryReceive().getOrNull() ?: break
                        outChannel.writeFully(chunk)
                    }
                    
                    if (!deferredResponse.isCompleted) {
                        Logger.i { "Fallback completion since header parsing was skipped..." }
                        val (status, headers) = parseHttpResponseHeaders(headersString)
                        val response = HttpResponseData(
                            statusCode = HttpStatusCode.fromValue(status),
                            requestTime = GMTDate(),
                            headers = headers,
                            version = HttpProtocolVersion.HTTP_1_1,
                            body = outChannel,
                            callContext = callContext
                        )
                        deferredResponse.complete(response)
                    }
                    Logger.i { "cURL loop finalized cleanly." }
                    
                } catch (e: Exception) {
                    deferredResponse.completeExceptionally(e)
                } finally {
                    outChannel.close()
                    curl_multi_remove_handle(multiHandle, easyHandle)
                    curl_easy_cleanup(easyHandle)
                    curl_multi_cleanup(multiHandle)
                    if (slist != null) curl_slist_free_all(slist)
                    ctxRef.dispose()
                }
            }
        }
        
        return deferredResponse.await()
    }

    private suspend fun OutgoingContent.toByteArray(): ByteArray = when (this) {
        is OutgoingContent.ByteArrayContent -> bytes()
        is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readByteArray()
        is OutgoingContent.WriteChannelContent -> {
            val channel = ByteChannel()
            kotlinx.coroutines.coroutineScope {
                launch {
                    try {
                        this@toByteArray.writeTo(channel)
                    } finally {
                        channel.close()
                    }
                }
                channel.readRemaining().readByteArray()
            }
        }
        is OutgoingContent.NoContent -> ByteArray(0)
        else -> ByteArray(0)
    }

    private fun parseHttpResponseHeaders(headerLinesRaw: String): Pair<Int, Headers> {
        val headerLines = headerLinesRaw.split("\r\n").filter { it.isNotBlank() }
        if (headerLines.isEmpty()) throw Exception("No headers received")
        
        val statusLine = headerLines.first()
        val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull()
            ?: throw Exception("Invalid status line: $statusLine")
            
        val headers = HeadersBuilder().apply {
            headerLines.drop(1).forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    append(line.take(idx).trim(), line.substring(idx + 1).trim())
                }
            }
        }.build()
        
        return Pair(statusCode, headers)
    }
}
