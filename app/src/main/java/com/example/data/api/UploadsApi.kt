package com.example.data.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface UploadsApi {
    @Multipart
    @POST("uploads/file")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<Map<String, Any>>
}
