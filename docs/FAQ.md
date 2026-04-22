# Frequently Asked Questions (FAQ)

## General Questions

### Q: Why does this library exist?

**A**: The Ktor CIO engine does not support HTTPS on **Linux Native** (`linuxX64`). This library provides a **universal Hybrid engine** that:
- Works across **all platforms** (Linux, macOS, Windows, JVM)
- Uses **CIO** when HTTPS is supported (macOS, Windows, JVM)
- Falls back to **libcurl** for HTTPS on Linux Native
- Provides a **single API** with optimal performance everywhere

### Q: Do I need this library on macOS/Windows/JVM?

**A**: You don't *need* it (CIO works fine), but you can *use* it! Benefits:
- ✅ **Single codebase** across all platforms
- ✅ **Same API** everywhere
- ✅ **Optimal performance** (uses CIO on platforms where it works)
- ✅ **Future-proof** (if you add Linux Native later)

On macOS/Windows/JVM, the Hybrid engine simply uses CIO internally, so there's no performance penalty.

### Q: What's the difference between Native and Hybrid engines?

**A**: 
- **Native Engine**: Uses libcurl for all requests (HTTP and HTTPS)
- **Hybrid Engine**: Uses CIO for HTTP (faster) and libcurl for HTTPS (secure)

**Recommendation**: Use **Hybrid** for best performance.

### Q: Is this production-ready?

**A**: The library is in **Beta** status. It's stable and memory-safe, but we recommend thorough testing in your specific use case before production deployment.

---

## Platform Questions

### Q: Can I use this in a Kotlin Multiplatform project?

**A**: Yes! And it's even simpler now - just use Hybrid everywhere:

```kotlin
// commonMain - single implementation for all platforms!
val client = HttpClient(Hybrid)

// Works optimally on:
// - Linux Native: CIO for HTTP, libcurl for HTTPS
// - macOS Native: CIO for everything
// - Windows Native: CIO for everything  
// - JVM: CIO for everything
```

No need for platform-specific source sets!

### Q: Will you support macOS/Windows Native?

**A**: **Already supported!** The Hybrid engine now works on:
- ✅ Linux Native (linuxX64)
- ✅ macOS Native (macosX64, macosArm64)
- ✅ Windows Native (mingwX64)
- ✅ JVM (all platforms)

On macOS/Windows/JVM, it uses CIO for everything (since CIO supports HTTPS there).

### Q: What about Android Native?

**A**: Android uses JVM/ART, not Kotlin/Native. Use the standard CIO or OkHttp engines on Android.

---

## Technical Questions

### Q: How does the Hybrid engine decide which engine to use?

**A**: It checks the URL protocol:
- `http://` → Routes to CIO engine
- `https://` → Routes to libcurl engine

You can override this with `forceCurl` or `forceCio` config options.

### Q: Does this add overhead to HTTP requests?

**A**: Minimal overhead (~1-2ms) for protocol detection. HTTP requests use CIO directly, so performance is nearly identical to using CIO alone.

### Q: What about HTTP/2 support?

**A**: 
- HTTP requests use CIO (HTTP/1.1 only)
- HTTPS requests use libcurl (HTTP/2 supported, enabled by default)

Set `forceHttp1 = true` to disable HTTP/2 for HTTPS.

### Q: Is it thread-safe?

**A**: Yes! Both CIO and libcurl engines are thread-safe, and the Hybrid engine routes requests without shared mutable state.

### Q: How is memory managed?

**A**: 
- **CIO**: Managed by Kotlin/Native runtime (automatic GC)
- **libcurl**: Uses `StableRef` and `memScoped` for safe C-Interop with explicit cleanup

See [ARCHITECTURE.md](ARCHITECTURE.md) for details.

---

## Performance Questions

### Q: Is libcurl slower than CIO?

**A**: Yes, slightly. libcurl has FFI (Foreign Function Interface) overhead:
- CIO: ~5-10ms latency
- libcurl: ~10-20ms latency

But libcurl is the only option for HTTPS on Linux Native, so the Hybrid engine gives you the best of both worlds.

### Q: Can I benchmark the engines?

**A**: Yes! See [BenchmarkTest.kt](../test/kotlin/build/kargo/ktor/client/native/BenchmarkTest.kt) for examples.

### Q: Should I use connection pooling?

**A**: It's enabled by default in both CIO and libcurl. No additional configuration needed.

---

## Configuration Questions

### Q: What does `forceHttp1` do?

**A**: Forces HTTP/1.1 for HTTPS requests (libcurl). By default, libcurl uses HTTP/2 when available.

```kotlin
val client = HttpClient(Hybrid) {
    forceHttp1 = true // Disable HTTP/2
}
```

### Q: What does `forceCurl` do?

**A**: Forces all requests (HTTP and HTTPS) to use libcurl. Useful for debugging.

```kotlin
val client = HttpClient(Hybrid) {
    forceCurl = true // Use libcurl for everything
}
```

### Q: What does `forceCio` do?

**A**: Forces all requests to use CIO. **Warning**: HTTPS requests will fail on Linux Native!

```kotlin
val client = HttpClient(Hybrid) {
    forceCio = true // Use CIO for everything (HTTPS will fail!)
}
```

### Q: Can I configure SSL/TLS options?

**A**: Currently, libcurl uses system defaults. Custom SSL configuration (certificates, verification) is planned for future releases.

---

## Troubleshooting Questions

### Q: I get "Failed to init curl easy handle" error

**A**: Install libcurl development headers:
```bash
sudo apt-get install libcurl4-openssl-dev
```

### Q: HTTPS requests fail with SSL errors

**A**: Update your CA certificates:
```bash
sudo apt-get install ca-certificates
sudo update-ca-certificates
```

### Q: I'm getting memory leaks

**A**: Make sure you're closing the client:
```kotlin
val client = HttpClient(Hybrid)
try {
    // Use client
} finally {
    client.close() // Always close!
}
```

### Q: Requests are timing out

**A**: Increase timeout values:
```kotlin
val client = HttpClient(Hybrid) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
        connectTimeoutMillis = 10000
    }
}
```

### Q: How do I debug which engine is being used?

**A**: Enable logging:
```kotlin
val client = HttpClient(Hybrid) {
    install(Logging) {
        level = LogLevel.ALL
    }
}
```

Look for log messages like:
- "Using CIO engine for http://..."
- "Using libcurl engine for https://..."

---

## Migration Questions

### Q: I'm using CIO and getting HTTPS errors on Linux Native

**A**: Switch to Hybrid:
```kotlin
// Before
val client = HttpClient(CIO)

// After
val client = HttpClient(Hybrid)
```

See [MIGRATION.md](MIGRATION.md) for details.

### Q: I'm using the old ktor-client-curl

**A**: Switch to Hybrid for better performance and no memory leaks:
```kotlin
// Before
val client = HttpClient(Curl)

// After
val client = HttpClient(Hybrid)
```

### Q: Do I need to change my request code?

**A**: No! The Hybrid engine is a drop-in replacement. All your existing code works as-is.

---

## Build Questions

### Q: How do I build this project?

**A**: 
```bash
./kargo build
```

### Q: How do I run tests?

**A**:
```bash
./kargo test
```

### Q: What are the system requirements?

**A**:
- Linux (Debian/Ubuntu recommended)
- libcurl4-openssl-dev
- Kargo/Amper build system

### Q: Can I use Gradle instead of Kargo?

**A**: This project uses Kargo/Amper. Gradle support is not currently available, but could be added in the future.

---

## Contributing Questions

### Q: How can I contribute?

**A**: 
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

### Q: What kind of contributions are welcome?

**A**:
- Bug fixes
- Performance improvements
- Documentation improvements
- Additional tests
- Examples

### Q: Where should I report bugs?

**A**: Open an issue on GitHub with:
- Description of the problem
- Steps to reproduce
- Expected vs actual behavior
- System information (OS, libcurl version, etc.)

---

## Future Plans

### Q: Will you add WebSocket support?

**A**: It's on the roadmap! WebSocket support would route based on protocol (ws:// vs wss://).

### Q: Will you add custom SSL certificate support?

**A**: Yes, planned for a future release.

### Q: Will you add HTTP/3 support?

**A**: Depends on libcurl and CIO support. Not currently planned.

---

## Still Have Questions?

- Check the [Architecture Guide](ARCHITECTURE.md)
- Check the [Migration Guide](MIGRATION.md)
- Check the [Examples](../examples/)
- Open an issue on GitHub
