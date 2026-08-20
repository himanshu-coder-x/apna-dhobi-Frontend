package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VendorDto(
    @Json(name = "_id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "rating") val rating: Double = 4.8,
    @Json(name = "address") val address: String = "",
    @Json(name = "isOpen") val isOpen: Boolean = true,
    @Json(name = "bannerColor") val bannerColor: String? = null,
    @Json(name = "logoText") val logoText: String? = null,
    @Json(name = "imageUrl") val imageUrl: String? = null,
    @Json(name = "categoryTag") val categoryTag: String? = null
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "colorHex") val colorHex: String? = null,
    @Json(name = "iconName") val iconName: String? = null,
    @Json(name = "imageUrl") val imageUrl: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "turnaroundTime") val turnaroundTime: String? = null,
    @Json(name = "startingPrice") val startingPrice: String? = null,
    @Json(name = "popularTag") val popularTag: String? = null,
    @Json(name = "displayOrder") val displayOrder: Int? = 0,
    @Json(name = "isActive") val isActive: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class ServiceDto(
    @Json(name = "_id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "categoryId") val categoryId: String = "",
    @Json(name = "originalPrice") val originalPrice: Double = 0.0,
    @Json(name = "discountPrice") val discountPrice: Double = 0.0,
    @Json(name = "deliveryEstimate") val deliveryEstimate: String? = null,
    @Json(name = "popularBadge") val popularBadge: String? = null,
    @Json(name = "imageUrl") val imageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class DeliveryPartner(
    @Json(name = "_id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "phone") val phone: String = "",
    @Json(name = "vehicleType") val vehicleType: String = "Bike 🏍️",
    @Json(name = "licenseNo") val licenseNo: String = "DL-982104921",
    @Json(name = "city") val city: String = "New Delhi",
    @Json(name = "status") val status: String = "Approved 🟢",
    @Json(name = "totalEarnings") val totalEarnings: Double = 1450.0,
    @Json(name = "rating") val rating: Double = 4.9
)
