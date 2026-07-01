# winsock4j

[![Publish to Maven Central](https://github.com/inpulse-vet/winsock4j/actions/workflows/publish.yml/badge.svg)](https://github.com/inpulse-vet/winsock4j/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/vet.inpulse/winsock4j?logo=apachemaven&color=blue)](https://central.sonatype.com/artifact/vet.inpulse/winsock4j)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Kotlin bindings to the Windows Winsock2 (`ws2_32`) API via the Java FFM API (Project Panama,
`java.lang.foreign`), focused on **Bluetooth RFCOMM** sockets (`AF_BTH` / `IPPROTO_RFCOMM`) — connecting
to Bluetooth Serial Port Profile (SPP) devices and exchanging bytes over them. There is no JNI and no
native build step: native calls go directly through `Linker.downcallHandle`.

> **Windows only.** This library binds `ws2_32` and only runs on Windows. It requires **JDK 25** (the FFM
> API is final as of Java 25).

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("vet.inpulse:winsock4j:0.1.0")
}
```

Maven:

```xml
<dependency>
    <groupId>vet.inpulse</groupId>
    <artifactId>winsock4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

The canonical native call sequence (see `TestWinsock`):

```kotlin
Arena.ofConfined().use { arena ->
    val wsaData = WSADATA.allocate(arena)
    Winsock2.WSAStartup(0x0202.toUShort(), wsaData)

    val socket = Winsock2.socket(Winsock2.AF_BTH, Winsock2.SOCK_STREAM, Winsock2.IPPROTO_RFCOMM)

    val guid = GUID.allocate(arena)
    guid.setFromUuid(Winsock2.SPP_UUID)

    val addr = SOCKADDR_BTH.allocate(arena)
    addr.addressFamily = Winsock2.AF_BTH.toShort()
    addr.btAddr = Winsock2.btAddrFromString("90:38:0c:fa:b7:42")
    addr.port = 0
    addr.serviceClassId = guid

    Winsock2.connect(socket, addr.pointer, SOCKADDR_BTH.LAYOUT.byteSize().toInt())
    // send / recv ...
    Winsock2.shutdown(socket, Winsock2.SD_BOTH)
    Winsock2.closesocket(socket)
    Winsock2.WSACleanup()
}
```

## Building

```bash
./gradlew build    # compile + test (the native tests only pass on Windows)
```

## Publishing

Releases are published to Maven Central automatically by GitHub Actions when a GitHub Release is created.
See [PUBLISHING.md](PUBLISHING.md) for the full setup and release runbook.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
