package com.example.data.api

import retrofit2.Response
import retrofit2.http.*

@JvmSuppressWildcards
interface StaffApi {
    // Worker / Delivery Partner management
    @POST("workers/verify-phone")
    suspend fun verifyWorkerPhone(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("workers/register")
    suspend fun registerDeliveryPartner(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @GET("workers/profile")
    suspend fun getWorkerProfile(): Response<Map<String, Any>>

    @GET("workers/stats")
    suspend fun getWorkerStats(): Response<Map<String, Any>>

    @PATCH("workers/availability")
    suspend fun updateWorkerAvailability(@Body body: Map<String, Boolean>): Response<Map<String, Any>>

    @POST("workers")
    suspend fun registerWorker(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("workers")
    suspend fun getWorkers(): Response<List<Map<String, Any>>>

    @PATCH("workers/{id}/status")
    suspend fun updateWorkerStatus(@Path("id") id: String, @Body body: Map<String, Boolean>): Response<Map<String, Any>>

    // Task management
    @POST("tasks")
    suspend fun assignTask(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("tasks/vendor")
    suspend fun getVendorTasks(): Response<List<Map<String, Any>>>

    @GET("tasks/worker")
    suspend fun getWorkerTasks(): Response<List<Map<String, Any>>>

    @PATCH("tasks/{id}/status")
    suspend fun updateTaskStatus(@Path("id") id: String, @Body body: Map<String, String>): Response<Map<String, Any>>
}

