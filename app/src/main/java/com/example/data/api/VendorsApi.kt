package com.example.data.api

import com.example.data.dto.VendorDto
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class RegisterVendorDto(
    val name: String,
    val description: String? = null,
    val address: String,
    val bannerColor: String? = null,
    val logoText: String? = null
)

@JvmSuppressWildcards
interface VendorsApi {
    @POST("vendors")
    suspend fun register(@Body body: Map<String, Any>): Response<VendorDto>

    @POST("vendors")
    suspend fun registerDto(@Body body: RegisterVendorDto): Response<VendorDto>

    @PATCH("vendors/{id}/toggle-status")
    suspend fun toggleStatus(@Path("id") id: String, @Body body: Map<String, Boolean>): Response<Map<String, Any>>

    @GET("vendors/{id}/stats")
    suspend fun getStats(@Path("id") id: String): Response<VendorStatsDto>
}

data class VendorStatsDto(
    val todayOrders: Int,
    val pendingOrders: Int,
    val activeOrders: Int,
    val completedOrders: Int,
    val revenue: Double,
    val commission: Double,
    val netEarnings: Double,
    val rating: Double
)
