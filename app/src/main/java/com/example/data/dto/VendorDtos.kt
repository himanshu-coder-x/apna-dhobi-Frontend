package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VendorDto(
    @Json(name = "_id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "rating") val rating: Double,
    @Json(name = "address") val address: String,
    @Json(name = "isOpen") val isOpen: Boolean,
    @Json(name = "bannerColor") val bannerColor: String?,
    @Json(name = "logoText") val logoText: String?
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "colorHex") val colorHex: String?,
    @Json(name = "iconName") val iconName: String?
)

@JsonClass(generateAdapter = true)
data class ServiceDto(
    @Json(name = "_id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "categoryId") val categoryId: String,
    @Json(name = "originalPrice") val originalPrice: Double,
    @Json(name = "discountPrice") val discountPrice: Double,
    @Json(name = "deliveryEstimate") val deliveryEstimate: String?,
    @Json(name = "popularBadge") val popularBadge: String?
)

@JsonClass(generateAdapter = true)
data class DeliveryPartner(
    @Json(name = "_id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "vehicleType") val vehicleType: String = "Bike 🏍️",
    @Json(name = "licenseNo") val licenseNo: String = "DL-982104921",
    @Json(name = "city") val city: String = "New Delhi",
    @Json(name = "status") val status: String = "Approved 🟢",
    @Json(name = "totalEarnings") val totalEarnings: Double = 1450.0,
    @Json(name = "rating") val rating: Double = 4.9
)
