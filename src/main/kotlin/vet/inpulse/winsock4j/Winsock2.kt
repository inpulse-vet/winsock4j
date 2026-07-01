package vet.inpulse.winsock4j

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class GUID(val pointer: MemorySegment) {

    companion object {
        val LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("Data1"),
            ValueLayout.JAVA_SHORT.withName("Data2"),
            ValueLayout.JAVA_SHORT.withName("Data3"),
            MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE).withName("Data4"),
        ).withName("GUID")

        val data1Handle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("Data1"))
        val data2Handle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("Data2"))
        val data3Handle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("Data3"))
        val data4Offset = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Data4"))

        fun allocate(arena: Arena): GUID {
            val ptr = arena.allocate(LAYOUT.byteSize())
            return GUID(ptr)
        }

        init {
            assert(LAYOUT.byteSize() == 16L) {
                "GUID must be 16 bytes"
            }
        }
    }

    var Data1: Int
        get() {
            return data1Handle.get(pointer, 0L) as Int
        }
        set(value) {
            data1Handle.set(pointer, 0L, value)
        }

    var Data2: Short
        get() {
            return data2Handle.get(pointer, 0L) as Short
        }
        set(value) {
            data2Handle.set(pointer, 0L, value)
        }

    var Data3: Short
        get() {
            return data3Handle.get(pointer, 0L) as Short
        }
        set(value) {
            data3Handle.set(pointer, 0L, value)
        }

    var Data4: ByteArray
        get() {
            val bytes = pointer.asSlice(data4Offset, 8)
            return bytes.toArray(ValueLayout.JAVA_BYTE)
        }
        set(value) {
            val normalized = value.copyOf(8)
            val dest = pointer.asSlice(data4Offset, 8)
            MemorySegment.copy(
                normalized,
                0,
                dest,
                ValueLayout.JAVA_BYTE,
                0L,
                8
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    fun setFromUuid(uuid: Uuid) {
        uuid.toLongs { mostSignificantBits, leastSignificantBits ->
            val d1 = (mostSignificantBits shr 32).toInt()
            val d2 = (mostSignificantBits shr 16 and 0xFFFF).toShort()
            val d3 = (mostSignificantBits and 0xFFFF).toShort()

            val d4a = leastSignificantBits shr 56 and 0xFF
            val d4b = leastSignificantBits shr 48 and 0xFF
            val d4c = leastSignificantBits shr 40 and 0xFF
            val d4d = leastSignificantBits shr 32 and 0xFF
            val d4e = leastSignificantBits shr 24 and 0xFF
            val d4f = leastSignificantBits shr 16 and 0xFF
            val d4g = leastSignificantBits shr 8 and 0xFF
            val d4h = leastSignificantBits shr 0 and 0xFF

            Data1 = d1
            Data2 = d2
            Data3 = d3
            Data4 = byteArrayOf(
                d4a.toByte(),
                d4b.toByte(),
                d4c.toByte(),
                d4d.toByte(),
                d4e.toByte(),
                d4f.toByte(),
                d4g.toByte(),
                d4h.toByte(),
            )
        }
    }
}

data class SOCKADDR_BTH(val pointer: MemorySegment) {

    companion object {
        // SOCKADDR_BTH is declared with #pragma pack(push, 1) in ws2bth.h — byte-packed, no padding.
        // Field offsets: addressFamily=0, btAddr=2, serviceClassId=10, port=26. Total=30 bytes.
        //
        // Rule: for each field whose natural type-alignment exceeds its actual offset alignment,
        // use withByteAlignment(n) where n is the largest power-of-2 that divides the actual offset.
        //   btAddr  (JAVA_LONG, natural align 8) at offset  2: withByteAlignment(2)
        //   port    (JAVA_INT,  natural align 4) at offset 26: withByteAlignment(2)
        //
        // GUID.LAYOUT cannot be embedded directly: even with withByteAlignment(1) on the group,
        // the group's internal JAVA_INT VarHandle (Data1) still requires a 4-byte-aligned segment
        // address at runtime. At offset 10 the address is only 2-byte aligned, causing
        // IllegalStateException on first access. Solution: use a 16-byte sequence as the wire
        // representation and copy into/out of a properly aligned GUID allocation on access.
        val LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("addressFamily"),                                      // offset  0, size  2
            ValueLayout.JAVA_LONG.withByteAlignment(2).withName("btAddr"),                         // offset  2, size  8
            MemoryLayout.sequenceLayout(16, ValueLayout.JAVA_BYTE).withName("serviceClassId"),     // offset 10, size 16
            ValueLayout.JAVA_INT.withByteAlignment(2).withName("port"),                            // offset 26, size  4
        ).withName("SOCKADDR_BTH")

        val addressFamilyHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("addressFamily"))
        val btAddrHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("btAddr"))
        val serviceClassIdOffset = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("serviceClassId"))
        val portHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("port"))

        fun allocate(arena: Arena): SOCKADDR_BTH {
            val pointer = arena.allocate(LAYOUT)
            return SOCKADDR_BTH(pointer)
        }

        init {
            check(LAYOUT.byteSize() == 30L) {
                "SOCKADDR_BTH must be 30 bytes, is ${LAYOUT.byteSize()}"
            }
        }
    }

    var addressFamily: Short
        get() = addressFamilyHandle.get(pointer, 0L) as Short
        set(value) { addressFamilyHandle.set(pointer, 0L, value) }

    var btAddr: Long
        get() = btAddrHandle.get(pointer, 0L) as Long
        set(value) { btAddrHandle.set(pointer, 0L, value) }

    // The 16 GUID bytes in the packed struct sit at a 2-byte-aligned address (offset 10).
    // We copy them into a fresh, naturally-aligned allocation so that GUID's internal
    // JAVA_INT VarHandle (Data1 at offset 0) sees a 4-byte-aligned base address.
    var serviceClassId: GUID
        get() {
            val guid = GUID.allocate(Arena.ofAuto())
            MemorySegment.copy(pointer, serviceClassIdOffset, guid.pointer, 0L, GUID.LAYOUT.byteSize())
            return guid
        }
        set(value) {
            MemorySegment.copy(value.pointer, 0L, pointer, serviceClassIdOffset, GUID.LAYOUT.byteSize())
        }

    // ULONG (Windows 32-bit) → JAVA_INT VarHandle returns Int; use toUInt() if unsigned range needed.
    var port: Int
        get() = portHandle.get(pointer, 0L) as Int
        set(value) { portHandle.set(pointer, 0L, value) }

}

data class SOCKADDR_IN(val pointer: MemorySegment) {

    companion object {
        // sockaddr_in is naturally aligned (no #pragma pack), so no withByteAlignment is needed.
        // Field offsets: sin_family=0, sin_port=2, sin_addr=4, sin_zero=8. Total=16 bytes.
        //
        // Byte order is the hazard here, not alignment: sin_port and sin_addr are in NETWORK
        // byte order (big-endian) on the wire, while sin_family is host order. We encode this
        // with .withOrder(BIG_ENDIAN) on the two network-order fields, so the Kotlin-facing
        // properties accept ordinary host-order values and the VarHandle does the htons/htonl.
        val LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("sin_family"),                                    // offset 0, size 2
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN).withName("sin_port"),      // offset 2, size 2
            ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withName("sin_addr"),        // offset 4, size 4
            MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE).withName("sin_zero"),       // offset 8, size 8
        ).withName("SOCKADDR_IN")

        val sinFamilyHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sin_family"))
        val sinPortHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sin_port"))
        val sinAddrHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sin_addr"))

        fun allocate(arena: Arena): SOCKADDR_IN {
            val pointer = arena.allocate(LAYOUT)
            return SOCKADDR_IN(pointer)
        }

        init {
            check(LAYOUT.byteSize() == 16L) {
                "SOCKADDR_IN must be 16 bytes, is ${LAYOUT.byteSize()}"
            }
        }
    }

    var sinFamily: Short
        get() = sinFamilyHandle.get(pointer, 0L) as Short
        set(value) { sinFamilyHandle.set(pointer, 0L, value) }

    // Host-order port in/out; the big-endian handle writes network byte order on the wire
    // (e.g. 8080 -> bytes 1F 90).
    var sinPort: Int
        get() = (sinPortHandle.get(pointer, 0L) as Short).toUShort().toInt()
        set(value) { sinPortHandle.set(pointer, 0L, value.toShort()) }

    // Host-order IPv4 address in/out (e.g. 0x7F000001 for 127.0.0.1); the big-endian handle
    // writes network byte order on the wire (bytes 7F 00 00 01). Build with ipv4AddrFromString.
    var sinAddr: Int
        get() = sinAddrHandle.get(pointer, 0L) as Int
        set(value) { sinAddrHandle.set(pointer, 0L, value) }

}

data class WSADATA(val pointer: MemorySegment) {

    companion object {
        val LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("wVersion"),
            ValueLayout.JAVA_SHORT.withName("wHighVersion"),
            MemoryLayout.paddingLayout(12),
            MemoryLayout.sequenceLayout(257, ValueLayout.JAVA_BYTE).withName("szDescription"),
            MemoryLayout.sequenceLayout(129, ValueLayout.JAVA_BYTE).withName("szSystemStatus"),
        ).withName("WSADATA")

        val wVersionHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("wVersion"))
        val wHighVersionHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("wHighVersion"))
        val szDescriptionOffset = LAYOUT.byteOffset(
            MemoryLayout.PathElement.groupElement("szDescription"),
        )
        val szSystemStatusOffset = LAYOUT.byteOffset(
            MemoryLayout.PathElement.groupElement("szSystemStatus"),
        )

        fun allocate(arena: Arena): WSADATA {
            val pointer = arena.allocate(LAYOUT)
            return WSADATA(pointer)
        }
    }

    var wVersion: UShort
        get() {
            val a = wVersionHandle.get(pointer, 0L)
            return (a as Short).toUShort()
        }
        set(value) {
            wVersionHandle.set(pointer, 0L, value.toShort())
        }

    var wHighVersion: UShort
        get() {
            return (wHighVersionHandle.get(pointer, 0L) as Short).toUShort()
        }
        set(value) {
            wHighVersionHandle.set(pointer, 0L, value.toShort())
        }

    val szDescription: String
        get() {
            val slice = pointer.asSlice(szDescriptionOffset, 257)
            return slice.getString(0)
        }

    val szSystemStatus: String
        get() {
            val slice = pointer.asSlice(szSystemStatusOffset, 129)
            return slice.getString(0)
        }
}

@OptIn(ExperimentalUuidApi::class)
object Winsock2 {

    val SPP_UUID = Uuid.parse("00001101-0000-1000-8000-00805f9b34fb")

    const val INVALID_SOCKET = UInt.MAX_VALUE
    const val SOCKET_ERROR = -1

    // Backlog value that lets the stack pick a reasonable maximum queue length (Winsock header value).
    const val SOMAXCONN = 0x7fffffff

    const val WSAEFAULT = 10014
    const val WSAEINPROGRESS = 10036
    const val WSAETIMEDOUT = 10060
    const val WSAEPROCLIM = 10067
    const val WSASYSNOTREADY = 10091
    const val WSAVERNOTSUPPORTED = 10092

    const val AF_UNSPEC = 0
    const val AF_INET = 2
    const val AF_IPX = 6
    const val AF_APPLETALK = 16
    const val AF_NETBIOS = 17
    const val AF_INET6 = 23
    const val AF_IRDA = 26
    const val AF_BTH = 32

    const val SOCK_STREAM = 1
    const val SOCK_DGRAM = 2
    const val SOCK_RAW = 3
    const val SOCK_RDM = 4
    const val SOCK_SEQPACKET = 5

    const val IPPROTO_ICMP = 1
    const val IPPROTO_IGMP = 2
    const val IPPROTO_RFCOMM = 3
    const val IPPROTO_TCP = 6
    const val IPPROTO_UDP = 17
    const val IPPROTO_ICMPV6 = 58
    const val IPPROTO_RM = 113

    const val SD_RECEIVE = 0
    const val SD_SEND = 1
    const val SD_BOTH = 2

    const val MSG_OOB = 0x1
    const val MSG_PEEK = 0x2
    const val MSG_DONTROUTE = 0x4
    const val MSG_WAITALL = 0x8

    private val arena = Arena.global()
    private val linker = Linker.nativeLinker()
    // Lazy: the "GetLastError" capture name only exists on Windows. Evaluating it eagerly here
    // would throw at class-init on non-Windows hosts (the capture-state struct exposes only
    // "errno"), breaking pure-memory unit tests that merely reference this object. The downcall
    // handles that consume css/capturedLastErrorHandle are themselves `by lazy`, so deferral is
    // transparent — on Windows they are still bound before first use.
    private val css by lazy { Linker.Option.captureCallState("GetLastError") }
    // Layout for the struct Panama fills immediately on JVM-to-Java transition,
    // before the JVM can make any internal Windows API call that clobbers GetLastError.
    private val capturedStateLayout = Linker.Option.captureStateLayout()
    private val capturedLastErrorHandle by lazy {
        capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"))
    }
    private val csb = arena.allocate(capturedStateLayout)

    val lib by lazy {
        SymbolLookup.libraryLookup("ws2_32", arena)
    }

    private val wsaStartupHandle by lazy {
        val fn = lib.findOrThrow("WSAStartup")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_SHORT,
            ValueLayout.ADDRESS,
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    fun WSAStartup(version: UShort, out: WSADATA): Int {
        return wsaStartupHandle(csb, version.toShort(), out.pointer) as Int
    }

    private val wsaCleanupHandle by lazy {
        val fn = lib.findOrThrow("WSACleanup")
        val fnDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT)
        linker.downcallHandle(fn, fnDesc)
    }

    fun WSACleanup(): Int {
        return wsaCleanupHandle() as Int
    }

    private val wsaGetLastErrorHandle by lazy {
        val fn = lib.findOrThrow("WSAGetLastError")
        val fnDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT)
        linker.downcallHandle(fn, fnDesc)
    }

    fun WSAGetLastError(): Int {
        return capturedLastErrorHandle.get(csb, 0L) as Int
    }

    private val socketHandle by lazy {
        val fn = lib.findOrThrow("socket")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    fun socket(af: Int, type: Int, protocol: Int): UInt {
        val ret = socketHandle(csb, af, type, protocol) as Int
        return ret.toUInt()
    }

    private val closesocketHandle by lazy {
        val fn = lib.findOrThrow("closesocket")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    fun closesocket(socket: UInt): Int {
        return closesocketHandle(csb, socket.toInt()) as Int
    }

    private val connectHandle by lazy {
        val fn = lib.findOrThrow("connect")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        )
        // captureCallState prepends a leading ADDRESS (the capture buffer) to every invocation.
        // WSAGetLastError and GetLastError read the same Windows TLS slot, so "GetLastError"
        // captures the Winsock error code atomically on JVM re-entry, before anything else runs.
        linker.downcallHandle(fn, fnDesc, css)
    }

    // Returns Pair(connectResult, wsaLastError).
    // connectResult == -1 on failure; wsaLastError is the Winsock error code (e.g. WSAECONNREFUSED).
    fun connect(s: UInt, name: MemorySegment, namelen: Int): Int {
        return connectHandle(csb, s.toInt(), name, namelen) as Int
    }

    private val bindHandle by lazy {
        val fn = lib.findOrThrow("bind")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,   // int return
            ValueLayout.JAVA_INT,   // SOCKET s
            ValueLayout.ADDRESS,    // const sockaddr *addr
            ValueLayout.JAVA_INT,   // int namelen
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    // Returns 0 on success, SOCKET_ERROR (-1) on failure (check WSAGetLastError()).
    fun bind(s: UInt, name: MemorySegment, namelen: Int): Int {
        return bindHandle(csb, s.toInt(), name, namelen) as Int
    }

    private val listenHandle by lazy {
        val fn = lib.findOrThrow("listen")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,   // int return
            ValueLayout.JAVA_INT,   // SOCKET s
            ValueLayout.JAVA_INT,   // int backlog
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    // Returns 0 on success, SOCKET_ERROR (-1) on failure. Use SOMAXCONN for backlog.
    fun listen(s: UInt, backlog: Int): Int {
        return listenHandle(csb, s.toInt(), backlog) as Int
    }

    private val acceptHandle by lazy {
        val fn = lib.findOrThrow("accept")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,   // SOCKET return
            ValueLayout.JAVA_INT,   // SOCKET s
            ValueLayout.ADDRESS,    // sockaddr *addr   (may be MemorySegment.NULL)
            ValueLayout.ADDRESS,    // int *addrlen     (may be MemorySegment.NULL)
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    // Returns the accepted SOCKET, or INVALID_SOCKET on failure (check WSAGetLastError()).
    // `addr` is filled with the peer address; `addrlen` is an in/out int* that must be
    // preset to the byte size of the addr buffer before the call. Pass MemorySegment.NULL
    // for either when the peer address is not needed.
    fun accept(s: UInt, addr: MemorySegment, addrlen: MemorySegment): UInt {
        val ret = acceptHandle(csb, s.toInt(), addr, addrlen) as Int
        return ret.toUInt()
    }

    private val shutdownHandle by lazy {
        val fn = lib.findOrThrow("shutdown")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    fun shutdown(s: UInt, how: Int): Int {
        return shutdownHandle(csb, s.toInt(), how) as Int
    }

    private val sendHandle by lazy {
        val fn = lib.findOrThrow("send")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    fun send(s: UInt, buf: MemorySegment, len: Int, flags: Int): Int {
        return sendHandle(csb, s.toInt(), buf, len, flags) as Int
    }

    private val recvHandle by lazy {
        val fn = lib.findOrThrow("recv")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc, css)
    }

    fun recv(s: UInt, buf: MemorySegment, len: Int, flags: Int): Int {
        return recvHandle(csb, s.toInt(), buf, len, flags) as Int
    }

    fun btAddrFromString(string: String): Long {
        val split = string.split(":")
        require(split.size == 6)
        var addr = 0L
        for (s in split) {
            val b = s.toLong(16)
            addr = (addr shl 8) or b
        }
        return addr
    }

    // Parses dotted-decimal IPv4 into a host-order Int (e.g. "127.0.0.1" -> 0x7F000001).
    // Combined with SOCKADDR_IN.sinAddr's big-endian handle this lands as network order
    // on the wire. Pure Kotlin, so it is unit-testable off Windows.
    fun ipv4AddrFromString(string: String): Int {
        val parts = string.split(".")
        require(parts.size == 4) { "IPv4 address must have 4 octets: $string" }
        var addr = 0
        for (p in parts) {
            val octet = p.toInt()
            require(octet in 0..255) { "Octet out of range: $p" }
            addr = (addr shl 8) or octet
        }
        return addr
    }

}