package com.example.thesisproject

import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class Client(var hostAddress: InetAddress, var activity: MyWifiActivity): Thread() {
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    var tRequest : Long = 0 //time at the time of request to server
    var tRound : Long = 0 //round-trip time, time taken to send request and receive reply
    var tCristian : Long = 0 //Cristian's algorithm or corrected time
    var tReceive : Long = 0

    init {
        activity.socket = Socket()
    }

    fun write(msg : String){
        try {
            tCristian = 0
            tRound = 0
            tRequest = System.currentTimeMillis() //client sends time request to server
            outputStream.write(msg.toByteArray())
        }catch (e : IOException){
            e.printStackTrace()
        }
    }

    override fun run() {
        try {
            activity.socket!!.connect(InetSocketAddress(hostAddress, 8888), 500)
            inputStream = activity.socket!!.getInputStream()
            outputStream = activity.socket!!.getOutputStream()
        } catch (e : IOException){
            e.printStackTrace()
        }

        val executor = Executors.newSingleThreadExecutor() as ExecutorService
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            val buffer = ByteArray(1024)
            var bytes : Int

            while(activity.socket != null && activity.socket!!.isConnected) {
                try {
                    bytes = inputStream.read(buffer)
                    tReceive = System.currentTimeMillis()
                    tRound = tReceive - tRequest
                    if (bytes > 0) {
                        val tServer = String(buffer, 0, bytes).toLong()
                        tCristian =  tServer + tRound/2
                        val range = Marzullo.Range(tRequest-tServer, tReceive-tServer)
                        activity.rangeArray.add(range)
                        tRequest = 0L
                        //val finalBytes = bytes
                        handler.post {
                            activity.message.text = "Range start: ${range.start}\nRange end: ${range.end}\nOffset: ${(range.end + range.start.toDouble()) / 2}"
                        }
                    }
                }catch (e : SocketException){
                    e.printStackTrace()
                    break
                }
            }
        }
    }
}