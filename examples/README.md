# Examples

This directory contains working examples demonstrating how to use ktor-client-native.

## Running Examples

### Prerequisites

1. Install libcurl development headers:
   ```bash
   sudo apt-get install libcurl4-openssl-dev
   ```

2. Build the project:
   ```bash
   ./kargo build
   ```

3. Run an example:
   ```bash
   ./kargo run examples/HybridEngineExample.kt
   ```

## Available Examples

### HybridEngineExample.kt

Demonstrates the Hybrid engine's automatic protocol selection:

- **HTTP requests** → Uses CIO (fast)
- **HTTPS requests** → Uses libcurl (secure)
- **Mixed requests** → Handles both seamlessly
- **POST with JSON** → Sending data to HTTPS endpoints
- **Concurrent requests** → Multiple requests in parallel
- **Force modes** → Testing with forceCurl and forceCio

**Key Concepts**:
- Automatic engine selection based on protocol
- Transparent API (no code changes needed)
- Performance optimization (CIO for HTTP)
- Security support (libcurl for HTTPS)

**Output Example**:
```
=== Hybrid Engine Demo ===

1. Making HTTP request...
   Status: 200 OK
   Engine used: CIO (fast)

2. Making HTTPS request...
   Status: 200 OK
   Engine used: libcurl (secure)

3. Making HTTPS POST request...
   Status: 200 OK
   Response contains our data: true

4. Making concurrent mixed requests...
   All requests completed successfully!
   Request 1: 200 OK
   Request 2: 201 Created
   Request 3: 202 Accepted
   Request 4: 203 Non-Authoritative Information

=== Demo Complete ===
```

## Common Use Cases

### Simple GET Request

```kotlin
val client = HttpClient(Hybrid)
val response = client.get("https://api.example.com/data")
println(response.bodyAsText())
client.close()
```

### POST with JSON

```kotlin
val client = HttpClient(Hybrid) {
    install(ContentNegotiation) {
        json()
    }
}

val response = client.post("https://api.example.com/users") {
    contentType(ContentType.Application.Json)
    setBody(User(name = "John", email = "john@example.com"))
}

client.close()
```

### With Authentication

```kotlin
val client = HttpClient(Hybrid) {
    install(Auth) {
        bearer {
            loadTokens {
                BearerTokens("your-access-token", "your-refresh-token")
            }
        }
    }
}

val response = client.get("https://api.example.com/protected")
client.close()
```

### With Retry Logic

```kotlin
val client = HttpClient(Hybrid) {
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
    }
}

val response = client.get("https://api.example.com/unstable")
client.close()
```

### Streaming Large Files

```kotlin
val client = HttpClient(Hybrid)

client.prepareGet("https://example.com/large-file.zip").execute { response ->
    val channel = response.bodyAsChannel()
    val file = File("downloaded-file.zip")
    
    file.outputStream().use { output ->
        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
            while (!packet.isEmpty) {
                val bytes = packet.readBytes()
                output.write(bytes)
            }
        }
    }
}

client.close()
```

### WebSocket (Future)

```kotlin
// Note: WebSocket support is planned for future releases
val client = HttpClient(Hybrid) {
    install(WebSockets)
}

client.webSocket("wss://echo.websocket.org") {
    send("Hello, WebSocket!")
    val message = incoming.receive()
    println(message)
}

client.close()
```

## Testing Examples

### Unit Test with Mock

```kotlin
@Test
fun testApiCall() = runBlocking {
    val mockEngine = MockEngine { request ->
        respond(
            content = """{"status": "ok"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    
    val client = HttpClient(mockEngine)
    val response = client.get("https://api.example.com/test")
    
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("ok"))
    
    client.close()
}
```

### Integration Test

```kotlin
@Test
fun testRealApi() = runBlocking {
    val client = HttpClient(Hybrid)
    
    val response = client.get("https://httpbin.org/get") {
        header("X-Test", "integration")
    }
    
    assertTrue(response.status.isSuccess())
    val body = response.bodyAsText()
    assertTrue(body.contains("X-Test"))
    
    client.close()
}
```

## Performance Tips

1. **Reuse clients**: Create one client and reuse it for multiple requests
   ```kotlin
   val client = HttpClient(Hybrid)
   // Use for many requests
   client.close() // Close when done
   ```

2. **Use connection pooling**: Enabled by default in both CIO and libcurl

3. **Enable HTTP/2**: For HTTPS requests (enabled by default)
   ```kotlin
   val client = HttpClient(Hybrid) {
       forceHttp1 = false // Allow HTTP/2
   }
   ```

4. **Concurrent requests**: Use coroutines for parallel requests
   ```kotlin
   coroutineScope {
       val deferred1 = async { client.get("https://api1.com") }
       val deferred2 = async { client.get("https://api2.com") }
       val results = awaitAll(deferred1, deferred2)
   }
   ```

## Troubleshooting

### SSL Certificate Errors

If you get SSL certificate errors:
```bash
# Update CA certificates
sudo apt-get install ca-certificates
sudo update-ca-certificates
```

### Connection Timeouts

Increase timeout values:
```kotlin
val client = HttpClient(Hybrid) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
        connectTimeoutMillis = 10000
        socketTimeoutMillis = 10000
    }
}
```

### Memory Issues

Always close clients when done:
```kotlin
val client = HttpClient(Hybrid)
try {
    // Use client
} finally {
    client.close() // Important!
}
```

## Contributing Examples

Have a useful example? Please contribute!

1. Create a new `.kt` file in this directory
2. Add clear comments explaining what it demonstrates
3. Include error handling and cleanup
4. Update this README with a description
5. Submit a pull request

## Resources

- [Ktor Client Documentation](https://ktor.io/docs/client.html)
- [Architecture Guide](../docs/ARCHITECTURE.md)
- [Migration Guide](../docs/MIGRATION.md)
- [Main README](../README.md)
