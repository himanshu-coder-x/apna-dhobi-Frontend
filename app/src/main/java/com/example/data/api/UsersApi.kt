package com.example.data.api

import com.example.data.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface UsersApi {
    @GET("users/profile")
    suspend fun getProfile(): Response<UserDto>

    @PATCH("users/profile")
    suspend fun updateProfile(@Body body: Map<String, Any>): Response<UserDto>
}
