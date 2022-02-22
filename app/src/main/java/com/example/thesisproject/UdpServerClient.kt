package com.example.thesisproject

import android.os.Looper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpServerClient (private val activity : MyWifiActivity) : Thread() {

    companion object {
        var tRequest : Long = 0
        var tRound : Long = 0
        var tCristian : Long = 0
        var tReceive : Long = 0

        fun clientSendReceive(ipAddress: InetAddress, message: String): Marzullo.Range {
            val socket = DatagramSocket()
            val buffer = message.toByteArray()

            val request = DatagramPacket(buffer, buffer.size, ipAddress, 5000)
            tRequest = System.currentTimeMillis() //time just before sending server request
            socket.send(request)

            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            tReceive = System.currentTimeMillis()
            tRound = tReceive - tRequest

            val serverResponse = if(responseBuffer.isNotEmpty()) String(responseBuffer, 0, responsePacket.length)
            else "Not yet received"
            //tCristian = if(responseBuffer.isNotEmpty()) serverResponse.toLong() + tRound/2 else 0
            val range = Marzullo.Range(tRequest-serverResponse.toLong(), tReceive - serverResponse.toLong())
            tRequest = 0

            return range

            //t1-t2  t3-t2

        }
    }


    override fun run() {

        val socket = DatagramSocket(5000)

        while (true) {
            val buffer = ByteArray(512)

            val request = DatagramPacket(buffer, buffer.size)
            socket.receive(request)

            if(buffer.isNotEmpty()) {
                val handler = android.os.Handler(Looper.getMainLooper())
                handler.post { activity.message.text = String(buffer, 0, request.length) }
                val serverTime = System.currentTimeMillis().toString().toByteArray()
                val response =
                    DatagramPacket(serverTime, serverTime.size, request.address, request.port)
                socket.send(response)
            }
        }
    }
}