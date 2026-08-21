package com.example.data.network

import com.example.data.api.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // 10.0.2.2 connects directly to host PC localhost:3000 from Android Emulator
    const val LOCAL_EMULATOR_URL = "http://10.0.2.2:3000/api/v1/"
    const val REMOTE_PROD_URL = "https://apna-dhobi-backend.onrender.com/api/v1/"
    
    var BASE_URL: String = REMOTE_PROD_URL
    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            authToken?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(requestBuilder.build())
        }
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val catalogApi: CatalogApi = retrofit.create(CatalogApi::class.java)
    val ordersApi: OrdersApi = retrofit.create(OrdersApi::class.java)
    val walletApi: WalletApi = retrofit.create(WalletApi::class.java)
    val vendorsApi: VendorsApi = retrofit.create(VendorsApi::class.java)
    val staffApi: StaffApi = retrofit.create(StaffApi::class.java)
    val uploadsApi: UploadsApi = retrofit.create(UploadsApi::class.java)
    val usersApi: UsersApi = retrofit.create(UsersApi::class.java)
    val addressesApi: AddressesApi = retrofit.create(AddressesApi::class.java)
    val supportApi: SupportApi = retrofit.create(SupportApi::class.java)
    val couponsApi: CouponsApi = retrofit.create(CouponsApi::class.java)
}
