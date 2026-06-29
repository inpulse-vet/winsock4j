package io.github.inpulse.io.github.inpulse.winsock4j

import io.github.inpulse.io.github.inpulse.winsock4j.GUID.Companion.data4Offset
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles

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
}

data class SOCKADDR_BTH(val pointer: MemorySegment) {

    companion object {
        val LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("addressFamily"),
            ValueLayout.JAVA_LONG.withName("btAddr").withByteAlignment(2),
            GUID.LAYOUT.withName("serviceClassId").withByteAlignment(1),
            ValueLayout.JAVA_INT.withName("port"),
        )

        val addressFamilyHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("addressFamily"))
        val btAddrHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("btAddr"))
        val serviceClassIdHandle = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("serviceClassId"))
        val portHandle = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("port"))

        fun allocate(arena: Arena): SOCKADDR_BTH {
            val pointer = arena.allocate(LAYOUT)
            return SOCKADDR_BTH(pointer)
        }

        init {
            val size = SOCKADDR_BTH.LAYOUT.byteSize()
            assert(size == 30L) {
                "SOCKADDR_BTH must be 30 bytes, is $size"
            }
        }
    }

    var addressFamily: Short
        get() {
            return addressFamilyHandle.get(pointer, 0L) as Short
        }
        set(value) {
            addressFamilyHandle.set(pointer, 0L, value)
        }

    var btAddr: Long
        get() {
            return btAddrHandle.get(pointer, 0L) as Long
        }
        set(value) {
            btAddrHandle.set(pointer, 0L, value)
        }

    var serviceClassId: GUID
        get() {
            val guidPtr = pointer.asSlice(serviceClassIdHandle, GUID.LAYOUT.byteSize())
            return GUID(guidPtr)
        }
        set(value) {
            val guidPtr = pointer.asSlice(serviceClassIdHandle, GUID.LAYOUT.byteSize())
            MemorySegment.copy(
                value.pointer,
                0L,
                guidPtr,
                0L,
                GUID.LAYOUT.byteSize(),
            )
        }

    var port: Long
        get() {
            return portHandle.get(pointer, 0) as Long
        }
        set(value) {
            portHandle.set(pointer, 0, value)
        }

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

object Winsock2 {

    const val INVALID_SOCKET = UInt.MAX_VALUE

    const val WSAEFAULT = 10014
    const val WSASYSNOTREADY = 10091
    const val WSAVERNOTSUPPORTED = 10092
    const val WSAEINPROGRESS = 10036
    const val WSAEPROCLIM = 10067

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

    private val arena = Arena.global()
    private val linker = Linker.nativeLinker()

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
        linker.downcallHandle(fn, fnDesc)
    }

    fun WSAStartup(version: UShort, out: WSADATA): Int {
        return wsaStartupHandle(version.toShort(), out.pointer) as Int
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
        return wsaGetLastErrorHandle() as Int
    }

    private val socketHandle by lazy {
        val fn = lib.findOrThrow("socket")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc)
    }

    fun socket(af: Int, type: Int, protocol: Int): UInt {
        val ret = socketHandle(af, type, protocol) as Int
        return ret.toUInt()
    }

    private val closesocketHandle by lazy {
        val fn = lib.findOrThrow("closesocket")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc)
    }

    fun closesocket(socket: UInt): Int {
        return closesocketHandle(socket.toInt()) as Int
    }

    private val connectHandle by lazy {
        val fn = lib.findOrThrow("connect")
        val fnDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        )
        linker.downcallHandle(fn, fnDesc)
    }

    fun connect(s: UInt, name: MemorySegment, namelen: Int): Int {
        return connectHandle(s.toInt(), name, namelen) as Int
    }

}