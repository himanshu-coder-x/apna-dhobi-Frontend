package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class CouponDto(
    @Json(name = "_id") val id: String? = "",
    @Json(name = "code") val code: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "discountType") val discountType: String = "percentage",
    @Json(name = "discountValue") val discountValue: Double = 0.0,
    @Json(name = "minOrderAmount") val minOrderAmount: Double = 0.0,
    @Json(name = "maxDiscount") val maxDiscount: Double = 0.0,
    @Json(name = "isActive") val isActive: Boolean = true,
    @Json(name = "isValid") val isValid: Boolean? = true,
    @Json(name = "error") val error: String? = null
)

interface CouponsApi {
    @GET("coupons")
    suspend fun getCoupons(): Response<List<CouponDto>>

    @GET("coupons/{code}")
    suspend fun verifyCoupon(@Path("code") code: String): Response<CouponDto>
}
