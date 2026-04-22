package examples

import build.kargo.ktor.client.native.Hybrid
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating the Hybrid engine's automatic protocol selection.
 * 
 * The Hybrid engine intelligently chooses:
 * - CIO for HTTP requests (faster)
 * - libcurl for HTTPS requests (secure, works on Linux Native)
 */
fun main() = runBlocking {
    val client = HttpClient(Hybrid) {
        // Optional configuration
        forceHttp1 = false // Allow HTTP/2 for HTTPS requests
    }

    try {
        println("=== Hybrid Engine Demo ===\n")

        // Example 1: HTTP request (will use CIO - fast)
        println("1. Making HTTP request...")
        val httpResponse = client.get("http://httpbin.org/get") {
            header("User-Agent", "Ktor-Hybrid-Example/1.0")
        }
        println("   Status: ${httpResponse.status}")
        println("   Engine used: CIO (fast)\n")

        // Example 2: HTTPS request (will use libcurl - secure)
        println("2. Making HTTPS request...")
        val httpsResponse = client.get("https://httpbin.org/get") {
            header("User-Agent", "Ktor-Hybrid-Example/1.0")
        }
        println("   Status: ${httpsResponse.status}")
        println("   Engine used: libcurl (secure)\n")

        // Example 3: POST with JSON to HTTPS endpoint
        println("3. Making HTTPS POST request...")
        val postResponse = client.post("https://httpbin.org/post") {
            header("Content-Type", "application/json")
            setBody("""{"message": "Hello from Hybrid Engine!", "timestamp": ${System.currentTimeMillis()}}""")
        }
        println("   Status: ${postResponse.status}")
        val body = postResponse.bodyAsText()
        println("   Response contains our data: ${body.contains("Hello from Hybrid Engine!")}\n")

        // Example 4: Multiple concurrent requests (mixed protocols)
        println("4. Making concurrent mixed requests...")
        val responses = listOf(
            client.get("http://httpbin.org/status/200"),
            client.get("https://httpbin.org/status/201"),
            client.get("http://httpbin.org/status/202"),
            client.get("https://httpbin.org/status/203")
        )
        println("   All requests completed successfully!")
        responses.forEachIndexed { index, response ->
            println("   Request ${index + 1}: ${response.status}")
        }

        println("\n=== Demo Complete ===")
        println("The Hybrid engine seamlessly handled both HTTP and HTTPS requests!")
        
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}

/**
 * Example showing how to force a specific engine for testing/debugging.
 */
fun forcedEngineExample() = runBlocking {
    // Force all requests to use libcurl (useful for debugging)
    val curlOnlyClient = HttpClient(Hybrid) {
        forceCurl = true
    }

    try {
        println("=== Forced libcurl Mode ===")
        val response = curlOnlyClient.get("http://httpbin.org/get")
        println("HTTP request handled by libcurl: ${response.status}")
    } finally {
        curlOnlyClient.close()
    }

    // Force all requests to use CIO (will fail on HTTPS on Linux Native)
    val cioOnlyClient = HttpClient(Hybrid) {
        forceCio = true
    }

    try {
        println("\n=== Forced CIO Mode ===")
        val response = cioOnlyClient.get("http://httpbin.org/get")
        println("HTTP request handled by CIO: ${response.status}")
        
        // This would fail on Linux Native:
        // val httpsResponse = cioOnlyClient.get("https://httpbin.org/get")
    } finally {
        cioOnlyClient.close()
    }
}
