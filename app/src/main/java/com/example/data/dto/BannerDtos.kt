package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BannerDto(
    @Json(name = "_id") val id: String,
    @Json(name = "title") val title: String? = "",
    @Json(name = "brandName") val brandName: String? = "",
    @Json(name = "imageUrl") val imageUrl: String? = "",
    @Json(name = "mediaUrl") val mediaUrl: String? = "",
    @Json(name = "mediaType") val mediaType: String? = "image",
    @Json(name = "redirectUrl") val redirectUrl: String? = "",
    @Json(name = "subtitle") val subtitle: String? = "",
    @Json(name = "code") val code: String? = "",
    @Json(name = "colors") val colors: List<String>? = emptyList(),
    @Json(name = "badge") val badge: String? = "",
    @Json(name = "isActive") val isActive: Boolean = true
)
