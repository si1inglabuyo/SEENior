package com.pup.seenior.network

import com.pup.seenior.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://seenior.onrender.com/"

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    // Render's free tier spins down when idle and a cold start takes ~40 s. OkHttp's 10 s
    // defaults cut that off and made a waking server look like an empty list, so these allow it to finish.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(DeviceKeyInterceptor())
        // BODY only in debug. It logs every request and response in full (names, addresses,
        // geohashes), which a release build must never carry. Credentials are redacted.
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                redactHeader(DeviceKeyInterceptor.HEADER)
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ApiService::class.java)
}
