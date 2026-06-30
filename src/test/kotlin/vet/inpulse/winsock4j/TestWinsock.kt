package vet.inpulse.winsock4j

import vet.inpulse.winsock4j.Winsock2.SPP_UUID
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class TestWinsock {

    @Test
    fun testGuidSet() {
        Arena.ofConfined().use { arena ->
            val guid = GUID.allocate(arena)
            guid.setFromUuid(SPP_UUID)

            assertEquals(0x00001101 ,guid.Data1, "Data1 does not match")
            assertEquals(0x0000 ,guid.Data2, "Data2 does not match")
            assertEquals(0x1000 ,guid.Data3, "Data3 does not match")
            assertEquals(0x80.toByte() ,guid.Data4[0], "Data4 does not match")
            assertEquals(0x00.toByte() ,guid.Data4[1], "Data4 does not match")
            assertEquals(0x00.toByte() ,guid.Data4[2], "Data4 does not match")
            assertEquals(0x80.toByte() ,guid.Data4[3], "Data4 does not match")
            assertEquals(0x5f.toByte() ,guid.Data4[4], "Data4 does not match")
            assertEquals(0x9b.toByte() ,guid.Data4[5], "Data4 does not match")
            assertEquals(0x34.toByte() ,guid.Data4[6], "Data4 does not match")
            assertEquals(0xfb.toByte() ,guid.Data4[7], "Data4 does not match")
        }
    }

    @Test
    fun testSockaddrIn() {
        assertEquals(16L, SOCKADDR_IN.LAYOUT.byteSize(), "SOCKADDR_IN must be 16 bytes")

        // ipv4AddrFromString produces host-order ints.
        assertEquals(0x7F000001, Winsock2.ipv4AddrFromString("127.0.0.1"))
        assertEquals(0, Winsock2.ipv4AddrFromString("0.0.0.0"))
        assertEquals(-1, Winsock2.ipv4AddrFromString("255.255.255.255")) // 0xFFFFFFFF
        assertFailsWith<IllegalArgumentException> { Winsock2.ipv4AddrFromString("1.2.3") }
        assertFailsWith<IllegalArgumentException> { Winsock2.ipv4AddrFromString("1.2.3.256") }

        Arena.ofConfined().use { arena ->
            val sockaddr = SOCKADDR_IN.allocate(arena)
            sockaddr.sinFamily = Winsock2.AF_INET.toShort()
            sockaddr.sinPort = 8080
            sockaddr.sinAddr = Winsock2.ipv4AddrFromString("127.0.0.1")

            // Round-trip through the host-order properties.
            assertEquals(Winsock2.AF_INET.toShort(), sockaddr.sinFamily, "sin_family")
            assertEquals(8080, sockaddr.sinPort, "sin_port")
            assertEquals(0x7F000001, sockaddr.sinAddr, "sin_addr")

            // Verify the bytes actually on the wire are network (big-endian) order.
            val wire = sockaddr.pointer.toArray(ValueLayout.JAVA_BYTE)
            // sin_port at offset 2: 8080 = 0x1F90 big-endian.
            assertEquals(0x1F.toByte(), wire[2], "sin_port byte 0")
            assertEquals(0x90.toByte(), wire[3], "sin_port byte 1")
            // sin_addr at offset 4: 127.0.0.1 in order.
            assertEquals(127.toByte(), wire[4], "sin_addr byte 0")
            assertEquals(0.toByte(), wire[5], "sin_addr byte 1")
            assertEquals(0.toByte(), wire[6], "sin_addr byte 2")
            assertEquals(1.toByte(), wire[7], "sin_addr byte 3")
        }
    }

    @Test
    fun startup() {
        Arena.ofConfined().use { arena ->
            val wsaData = WSADATA.allocate(arena)
            val version = 0x0202.toUShort()
            val ret = Winsock2.WSAStartup(version, wsaData)
            println("WSAStartup: $ret")
            println(wsaData.wVersion)
            println(wsaData.wHighVersion)
            println(wsaData.szDescription)
            println(wsaData.szSystemStatus)

            val socket = Winsock2.socket(
                Winsock2.AF_BTH,
                Winsock2.SOCK_STREAM,
                Winsock2.IPPROTO_RFCOMM
            )
            if (socket == Winsock2.INVALID_SOCKET) {
                println("Invalid SOCKET")
            }

            val guid = GUID.allocate(arena)
            guid.setFromUuid(Winsock2.SPP_UUID)

            // 68:0a:e2:52:b6:a8 INCARDIO
            // 88:6b:0f:ad:b9:5b INMONITOR
            // 00:81:f9:19:40:2a INMONITOR
            // 88:6b:0f:ad:b9:b5 INMONITOR
            // 30:c9:22:16:09:3a INcardio X
            // 90:38:0c:fa:b7:42 INmonitor

            val sockaddr = SOCKADDR_BTH.allocate(arena)
            sockaddr.addressFamily = Winsock2.AF_BTH.toShort()
            sockaddr.btAddr = Winsock2.btAddrFromString("90:38:0c:fa:b7:42")
            sockaddr.port = 0
            sockaddr.serviceClassId = guid

            val connectRes = Winsock2.connect(socket, sockaddr.pointer, SOCKADDR_BTH.LAYOUT.byteSize().toInt())
            if (connectRes != 0) {
                val error = Winsock2.WSAGetLastError()
                println("connect error: $error")
            } else {
                println("CONNECTED!")
            }

            val getInfoMsg = arena.allocateFrom(
                ValueLayout.JAVA_BYTE,
                0x7e.toByte(),
                0x00.toByte(),
                0x05.toByte(),
                0x15.toByte(),
                0x98.toByte(),
            )

            val until = System.currentTimeMillis() + 50
            val recvBuffer = arena.allocate(512)
            while (System.currentTimeMillis() < until) {
                val written = Winsock2.send(socket, getInfoMsg, 5, 0)
                if (written == -1) {
                    val error = Winsock2.WSAGetLastError()
                    error("send: $error")
                }
                println("send: $written")

                val received = Winsock2.recv(socket, recvBuffer, 512, 0)
                if (received == -1) {
                    val error = Winsock2.WSAGetLastError()
                    error("recv: $error")
                }
                val recvBufferArray = ByteArray(received)
                MemorySegment.copy(recvBuffer, ValueLayout.JAVA_BYTE, 0, recvBufferArray, 0, received)
                println("recv: $received <-- ${recvBufferArray.toHexString()}")
            }

            val shutdown = Winsock2.shutdown(socket, Winsock2.SD_BOTH)
            if (shutdown != 0) {
                val error = Winsock2.WSAGetLastError()
                println("shutdown error: $error")
            }

            val close = Winsock2.closesocket(socket)
            if (close != 0) {
                println("Close error")
            }

            val success = Winsock2.WSACleanup()
            if (success != 0) {
                val lastError = Winsock2.WSAGetLastError()
                println(lastError)
            }
        }
    }

}