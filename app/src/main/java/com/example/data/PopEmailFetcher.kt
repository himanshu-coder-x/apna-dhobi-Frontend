package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

object PopEmailFetcher {
    private const val TAG = "PopEmailFetcher"

    suspend fun testPopConnection(
        popServer: String,
        popPort: Int,
        user: String,
        pass: String
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to POP server $popServer:$popPort")
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val socket: Socket = factory.createSocket(popServer, popPort)
            socket.soTimeout = 8000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)

            fun readResponse(): String {
                val line = reader.readLine() ?: throw Exception("No response from POP server")
                Log.d(TAG, "POP Server: $line")
                if (!line.startsWith("+OK")) {
                    throw Exception("Unexpected POP response. Got: $line")
                }
                return line
            }

            // Read welcome
            readResponse()

            // Send USER
            writer.print("USER $user\r\n")
            writer.flush()
            readResponse()

            // Send PASS
            val passClean = pass.replace(" ", "").trim()
            writer.print("PASS $passClean\r\n")
            writer.flush()
            readResponse()

            // Send STAT to check emails
            writer.print("STAT\r\n")
            writer.flush()
            val statLine = readResponse() // +OK <msgCount> <octetsCount>
            
            // Send QUIT
            writer.print("QUIT\r\n")
            writer.flush()
            socket.close()

            "Successfully connected to POP account! Stat response: $statLine"
        } catch (e: Exception) {
            Log.e(TAG, "Error testing POP configuration", e)
            "POP Connection failed: ${e.message}"
        }
    }
}
