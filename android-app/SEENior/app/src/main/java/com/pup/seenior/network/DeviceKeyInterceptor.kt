package com.pup.seenior.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Adds the senior's device key to requests the senior's phone makes in the senior's name. */
class DeviceKeyInterceptor(private val keyProvider: () -> String? = DeviceKeyStore::current) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(stamp(chain.request(), keyProvider()))

    companion object {
        const val HEADER = "X-Device-Key"

        /**
         * Returns [request] with the key added. Requests that already carry a login (the family
         * app's) are left alone, as are requests when this phone has no key yet.
         */
        fun stamp(request: Request, key: String?): Request {
            if (key == null || request.header("Authorization") != null) return request
            return request.newBuilder().header(HEADER, key).build()
        }
    }
}
