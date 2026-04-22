package build.kargo.ktor.client.native

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class NativeEngineTest {

    @Test
    fun testSimpleGetRequest() = runBlocking {
        val client = HttpClient(Native)
        try {
            val response = client.get("https://httpbin.org/headers") {
                header("X-Kargo-Test", "true")
            }
            val body = response.bodyAsText()
            assertTrue(response.status.value in 200..299, "Expected 2xx status code but got ${response.status}")
            assertTrue(body.contains("X-Kargo-Test"), "Body should contain custom header")
        } finally {
            client.close()
        }
    }
}
