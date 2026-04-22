# Project Structure

This document explains the multiplatform structure of ktor-client-native.

## Directory Layout

The project follows Amper's convention-based source set structure:

```
ktor-client-native/
├── src/                          # Common code (all platforms)
│   └── build/kargo/ktor/client/native/
│       ├── HybridEngine.kt       # expect declarations
│       └── NativeEngine.kt       # Linux Native libcurl engine
│
├── src@linuxX64/                 # Linux Native specific
│   └── build/kargo/ktor/client/native/
│       └── HybridEngine.kt       # actual: uses CIO + libcurl
│
├── src@macosX64/                 # macOS x64 specific
│   └── build/kargo/ktor/client/native/
│       └── HybridEngine.kt       # actual: uses CIO only
│
├── src@macosArm64/               # macOS ARM specific
│   └── build/kargo/ktor/client/native/
│       └── HybridEngine.kt       # actual: uses CIO only
│
├── src@mingwX64/                 # Windows specific
│   └── build/kargo/ktor/client/native/
│       └── HybridEngine.kt       # actual: uses CIO only
│
├── src@jvm/                      # JVM specific
│   └── build/kargo/ktor/client/native/
│       └── HybridEngine.kt       # actual: uses CIO only
│
├── test/                         # Common tests
├── resources/                    # Resources
│   └── cinterop/
│       └── libcurl.def           # C-Interop definition (Linux only)
│
└── module.yaml                   # Amper configuration
```

## Platform-Specific Implementations

### Common Code (`src/`)

Contains:
- **HybridEngine.kt**: `expect class` declarations for the Hybrid engine
- **NativeEngine.kt**: libcurl-based engine (Linux Native only)

### Linux Native (`src@linuxX64/`)

**Implementation**: CIO for HTTP, libcurl for HTTPS

**Why**: CIO doesn't support HTTPS on Linux Native, so we use libcurl as fallback.

**Dependencies**:
- Requires `libcurl4-openssl-dev` system package
- Uses C-Interop for libcurl bindings

### macOS Native (`src@macosX64/`, `src@macosArm64/`)

**Implementation**: CIO for everything (HTTP and HTTPS)

**Why**: CIO supports HTTPS natively on macOS using the Security framework.

**Dependencies**: None (CIO works out of the box)

### Windows Native (`src@mingwX64/`)

**Implementation**: CIO for everything (HTTP and HTTPS)

**Why**: CIO supports HTTPS natively on Windows using SChannel.

**Dependencies**: None (CIO works out of the box)

### JVM (`src@jvm/`)

**Implementation**: CIO for everything (HTTP and HTTPS)

**Why**: CIO supports HTTPS natively on JVM using JSSE.

**Dependencies**: None (CIO works out of the box)

## How Amper Resolves Source Sets

Amper uses a **convention-based** approach:

1. **Common code** goes in `src/`
2. **Platform-specific code** goes in `src@<platform>/`
3. The `@platform` qualifier matches the platform name in `module.yaml`

Example from `module.yaml`:
```yaml
product:
  type: lib
  platforms: [linuxX64, macosX64, macosArm64, mingwX64, jvm]
```

Amper automatically:
- Compiles `src/` for all platforms
- Compiles `src@linuxX64/` only for Linux Native
- Compiles `src@macosX64/` only for macOS x64
- Compiles `src@macosArm64/` only for macOS ARM
- Compiles `src@mingwX64/` only for Windows
- Compiles `src@jvm/` only for JVM

## Platform-Specific Settings

Platform-specific settings use the `@platform` qualifier in `module.yaml`:

```yaml
# Linux Native specific: libcurl C-Interop (only needed for HTTPS support)
settings@linuxX64:
  native:
    cinterop:
      libcurl:
        defFile: resources/cinterop/libcurl.def
        packageName: libcurl
```

This ensures libcurl is only compiled for Linux Native, not for other platforms.

## Build Process

### Building for All Platforms

```bash
./kargo build
```

This builds for all platforms defined in `module.yaml`.

### Building for Specific Platform

```bash
./kargo build --platform linuxX64
./kargo build --platform macosX64
./kargo build --platform jvm
```

### Running Tests

```bash
./kargo test
```

Tests run on all platforms.

## Adding a New Platform

To add support for a new platform (e.g., `iosArm64`):

1. **Add platform to `module.yaml`**:
   ```yaml
   product:
     platforms: [linuxX64, macosX64, macosArm64, mingwX64, jvm, iosArm64]
   ```

2. **Create platform-specific directory**:
   ```bash
   mkdir -p src@iosArm64/build/kargo/ktor/client/native
   ```

3. **Implement `actual class HybridEngine`**:
   ```kotlin
   // src@iosArm64/build/kargo/ktor/client/native/HybridEngine.kt
   actual class HybridEngine actual constructor(
       override val config: HybridEngineConfig
   ) : HttpClientEngineBase("ktor-hybrid-ios") {
       // Implementation using CIO (HTTPS works on iOS)
   }
   ```

4. **Build and test**:
   ```bash
   ./kargo build --platform iosArm64
   ./kargo test --platform iosArm64
   ```

## Troubleshooting

### "Unresolved reference: HybridEngine"

**Cause**: Missing platform-specific implementation.

**Solution**: Ensure you have an `actual class HybridEngine` in `src@<platform>/`.

### "libcurl not found" (Linux Native only)

**Cause**: Missing libcurl development headers.

**Solution**:
```bash
sudo apt-get install libcurl4-openssl-dev
```

### Build fails on macOS/Windows/JVM

**Cause**: Trying to use libcurl on platforms that don't need it.

**Solution**: Ensure `settings@linuxX64` is used (not `settings`) in `module.yaml`.

## References

- [Amper Documentation](https://github.com/JetBrains/amper)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Ktor Client](https://ktor.io/docs/client.html)
