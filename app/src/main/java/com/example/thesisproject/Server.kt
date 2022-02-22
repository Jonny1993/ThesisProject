package com.example.thesisproject

import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Server(val activity: MyWifiActivity) : Thread(){
    lateinit var serverSocket : ServerSocket
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    var tLatest : Long = 0L //current time at the last possible point before transmission to client

    fun write(msg : String){
        try {
            tLatest = System.currentTimeMillis()
            outputStream.write(tLatest.toString().toByteArray())
            tLatest = 0L
        }catch (e : IOException){
            e.printStackTrace()
        }
    }

    override fun run() {
        try {
            serverSocket = ServerSocket(8888)
            activity.socket = serverSocket.accept()
            inputStream = activity.socket!!.getInputStream()
            outputStream = activity.socket!!.getOutputStream()
        } catch (e : IOException){
            e.printStackTrace()
        }

        val executor = Executors.newSingleThreadExecutor() as ExecutorService
        val handler = Handler(Looper.getMainLooper())

        executor.execute{
            val buffer = ByteArray(1024)
            var bytes : Int
            while(activity.socket != null && activity.socket!!.isConnected){
                try {
                    bytes = inputStream.read(buffer)
                    write("")
                    if(bytes > 0){
                        val finalBytes = bytes
                        handler.post {
                            activity.message.text = String(buffer, 0, finalBytes)
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