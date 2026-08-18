package com.example.data.api

import com.example.data.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

interface OrdersApi {
    @POST("orders")
    suspend fun createOrder(@Body request: CreateOrderRequest): Response<OrderDto>

    @GET("orders")
    suspend fun getOrders(): Response<List<OrderDto>>

    @GET("orders/vendor/{vendorId}")
    suspend fun getVendorOrders(@Path("vendorId") vendorId: String): Response<List<OrderDto>>

    @PATCH("orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: String, @Body body: Map<String, String>): Response<OrderDto>

    @PATCH("orders/{id}/details")
    suspend fun updateOrderDetails(@Path("id") id: String, @Body details: Map<String, Any>): Response<OrderDto>

    @GET("orders/qr/{qrCode}")
    suspend fun findByQr(@Path("qrCode") qrCode: String): Response<OrderDto>
}

data class CreateOrderRequest(
    val vendorId: String,
    val itemsSummary: String,
    val totalPrice: Double,
    val pickupSlot: String,
    val deliverySlot: String,
    val paymentMethod: String,
    val useWallet: Boolean
)

data class OrderDto(
    val id: String,
    val itemsSummary: String,
    val totalPrice: Double,
    val pickupSlot: String,
    val deliverySlot: String,
    val status: String,
    val paymentMethod: String
)
