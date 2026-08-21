package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class SupportTicketDto(
    @Json(name = "_id") val id: String? = "",
    @Json(name = "ticketId") val ticketId: String? = "",
    @Json(name = "category") val category: String = "OTHER",
    @Json(name = "subject") val subject: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "orderId") val orderId: String? = null,
    @Json(name = "status") val status: String = "OPEN",
    @Json(name = "priority") val priority: String = "MEDIUM",
    @Json(name = "attachments") val attachments: List<String> = emptyList(),
    @Json(name = "createdAt") val createdAt: String? = null
)

@JvmSuppressWildcards
interface SupportApi {
    @GET("support/tickets")
    suspend fun getTickets(): Response<List<SupportTicketDto>>

    @POST("support/tickets")
    suspend fun createTicket(@Body body: Map<String, Any>): Response<SupportTicketDto>

    @GET("support/tickets/{id}")
    suspend fun getTicketById(@Path("id") id: String): Response<SupportTicketDto>

    @GET("support/messages")
    suspend fun getMessages(): Response<List<Map<String, Any>>>

    @POST("support/messages")
    suspend fun sendMessage(@Body body: Map<String, String>): Response<Map<String, Any>>
}
