package build.kargo.ktor.client.native

import co.touchlab.kermit.Logger
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*

/**
 * Linux Native implementation of HybridEngine.
 * 
 * Uses CIO for HTTP and libcurl for HTTPS (CIO doesn't support HTTPS on Linux Native).
 */
@OptIn(io.ktor.utils.io.InternalAPI::class)
actual class HybridEngine actual constructor(
    actual override val config: HybridEngineConfig
) : HttpClientEngineBase("ktor-hybrid-linux") {

    private val cioEngine: HttpClientEngine by lazy {
        Logger.i { "Initializing CIO engine for HTTP requests (Linux Native)" }
        CIO.create()
    }

    private val curlEngine: HttpClientEngine by lazy {
        Logger.i { "Initializing libcurl engine for HTTPS requests (Linux Native)" }
        Native.create {
            forceHttp1 = config.forceHttp1
        }
    }

    override actual val dispatcher = kotlinx.coroutines.Dispatchers.Default
    override actual val supportedCapabilities: Set<HttpClientEngineCapability<*>> = emptySet()

    actual override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val engine = selectEngine(data)
        Logger.d { "Using ${engine::class.simpleName} for ${data.url}" }
        return engine.execute(data)
    }

    /**
     * Selects the appropriate engine based on the request URL protocol.
     * 
     * Linux Native specific: CIO for HTTP, libcurl for HTTPS.
     */
    private fun selectEngine(data: HttpRequestData): HttpClientEngine {
        // Allow manual override for testing/debugging
        if (config.forceCurl) {
            Logger.d { "Forcing libcurl engine (forceCurl=true)" }
            return curlEngine
        }
        
        if (config.forceCio) {
            Logger.d { "Forcing CIO engine (forceCio=true)" }
            return cioEngine
        }

        // Select based on protocol
        return when (data.url.protocol.name.lowercase()) {
            "https" -> {
                Logger.d { "HTTPS detected, using libcurl engine (Linux Native)" }
                curlEngine
            }
            "http" -> {
                Logger.d { "HTTP detected, using CIO engine" }
                cioEngine
            }
            else -> {
                Logger.w { "Unknown protocol ${data.url.protocol.name}, defaulting to libcurl" }
                curlEngine
            }
        }
    }

    actual override fun close() {
        Logger.i { "Closing hybrid engine (Linux Native)" }
        cioEngine.close()
        curlEngine.close()
        super.close()
    }
}
