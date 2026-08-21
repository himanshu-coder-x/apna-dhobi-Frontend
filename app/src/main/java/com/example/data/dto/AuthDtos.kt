package com.example.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    @Json(name = "phone") val phone: String
)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(
    @Json(name = "message") val message: String? = "OTP sent successfully",
    @Json(name = "phone") val phone: String? = "",
    @Json(name = "otp") val otp: String? = null,
    @Json(name = "isRegistered") val isRegistered: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    @Json(name = "phone") val phone: String,
    @Json(name = "otp") val otp: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "phone") val phone: String,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "referralCode") val referralCode: String?
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "accessToken") val accessToken: String?,
    @Json(name = "refreshToken") val refreshToken: String?,
    @Json(name = "user") val user: UserDto?,
    @Json(name = "isVerified") val isVerified: Boolean? = false,
    @Json(name = "needsRegistration") val needsRegistration: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: String? = "",
    @Json(name = "_id") val _id: String? = "",
    @Json(name = "phone") val phone: String = "",
    @Json(name = "name") val name: String? = "",
    @Json(name = "email") val email: String? = "",
    @Json(name = "roles") val roles: List<String> = emptyList(),
    @Json(name = "profilePhoto") val profilePhoto: String? = null,
    @Json(name = "gender") val gender: String? = null,
    @Json(name = "dob") val dob: String? = null
)
