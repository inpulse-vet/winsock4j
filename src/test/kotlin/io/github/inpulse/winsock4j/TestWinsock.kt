package io.github.inpulse.winsock4j

import io.github.inpulse.winsock4j.Winsock2.SPP_UUID
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
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