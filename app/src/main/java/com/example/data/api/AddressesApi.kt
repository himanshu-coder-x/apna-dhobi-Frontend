package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class AddressDto(
    @Json(name = "_id") val id: String? = "",
    @Json(name = "name") val name: String? = "",
    @Json(name = "phone") val phone: String? = "",
    @Json(name = "flatBuilding") val flatBuilding: String? = "",
    @Json(name = "streetArea") val streetArea: String? = "",
    @Json(name = "landmark") val landmark: String? = "",
    @Json(name = "city") val city: String? = "",
    @Json(name = "pincode") val pincode: String? = "",
    @Json(name = "type") val type: String? = "Home",
    @Json(name = "isDefault") val isDefault: Boolean? = false
)

@JvmSuppressWildcards
interface AddressesApi {
    @GET("addresses")
    suspend fun getAddresses(): Response<List<AddressDto>>

    @POST("addresses")
    suspend fun createAddress(@Body body: Map<String, Any>): Response<AddressDto>

    @PUT("addresses/{id}")
    suspend fun updateAddress(@Path("id") id: String, @Body body: Map<String, Any>): Response<AddressDto>

    @PATCH("addresses/{id}/default")
    suspend fun setDefaultAddress(@Path("id") id: String): Response<AddressDto>

    @DELETE("addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: String): Response<Map<String, Any>>
}
