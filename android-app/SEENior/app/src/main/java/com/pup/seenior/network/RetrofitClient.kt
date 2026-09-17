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

    // Render's free tier spins the service down after ~15 min idle; the next request has to
    // wait out a cold start measured at ~40s. OkHttp's 10s defaults cut that off, which made a
    // waking server look like "your data is gone" (an empty list) on the first call after the
    // app had been closed for a while. These give the wake-up room to finish.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // BODY only in a debug build. This interceptor writes every request and response in
        // full to logcat, which on this API means bearer tokens, the password posted to
        // /auth/login, senior names and addresses, and an alert's precise geohash -- exactly
        // the material CLAUDE.md §11 keeps off the wire and out of logs. A debug build is
        // already readable over adb by anyone holding the handset, so it changes nothing
        // there; a release build must never carry it.
        .addInterceptor(
            HttpLoggingInterceptor().apply {
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
