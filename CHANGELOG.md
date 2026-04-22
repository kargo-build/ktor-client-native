# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Multiplatform Support**: Hybrid engine now works on all platforms:
  - ✅ Linux Native (linuxX64) - uses CIO for HTTP, libcurl for HTTPS
  - ✅ macOS Native (macosX64, macosArm64) - uses CIO for everything
  - ✅ Windows Native (mingwX64) - uses CIO for everything
  - ✅ JVM - uses CIO for everything
  
- **Platform-Specific Implementations**:
  - `HybridEngineLinux.kt` - Linux Native with CIO + libcurl
  - `HybridEngineNative.kt` - macOS/Windows Native with CIO only
  - `HybridEngineJvm.kt` - JVM with CIO only
  - Automatic platform detection and optimal engine selection
  
- **Single Codebase**: Write once, works optimally everywhere
  - No need for platform-specific code in your application
  - Same API across all platforms
  - Optimal performance on each platform
  
- **Documentation**:
  - Architecture guide explaining multiplatform design
  - Migration guide for multiplatform projects
  - Complete working examples
  - FAQ covering platform-specific questions
  
- **Tests**:
  - Comprehensive test suite for Hybrid engine
  - HTTP and HTTPS request tests
  - Mixed protocol tests
  - Concurrent request tests
  - Benchmark tests comparing engines

### Changed
- **module.yaml**: Added support for macosX64, macosArm64, mingwX64, and jvm platforms
- **README.md**: Updated with multiplatform information and platform comparison table
- **libcurl C-Interop**: Now only compiled for Linux Native (not needed on other platforms)

### Performance
- **Linux Native**: HTTP uses CIO (fast), HTTPS uses libcurl (secure)
- **macOS/Windows/JVM**: Everything uses CIO (fast for both HTTP and HTTPS)
- No performance regression on any platform
- Optimal engine selection based on platform capabilities

## [3.4.1] - 2024-XX-XX

### Changed
- Updated Ktor to 3.4.1

## [3.3.0] - 2024-XX-XX

### Added
- Support for `OutgoingContent.WriteChannelContent`

### Fixed
- Content header handling

## [Earlier Versions]

### Added
- Initial Native engine implementation using libcurl
- Memory-safe C-Interop with proper cleanup
- Non-blocking request handling with `curl_multi_*`
- Backpressure support with pause/resume
- Streaming response body via ByteChannel
- Custom header support
- POST/PUT request body support

---

## Migration Notes

### From CIO Engine
If you were using CIO and hitting HTTPS errors on Linux Native:
```kotlin
// Before (fails on HTTPS)
val client = HttpClient(CIO)

// After (works with both HTTP and HTTPS)
val client = HttpClient(Hybrid)
```

### From Native Engine
The Native engine still works as before, but Hybrid is now recommended:
```kotlin
// Before (works but slower for HTTP)
val client = HttpClient(Native)

// After (faster for HTTP, same for HTTPS)
val client = HttpClient(Hybrid)
```

See [docs/MIGRATION.md](docs/MIGRATION.md) for detailed migration instructions.
