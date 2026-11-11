package com.example.data.remote

import com.example.data.security.SecureKeystoreManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ApiClientProvider(
    private val keystoreManager: SecureKeystoreManager
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(SecurityInterceptor(keystoreManager))
            .build()
    }

    private var currentBaseUrl: String? = null
    private var currentService: RelayApiService? = null

    @Synchronized
    fun getApiService(overrideBaseUrl: String? = null): RelayApiService {
        val targetBaseUrl = overrideBaseUrl ?: keystoreManager.baseUrl
        val normalized = if (targetBaseUrl.endsWith("/")) targetBaseUrl else "$targetBaseUrl/"

        if (currentService != null && currentBaseUrl == normalized) {
            return currentService!!
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(RelayApiService::class.java)
        currentBaseUrl = normalized
        currentService = service
        return service
    }

    fun moshi(): Moshi = moshi
}
