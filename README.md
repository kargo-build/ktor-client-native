# ktor-client-hybrid

**ktor-client-hybrid** is a memory-safe, Kotlin/Native-first HTTP client engine compatible with Ktor Client.

## Why This Project Exists

The official **Ktor CIO engine does not support HTTPS on Kotlin/Native Linux targets**, making it impossible to make secure HTTP requests in native Linux applications.

This project provides a **universal Hybrid engine** that works across all platforms:

| Platform | HTTP | HTTPS | Implementation |
|----------|------|-------|----------------|
| **Linux Native** | ✅ CIO | ✅ libcurl | Hybrid (CIO + libcurl) |
| **macOS Native** | ✅ CIO | ✅ CIO | Hybrid (CIO only) |
| **Windows Native** | ✅ CIO | ✅ CIO | Hybrid (CIO only) |
| **JVM** | ✅ CIO | ✅ CIO | Hybrid (CIO only) |

**Key Benefits**:
- ✅ **Single API** across all platforms
- ✅ **Optimal performance** (uses CIO when possible)
- ✅ **HTTPS support** on Linux Native (via libcurl)
- ✅ **Drop-in replacement** for CIO
- ✅ **No platform-specific code** needed in your app

### How It Works

The Hybrid engine automatically adapts to each platform:

**Linux Native** (where CIO doesn't support HTTPS):
```
HTTP  → CIO (fast)
HTTPS → libcurl (secure)
```

**macOS/Windows/JVM** (where CIO supports HTTPS):
```
HTTP  → CIO (fast)
HTTPS → CIO (fast + secure)
```

This means you write code once, and it works optimally everywhere!

## Goals

-   **HTTPS Support**: Full SSL/TLS support via libcurl where CIO fails
-   **Zero Memory Leaks**: Predictable resource lifecycle using strict `StableRef` and `memScoped` cleanup blocks
-   **No Main-Thread Blocking**: The engine delegates native polling to a background Worker Coroutine, preventing your UI or main event loop from freezing
-   **Streamlined Data Flow**: Data gets pushed directly into Kotlin `ByteChannel`s on arrival instead of buffering huge blocks in C memory
-   **High Performance**: Uses CIO when possible for maximum speed, falls back to libcurl only when needed

---

## 🚀 Usage & Setup

### 1. System Requirements

**Linux Native** (requires libcurl for HTTPS):
```bash
sudo apt-get install libcurl4-openssl-dev
```

**macOS/Windows/JVM**: No additional requirements (CIO works natively)

### 2. Kargo / Amper Configuration

Update your `module.yaml` to declare the engine dependency and point the build system to the C-Interop wrapper definition included in this library.

```yaml
dependencies:
  - io.ktor:ktor-client-core:3.4.1
  - io.ktor:ktor-client-cio:3.4.1
  # And other ktor dependencies...

settings:
  native:
    cinterop:
      libcurl:
        defFile: resources/cinterop/libcurl.def
        packageName: libcurl
```

*(Note: The `cinterop` block is supported by the Kargo build system, solving seamless native interactions).*

### 3. Using the Hybrid Engine (Recommended)

The **Hybrid engine** works across all platforms with optimal performance:

```kotlin
import build.kargo.ktor.client.native.Hybrid
import io.ktor.client.*

val client = HttpClient(Hybrid) {
    // Works on Linux, macOS, Windows, and JVM!
}

// HTTP request - uses CIO on all platforms (fast)
val httpResponse = client.get("http://api.example.com/data")

// HTTPS request:
// - Linux Native: uses libcurl (secure)
// - macOS/Windows/JVM: uses CIO (fast + secure)
val httpsResponse = client.get("https://api.example.com/secure")
```

**Platform-Specific Behavior**:
- **Linux Native**: CIO for HTTP, libcurl for HTTPS
- **macOS/Windows/JVM**: CIO for everything (HTTPS supported natively)

**No platform-specific code needed!** The same code works optimally everywhere.

### 4. Using the Native Engine (Linux Native only)

If you want to use libcurl for all requests on Linux Native:

```kotlin
import build.kargo.ktor.client.native.Native
import io.ktor.client.*

val client = HttpClient(Native) {
    // Configure Ktor as usual
}
```

**Note**: The Native engine is only available on Linux Native.

---

## 📊 Engine Comparison

| Feature | Hybrid Engine | Native Engine | CIO Engine |
|---------|--------------|---------------|------------|
| **Platforms** | Linux, macOS, Windows, JVM | Linux only | All platforms |
| **HTTP Support** | ✅ (CIO) | ✅ (libcurl) | ✅ |
| **HTTPS on Linux Native** | ✅ (libcurl) | ✅ (libcurl) | ❌ |
| **HTTPS on macOS/Windows/JVM** | ✅ (CIO) | N/A | ✅ |
| **Performance (HTTP)** | ⚡ Fast (CIO) | 🐢 Moderate | ⚡ Fast |
| **Performance (HTTPS)** | ⚡ Fast (platform-dependent) | 🐢 Moderate | ⚡ Fast (except Linux) |
| **Memory Safety** | ✅ | ✅ | ✅ |
| **Single Codebase** | ✅ **Best choice** | Linux only | Fails on Linux HTTPS |

---

## 📚 Examples

### Basic Usage

```kotlin
import build.kargo.ktor.client.native.Hybrid
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

suspend fun fetchData() {
    val client = HttpClient(Hybrid)
    
    // HTTP request - automatically uses CIO
    val httpData = client.get("http://api.example.com/data").bodyAsText()
    
    // HTTPS request - automatically uses libcurl
    val secureData = client.get("https://api.example.com/secure").bodyAsText()
    
    client.close()
}
```

### POST Request with JSON

```kotlin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val client = HttpClient(Hybrid) {
    install(ContentNegotiation) {
        json()
    }
}

val response = client.post("https://api.example.com/users") {
    contentType(ContentType.Application.Json)
    setBody(User(name = "John", email = "john@example.com"))
}
```

### With Logging

```kotlin
import io.ktor.client.plugins.logging.*

val client = HttpClient(Hybrid) {
    install(Logging) {
        level = LogLevel.INFO
    }
}
```

See [examples/HybridEngineExample.kt](examples/HybridEngineExample.kt) for more complete examples.

---

## 📖 Documentation

- **[Architecture Guide](docs/ARCHITECTURE.md)** - Deep dive into the hybrid engine design
- **[Migration Guide](docs/MIGRATION.md)** - Migrate from CIO, Curl, or other engines
- **[Examples](examples/)** - Complete working examples

---

## Status

**Beta**. The engine provides a stable memory-safe foundation for HTTP/1.1 networking on `linuxX64` targets, acting as the primary driver for high-performance Native Kotlin apps on the Kargo toolchain.
