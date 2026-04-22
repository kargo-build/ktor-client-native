# Migration Guide

This guide helps you migrate from other Ktor client engines to ktor-client-native.

## From CIO Engine

### Problem
CIO doesn't support HTTPS on Kotlin/Native Linux:

```kotlin
// ❌ This fails on Linux Native with HTTPS
val client = HttpClient(CIO)
val response = client.get("https://api.example.com/data") // Error!
```

### Solution
Use the Hybrid engine:

```kotlin
// ✅ Works perfectly
import build.kargo.ktor.client.native.Hybrid

val client = HttpClient(Hybrid)
val response = client.get("https://api.example.com/data") // Success!
```

### Migration Steps

1. **Add dependency** to `module.yaml`:
   ```yaml
   dependencies:
     - io.ktor:ktor-client-core:3.4.1
     - io.ktor:ktor-client-cio:3.4.1  # Still needed for HTTP
   
   settings:
     native:
       cinterop:
         libcurl:
           defFile: resources/cinterop/libcurl.def
           packageName: libcurl
   ```

2. **Install libcurl** (if not already installed):
   ```bash
   sudo apt-get install libcurl4-openssl-dev
   ```

3. **Change import**:
   ```kotlin
   // Before
   import io.ktor.client.engine.cio.CIO
   
   // After
   import build.kargo.ktor.client.native.Hybrid
   ```

4. **Update client creation**:
   ```kotlin
   // Before
   val client = HttpClient(CIO) {
       // your config
   }
   
   // After
   val client = HttpClient(Hybrid) {
       // your config (same as before)
   }
   ```

5. **No code changes needed** - all your existing requests work as-is!

### Performance Impact

- **HTTP requests**: Same performance (Hybrid uses CIO internally)
- **HTTPS requests**: Now work! (Previously failed)

## From ktor-client-curl

### Problem
The old curl engine had memory leaks and blocking issues:

```kotlin
// ❌ Memory leaks and thread blocking
val client = HttpClient(Curl)
```

### Solution
Use the Native or Hybrid engine:

```kotlin
// ✅ Memory-safe, non-blocking
import build.kargo.ktor.client.native.Hybrid

val client = HttpClient(Hybrid)
```

### Migration Steps

1. **Remove old curl dependency** from `module.yaml`
2. **Add new dependencies**:
   ```yaml
   dependencies:
     - io.ktor:ktor-client-core:3.4.1
     - io.ktor:ktor-client-cio:3.4.1
   
   settings:
     native:
       cinterop:
         libcurl:
           defFile: resources/cinterop/libcurl.def
           packageName: libcurl
   ```

3. **Update imports**:
   ```kotlin
   // Before
   import io.ktor.client.engine.curl.Curl
   
   // After
   import build.kargo.ktor.client.native.Hybrid
   ```

4. **Update client creation**:
   ```kotlin
   // Before
   val client = HttpClient(Curl)
   
   // After
   val client = HttpClient(Hybrid)
   ```

### Benefits

- ✅ No more memory leaks
- ✅ No thread blocking
- ✅ Better performance for HTTP (uses CIO)
- ✅ Proper coroutine integration

## From Other Platforms (JVM/JS/macOS/Windows)

### Important Context

**This library is only needed for Linux Native**. Other platforms work fine with CIO:

| Platform | HTTPS Support | Recommended Engine |
|----------|---------------|-------------------|
| JVM | ✅ Works | CIO, Apache, OkHttp |
| JavaScript | ✅ Works | Js (browser) |
| macOS Native | ✅ Works | CIO |
| Windows Native | ✅ Works | CIO |
| **Linux Native** | ❌ **Broken** | **Hybrid** (this library) |

### Scenario: Multiplatform Project

If you're building a Kotlin Multiplatform project that includes Linux Native:

**commonMain/Client.kt**:
```kotlin
expect fun createHttpClient(): HttpClient
```

**jvmMain/Client.kt**:
```kotlin
import io.ktor.client.engine.cio.CIO

actual fun createHttpClient() = HttpClient(CIO) {
    // CIO works fine on JVM with HTTPS
}
```

**macosMain/Client.kt** (or iosMain):
```kotlin
import io.ktor.client.engine.cio.CIO

actual fun createHttpClient() = HttpClient(CIO) {
    // CIO works fine on macOS Native with HTTPS
}
```

**linuxX64Main/Client.kt**:
```kotlin
import build.kargo.ktor.client.native.Hybrid

actual fun createHttpClient() = HttpClient(Hybrid) {
    // Only Linux Native needs this library
}
```

**Usage** (in commonMain):
```kotlin
val client = createHttpClient()
val response = client.get("https://api.example.com/data")
// Works on all platforms!
```

### Single Platform (Linux Native Only)

If you're building a Linux-only native application:

```kotlin
import build.kargo.ktor.client.native.Hybrid

val client = HttpClient(Hybrid)
val response = client.get("https://api.example.com/data")
```

## Configuration Migration

### CIO Config → Hybrid Config

```kotlin
// Before (CIO)
val client = HttpClient(CIO) {
    endpoint {
        maxConnectionsPerRoute = 100
        pipelineMaxSize = 20
        keepAliveTime = 5000
        connectTimeout = 5000
        connectAttempts = 5
    }
}

// After (Hybrid)
val client = HttpClient(Hybrid) {
    // HTTP requests use CIO with same config
    // HTTPS requests use libcurl
    
    // Optional: force HTTP/1.1 for HTTPS
    forceHttp1 = false
}
```

### Curl Config → Native Config

```kotlin
// Before (old Curl)
val client = HttpClient(Curl) {
    // Limited config options
}

// After (Native/Hybrid)
val client = HttpClient(Hybrid) {
    forceHttp1 = false // Allow HTTP/2
    
    // Or use Native directly for libcurl-only
    // HttpClient(Native) { ... }
}
```

## Common Plugins

All Ktor plugins work the same way:

```kotlin
val client = HttpClient(Hybrid) {
    install(ContentNegotiation) {
        json()
    }
    
    install(Logging) {
        level = LogLevel.INFO
    }
    
    install(HttpTimeout) {
        requestTimeoutMillis = 30000
    }
    
    install(DefaultRequest) {
        header("User-Agent", "MyApp/1.0")
    }
}
```

## Testing

### Unit Tests

```kotlin
// Before (with CIO)
@Test
fun testHttpRequest() = runBlocking {
    val client = HttpClient(CIO)
    // ... test code
}

// After (with Hybrid)
@Test
fun testHttpRequest() = runBlocking {
    val client = HttpClient(Hybrid)
    // ... same test code
}
```

### Mock Engine

For testing without network calls, use MockEngine (works with all engines):

```kotlin
val mockEngine = MockEngine { request ->
    respond(
        content = """{"status": "ok"}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}

val client = HttpClient(mockEngine)
```

## Troubleshooting

### HTTPS Fails with "SSL/TLS error"

**Cause**: libcurl not installed or missing SSL support

**Solution**:
```bash
# Debian/Ubuntu
sudo apt-get install libcurl4-openssl-dev

# Verify installation
curl --version  # Should show SSL support
```

### HTTP Requests Slower Than Expected

**Cause**: Accidentally forcing libcurl for HTTP

**Solution**:
```kotlin
// Make sure you're NOT forcing curl
val client = HttpClient(Hybrid) {
    forceCurl = false  // Let it use CIO for HTTP
}
```

### Memory Leaks

**Cause**: Not closing the client

**Solution**:
```kotlin
val client = HttpClient(Hybrid)
try {
    // ... use client
} finally {
    client.close()  // Always close!
}
```

### Build Errors with C-Interop

**Cause**: Missing libcurl headers

**Solution**:
```bash
# Install development headers
sudo apt-get install libcurl4-openssl-dev

# Verify headers exist
ls /usr/include/curl/curl.h
```

## Performance Tuning

### For HTTP-Heavy Workloads

If you mostly use HTTP, the Hybrid engine automatically uses CIO (fast):

```kotlin
val client = HttpClient(Hybrid)
// HTTP requests automatically use CIO - no tuning needed!
```

### For HTTPS-Heavy Workloads

If you mostly use HTTPS, consider using Native directly:

```kotlin
val client = HttpClient(Native) {
    forceHttp1 = false  // Enable HTTP/2 for better performance
}
```

### For Mixed Workloads

Use Hybrid (default recommendation):

```kotlin
val client = HttpClient(Hybrid)
// Automatically optimizes based on protocol
```

## Need Help?

- Check [ARCHITECTURE.md](ARCHITECTURE.md) for design details
- See [examples/](../examples/) for code samples
- Open an issue on GitHub for bugs or questions
