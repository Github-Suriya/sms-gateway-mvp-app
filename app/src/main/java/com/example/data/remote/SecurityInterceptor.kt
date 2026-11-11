package com.example.data.remote

import com.example.BuildConfig
import com.example.data.security.SecureKeystoreManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class SecurityInterceptor(
    private val keystoreManager: SecureKeystoreManager
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // 1. Enforce HTTPS outside debug builds
        if (!originalUrl.isHttps && !BuildConfig.DEBUG) {
            throw IOException("HTTPS is strictly required in production: ${originalUrl.host}")
        }

        // 2. Attach Authorization header if authenticated and not pairing endpoint
        val path = originalUrl.encodedPath
        val isPairEndpoint = path.endsWith("devices/pair")

        val token = keystoreManager.deviceToken
        val requestBuilder = originalRequest.newBuilder()

        if (!isPairEndpoint && !token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        // 3. Prevent cross-origin redirect token leakage
        val initialRequest = requestBuilder.build()
        val response = chain.proceed(initialRequest)

        if (response.isRedirect) {
            val location = response.header("Location")
            if (location != null) {
                val redirectedUrl = originalUrl.resolve(location)
                if (redirectedUrl != null && !isSameOrigin(originalUrl, redirectedUrl)) {
                    // Cross-origin redirect detected: strip Authorization bearer token!
                    val sanitizedRedirectRequest = initialRequest.newBuilder()
                        .url(redirectedUrl)
                        .removeHeader("Authorization")
                        .build()
                    response.close()
                    return chain.proceed(sanitizedRedirectRequest)
                }
            }
        }

        return response
    }

    private fun isSameOrigin(url1: okhttp3.HttpUrl, url2: okhttp3.HttpUrl): Boolean {
        return url1.scheme == url2.scheme &&
                url1.host == url2.host &&
                url1.port == url2.port
    }
}
