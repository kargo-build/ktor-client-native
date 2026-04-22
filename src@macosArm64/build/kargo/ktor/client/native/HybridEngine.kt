package build.kargo.ktor.client.native

import co.touchlab.kermit.Logger
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*

/**
 * macOS ARM Native implementation of HybridEngine.
 * 
 * Uses CIO for all requests (both HTTP and HTTPS work fine on macOS).
 */
@OptIn(io.ktor.utils.io.InternalAPI::class)
actual class HybridEngine actual constructor(
    actual override val config: HybridEngineConfig
) : HttpClientEngineBase("ktor-hybrid-macos") {

    private val cioEngine: HttpClientEngine by lazy {
        Logger.i { "Initializing CIO engine for all requests (macOS ARM Native - HTTPS supported)" }
        CIO.create()
    }

    actual override val dispatcher = kotlinx.coroutines.Dispatchers.Default
    actual override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = emptySet()

    actual override suspend fun execute(data: HttpRequestData): HttpResponseData {
        Logger.d { "Using CIO engine for ${data.url} (macOS ARM Native)" }
        return cioEngine.execute(data)
    }

    actual override fun close() {
        Logger.i { "Closing hybrid engine (macOS ARM Native)" }
        cioEngine.close()
        super.close()
    }
}
