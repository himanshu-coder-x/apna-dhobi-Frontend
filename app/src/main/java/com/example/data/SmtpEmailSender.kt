package com.example.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

object SmtpEmailSender {
    private const val TAG = "SmtpEmailSender"

    suspend fun sendEmail(
        toEmail: String,
        subject: String,
        body: String,
        smtpServer: String = "smtp.gmail.com",
        smtpPort: Int = 465,
        smtpUser: String = "anil.satyaka@gmail.com",
        smtpPass: String = "taqk iwdr zmqy ppyd"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting socket connection to $smtpServer:$smtpPort")
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val socket: Socket = factory.createSocket(smtpServer, smtpPort)
            socket.soTimeout = 10000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)

            fun readResponse(expectedCode: String): String {
                val line = reader.readLine() ?: throw Exception("No response from server")
                Log.d(TAG, "SMTP Server response: $line")
                if (!line.startsWith(expectedCode)) {
                    throw Exception("Unexpected SMTP response code. Got: $line, expected: $expectedCode")
                }
                return line
            }

            // Read welcome greeting
            readResponse("220")

            // Send EHLO
            writer.print("EHLO localhost\r\n")
            writer.flush()
            
            // Server will output multiple 250 responses, read them until no - at 3rd index
            while (true) {
                val line = reader.readLine() ?: break
                Log.d(TAG, "EHLO response: $line")
                if (line.startsWith("250 ")) {
                    break
                }
            }

            // Send AUTH LOGIN
            writer.print("AUTH LOGIN\r\n")
            writer.flush()
            readResponse("334")

            // Send Base64 Username
            val userB64 = Base64.encodeToString(smtpUser.toByteArray(), Base64.NO_WRAP)
            writer.print("$userB64\r\n")
            writer.flush()
            readResponse("334")

            // Send Base64 Password
            val passClean = smtpPass.replace(" ", "").trim()
            val passB64 = Base64.encodeToString(passClean.toByteArray(), Base64.NO_WRAP)
            writer.print("$passB64\r\n")
            writer.flush()
            readResponse("235") // 235 Authentication successful

            // MAIL FROM
            writer.print("MAIL FROM:<$smtpUser>\r\n")
            writer.flush()
            readResponse("250")

            // RCPT TO
            writer.print("RCPT TO:<$toEmail>\r\n")
            writer.flush()
            readResponse("250")

            // DATA
            writer.print("DATA\r\n")
            writer.flush()
            readResponse("354")

            // Send Mail Content
            writer.print("From: Apna Dhobi <$smtpUser>\r\n")
            writer.print("To: <$toEmail>\r\n")
            writer.print("Subject: $subject\r\n")
            writer.print("Content-Type: text/html; charset=utf-8\r\n\r\n")
            writer.print("$body\r\n")
            writer.print(".\r\n")
            writer.flush()
            readResponse("250")

            // QUIT
            writer.print("QUIT\r\n")
            writer.flush()
            
            socket.close()
            Log.d(TAG, "Email sent successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMTP email", e)
            false
        }
    }
}
