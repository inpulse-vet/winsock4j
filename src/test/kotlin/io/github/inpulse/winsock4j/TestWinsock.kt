package io.github.inpulse.winsock4j

import io.github.inpulse.io.github.inpulse.winsock4j.GUID
import io.github.inpulse.io.github.inpulse.winsock4j.SOCKADDR_BTH
import io.github.inpulse.io.github.inpulse.winsock4j.WSADATA
import io.github.inpulse.io.github.inpulse.winsock4j.Winsock2
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import kotlin.test.Test

class TestWinsock {

    @Test
    fun startup() {
        Arena.ofConfined().use { arena ->
            val wsaData = WSADATA.allocate(arena)
            val version = 0x0202.toUShort()
            val ret = Winsock2.WSAStartup(version, wsaData)
            println(ret)
            println(wsaData.wVersion)
            println(wsaData.wHighVersion)
            println(wsaData.szDescription)
            println(wsaData.szSystemStatus)

            println(wsaData)

            val socket = Winsock2.socket(
                Winsock2.AF_BTH,
                Winsock2.SOCK_STREAM,
                Winsock2.IPPROTO_RFCOMM
            )
            if (socket == Winsock2.INVALID_SOCKET) {
                println("Invalid SOCKET")
            }

            val guid = GUID.allocate(arena)
            guid.Data1 = 2
            guid.Data2 = 4
            guid.Data3 = 8
            guid.Data4 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

            val sockaddr = SOCKADDR_BTH.allocate(arena)
            sockaddr.addressFamily = Winsock2.AF_BTH.toShort()
            sockaddr.btAddr = 0xAFADADADADADAD
            sockaddr.port = 0
            sockaddr.serviceClassId = guid
            val a = sockaddr.serviceClassId.Data1
            println("guid Data1: $a")

            val connectRes = Winsock2.connect(socket, sockaddr.pointer, SOCKADDR_BTH.LAYOUT.byteSize().toInt())
            if (connectRes != 0) {
                val error = Winsock2.WSAGetLastError()
                println("connect error: $error")
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

            val ignoredError = Winsock2.WSAGetLastError()
            println(ignoredError)
        }
    }

}