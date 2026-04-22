package build.kargo.ktor.client.native

import io.ktor.client.engine.*
import io.ktor.client.request.*

/**
 * Hybrid HTTP client engine that intelligently selects between CIO and libcurl.
 * 
 * **Platform-Specific Behavior**:
 * - **Linux Native**: Uses CIO for HTTP, libcurl for HTTPS (CIO doesn't support HTTPS)
 * - **macOS Native**: Uses CIO for all requests (CIO supports HTTPS)
 * - **Windows Native**: Uses CIO for all requests (CIO supports HTTPS)
 * - **JVM**: Uses CIO for all requests (CIO supports HTTPS)
 * 
 * This provides optimal performance across all platforms while solving the
 * Linux Native HTTPS limitation.
 * 
 * Strategy:
 * - Uses CIO engine for HTTP requests (faster, better supported)
 * - Falls back to libcurl for HTTPS requests (only on Linux Native where needed)
 * 
 * This provides the best of both worlds: performance where possible, 
 * and HTTPS support where needed.
 * 
 * @see Native for libcurl-only implementation (Linux Native only)
 * @see io.ktor.client.engine.cio.CIO for the underlying CIO engine
 */
class HybridEngineConfig : HttpClientEngineConfig() {
    /**
     * Force HTTP/1.1 for libcurl requests (when HTTPS is used on Linux Native)
     */
    var forceHttp1: Boolean = false
    
    /**
     * Force all requests to use libcurl (useful for debugging, Linux Native only)
     */
    var forceCurl: Boolean = false
    
    /**
     * Force all requests to use CIO (will fail on HTTPS on Linux Native)
     */
    var forceCio: Boolean = false
}

object Hybrid : HttpClientEngineFactory<HybridEngineConfig> {
    override fun create(block: HybridEngineConfig.() -> Unit): HttpClientEngine {
        val config = HybridEngineConfig().apply(block)
        return HybridEngine(config)
    }
}

/**
 * Hybrid engine implementation that delegates to CIO or Native (libcurl) based on platform and protocol.
 */
@OptIn(io.ktor.utils.io.InternalAPI::class)
expect class HybridEngine(config: HybridEngineConfig) : HttpClientEngineBase {
    override val config: HybridEngineConfig
    override val dispatcher: kotlinx.coroutines.CoroutineDispatcher
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>>
    
    override suspend fun execute(data: HttpRequestData): HttpResponseData
    
    override fun close()
}

