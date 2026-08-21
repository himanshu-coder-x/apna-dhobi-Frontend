package com.example.data.api

import com.example.data.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("auth/check-registration/{phone}")
    suspend fun checkRegistration(@Path("phone") phone: String): Response<Map<String, Boolean>>

    @GET("auth/validate-referral/{code}")
    suspend fun validateReferral(@Path("code") code: String): Response<Map<String, Boolean>>

    @POST("auth/google")
    suspend fun googleLogin(@Body request: Map<String, String>): Response<AuthResponse>

    @GET("users/role/delivery-agents")
    suspend fun getDeliveryAgents(): Response<List<UserDto>>
}
