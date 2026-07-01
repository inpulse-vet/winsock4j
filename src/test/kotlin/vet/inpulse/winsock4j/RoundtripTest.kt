package vet.inpulse.winsock4j

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full client/server roundtrip over loopback TCP, exercising the whole binding surface:
 * WSAStartup -> socket -> bind -> listen -> accept (server) / connect (client) ->
 * send/recv echo -> shutdown -> closesocket -> WSACleanup.
 *
 * Like [TestWinsock.startup], this touches ws2_32 downcalls, so it only runs on Windows;
 * on the Linux dev/CI host it compiles but fails at runtime (WSAStartup has no ws2_32 to
 * bind against). AF_INET/loopback is used instead of AF_BTH so no Bluetooth hardware is
 * required — this is a self-contained integration test.
 */
class RoundtripTest {

    @Test
    fun roundtrip() {
        val port = 54_321
        val payload = "hello, winsock".toByteArray()

        Arena.ofConfined().use { arena ->
            val wsaData = WSADATA.allocate(arena)
            assertEquals(0, Winsock2.WSAStartup(0x0202.toUShort(), wsaData), "WSAStartup failed")

            // --- server socket: bind + listen (before the client can connect) ---
            val serverSocket = Winsock2.socket(Winsock2.AF_INET, Winsock2.SOCK_STREAM, Winsock2.IPPROTO_TCP)
            assertTrue(serverSocket != Winsock2.INVALID_SOCKET, "server socket invalid: ${Winsock2.WSAGetLastError()}")

            val serverAddr = SOCKADDR_IN.allocate(arena)
            serverAddr.sinFamily = Winsock2.AF_INET.toShort()
            serverAddr.sinPort = port
            serverAddr.sinAddr = Winsock2.ipv4AddrFromString("127.0.0.1")

            val addrSize = SOCKADDR_IN.LAYOUT.byteSize().toInt()
            assertEquals(0, Winsock2.bind(serverSocket, serverAddr.pointer, addrSize), "bind failed: ${Winsock2.WSAGetLastError()}")
            assertEquals(0, Winsock2.listen(serverSocket, Winsock2.SOMAXCONN), "listen failed: ${Winsock2.WSAGetLastError()}")

            // Server runs on its own thread because accept/recv block: accept a peer,
            // read its bytes, echo them straight back, then tear the peer socket down.
            val serverError = arrayOfNulls<Throwable>(1)
            val serverThread = thread(name = "echo-server") {
                Arena.ofConfined().use { serverArena ->
                    try {
                        val peerAddr = SOCKADDR_IN.allocate(serverArena)
                        val addrLen = serverArena.allocate(ValueLayout.JAVA_INT)
                        addrLen.set(ValueLayout.JAVA_INT, 0L, addrSize)

                        val peer = Winsock2.accept(serverSocket, peerAddr.pointer, addrLen)
                        assertTrue(peer != Winsock2.INVALID_SOCKET, "accept failed: ${Winsock2.WSAGetLastError()}")

                        val buf = serverArena.allocate(512)
                        val received = Winsock2.recv(peer, buf, 512, 0)
                        assertTrue(received > 0, "server recv failed: ${Winsock2.WSAGetLastError()}")

                        assertEquals(received, Winsock2.send(peer, buf, received, 0), "server echo send failed: ${Winsock2.WSAGetLastError()}")

                        Winsock2.shutdown(peer, Winsock2.SD_BOTH)
                        Winsock2.closesocket(peer)
                    } catch (t: Throwable) {
                        serverError[0] = t
                    }
                }
            }

            // --- client: connect, send the payload, recv the echo ---
            val clientSocket = Winsock2.socket(Winsock2.AF_INET, Winsock2.SOCK_STREAM, Winsock2.IPPROTO_TCP)
            assertTrue(clientSocket != Winsock2.INVALID_SOCKET, "client socket invalid: ${Winsock2.WSAGetLastError()}")

            assertEquals(0, Winsock2.connect(clientSocket, serverAddr.pointer, addrSize), "connect failed: ${Winsock2.WSAGetLastError()}")

            val sendBuf = arena.allocate(payload.size.toLong())
            MemorySegment.copy(payload, 0, sendBuf, ValueLayout.JAVA_BYTE, 0L, payload.size)
            assertEquals(payload.size, Winsock2.send(clientSocket, sendBuf, payload.size, 0), "client send failed: ${Winsock2.WSAGetLastError()}")

            val recvBuf = arena.allocate(512)
            val echoedLen = Winsock2.recv(clientSocket, recvBuf, 512, 0)
            assertEquals(payload.size, echoedLen, "client recv failed: ${Winsock2.WSAGetLastError()}")

            val echoed = ByteArray(echoedLen)
            MemorySegment.copy(recvBuf, ValueLayout.JAVA_BYTE, 0L, echoed, 0, echoedLen)
            assertEquals(payload.toList(), echoed.toList(), "echoed payload mismatch")

            // --- teardown ---
            Winsock2.shutdown(clientSocket, Winsock2.SD_BOTH)
            Winsock2.closesocket(clientSocket)

            serverThread.join(5_000)
            serverError[0]?.let { throw it }

            Winsock2.closesocket(serverSocket)
            Winsock2.WSACleanup()
        }
    }
}
