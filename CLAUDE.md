# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin/JVM binding to the Windows Winsock2 API (`ws2_32.dll`), built with the Java FFM API (Project Panama, `java.lang.foreign`). The current focus is **Bluetooth RFCOMM** sockets (`AF_BTH` / `IPPROTO_RFCOMM`) — connecting to Bluetooth Serial Port Profile (SPP) devices and exchanging bytes over them. There is no JNI and no native build step: native calls go directly through `Linker.downcallHandle`.

## Platform constraint

This binds `ws2_32` and **only runs on Windows**. The dev/CI host is Linux, so `./gradlew test` will compile but fail at runtime when a test touches `Winsock2` (the `libraryLookup("ws2_32")` / downcalls have no equivalent off Windows). Pure-memory tests like `testGuidSet` (struct marshalling, no native call) are the only ones that can pass cross-platform. Don't assume a test failure here is a code regression — check whether it's just the platform.

## Build & test

```bash
./gradlew build              # compile + test
./gradlew test               # run tests (JUnit Platform)
./gradlew test --tests "vet.inpulse.winsock4j.TestWinsock.testGuidSet"   # single test
```

Requires **JDK 25** (`jvmToolchain(25)` in `build.gradle.kts`; the foojay resolver auto-provisions it). The FFM API is final in 25 — no `--enable-preview` / `--enable-native-access` flags are wired up, so if a future JDK warns about restricted native access, that's where it would go.

## Architecture

All native binding lives in `src/main/kotlin/vet/inpulse/winsock4j/Winsock2.kt`.

- **`Winsock2`** (object): the entry point. Holds a global `Arena`, the `ws2_32` `SymbolLookup`, the `Linker`, and one lazily-bound `MethodHandle` per native function (`WSAStartup`, `socket`, `connect`, `send`, `recv`, `shutdown`, `closesocket`, `WSACleanup`). Each Kotlin wrapper function converts Kotlin types to/from the C ABI types and invokes its handle. Also holds the Winsock constants (`AF_BTH`, `SOCK_STREAM`, error codes, etc.) and helpers like `btAddrFromString`.
- **Struct wrappers** (`GUID`, `SOCKADDR_BTH`, `WSADATA`): each is a `data class` wrapping a `MemorySegment`, with a companion `LAYOUT` (`MemoryLayout`), cached `VarHandle`s, and an `allocate(arena)` factory. Fields are exposed as Kotlin properties backed by the VarHandles.

### Error handling: GetLastError capture

Winsock reports failures via `WSAGetLastError()`, which reads thread-local state that the JVM can clobber on the way out of a downcall. This binding uses `Linker.Option.captureCallState("GetLastError")`: a capture-state struct (`csb`) is allocated once and passed as the **leading argument** to every error-prone downcall. `Winsock2.WSAGetLastError()` reads the captured value out of that struct via `capturedLastErrorHandle` rather than calling the native function. When adding a new downcall that can fail, bind it with the `css` option and pass `csb` first (see `connect`/`send`/`recv`).

### Struct layout & alignment — the central hazard

This is the part that bites. Native struct fields must match the C layout exactly, **including alignment**, or you get silent corruption or `IllegalStateException` at access time. Two patterns established in the code:

- **Packed structs** (`SOCKADDR_BTH`, declared `#pragma pack(push,1)` in `ws2bth.h`): fields land at non-natural offsets. Use `.withByteAlignment(n)` on each field, where `n` is the largest power of 2 dividing the field's actual offset (e.g. `btAddr` at offset 2 → `withByteAlignment(2)`). Each struct's companion `init {}` asserts the computed `byteSize()` matches the C size (30 for `SOCKADDR_BTH`, 16 for `GUID`) — keep these checks when editing layouts.
- **Nested structs at unaligned offsets**: a `GUID` cannot be embedded directly inside the packed `SOCKADDR_BTH`, because `GUID`'s internal `JAVA_INT` VarHandle still demands a 4-byte-aligned base address even with `withByteAlignment(1)` on the group. Instead the GUID is stored as a raw 16-byte sequence on the wire and **copied** into/out of a freshly-allocated, naturally-aligned `GUID` on property access (see `SOCKADDR_BTH.serviceClassId`). Use this copy-in/copy-out approach for any nested struct sitting at an unaligned offset.

The header comments in `SOCKADDR_BTH` document the offset/alignment reasoning in detail — read them before changing any layout.

## Usage shape (from `TestWinsock.startup`)

The canonical native call sequence: `WSAStartup(0x0202, …)` → `socket(AF_BTH, SOCK_STREAM, IPPROTO_RFCOMM)` → fill a `SOCKADDR_BTH` (`addressFamily`, `btAddr` via `btAddrFromString("aa:bb:…")`, `port`, `serviceClassId` = SPP GUID) → `connect` → `send`/`recv` loop → `shutdown(SD_BOTH)` → `closesocket` → `WSACleanup`. Allocate every struct from a single confined `Arena` (`Arena.ofConfined().use { … }`) so memory is released deterministically. Note `startup` connects to real Bluetooth hardware by MAC address — it is a hardware-dependent integration test, not a unit test.
