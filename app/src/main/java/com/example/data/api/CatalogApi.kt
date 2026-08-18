package com.example.data.api

import com.example.data.dto.*
import retrofit2.Response
import retrofit2.http.*

interface CatalogApi {
    @GET("categories")
    suspend fun getCategories(): Response<List<CategoryDto>>

    @GET("vendors")
    suspend fun getVendors(): Response<List<VendorDto>>

    @GET("services")
    suspend fun getServices(
        @Query("categoryId") categoryId: String? = null,
        @Query("vendorId") vendorId: String? = null
    ): Response<List<ServiceDto>>

    @POST("services")
    suspend fun createService(@Body service: Map<String, Any>): Response<ServiceDto>

    @PATCH("services/{id}")
    suspend fun updateService(@Path("id") id: String, @Body service: Map<String, Any>): Response<ServiceDto>

    @DELETE("services/{id}")
    suspend fun deleteService(@Path("id") id: String): Response<Unit>

    @GET("banners")
    suspend fun getBanners(): Response<List<BannerDto>>

    @POST("banners")
    suspend fun createBanner(@Body banner: Map<String, Any>): Response<BannerDto>

    @DELETE("banners/{id}")
    suspend fun deleteBanner(@Path("id") id: String): Response<Unit>
}
