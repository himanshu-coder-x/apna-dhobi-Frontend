package com.example.data.api

import retrofit2.Response
import retrofit2.http.*

@JvmSuppressWildcards
interface WalletApi {
    @GET("wallets/balance")
    suspend fun getBalance(): Response<Map<String, Any>>

    @GET("wallets/transactions")
    suspend fun getTransactions(): Response<List<Map<String, Any>>>

    @POST("wallets/razorpay/create-order")
    suspend fun createRazorpayOrder(@Body data: Map<String, Any>): Response<Map<String, Any>>

    @POST("wallets/razorpay/verify-payment")
    suspend fun verifyPayment(@Body data: Map<String, Any>): Response<Map<String, Any>>

    @POST("wallets/recharge-request")
    suspend fun createRechargeRequest(@Body data: Map<String, Any>): Response<Map<String, Any>>

    @GET("wallets/recharge-requests")
    suspend fun getAllRechargeRequests(): Response<List<Map<String, Any>>>

    @PATCH("wallets/recharge-requests/{id}/approve")
    suspend fun approveRechargeRequest(@Path("id") id: String): Response<Map<String, Any>>

    @PATCH("wallets/recharge-requests/{id}/reject")
    suspend fun rejectRechargeRequest(@Path("id") id: String): Response<Map<String, Any>>
}
