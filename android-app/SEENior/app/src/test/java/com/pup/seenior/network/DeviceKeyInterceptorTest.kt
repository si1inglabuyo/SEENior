package com.pup.seenior.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The senior's device key must go on the senior's own requests and nowhere else. */
class DeviceKeyInterceptorTest {

    private fun request(authorization: String? = null): Request =
        Request.Builder().url("https://seenior.onrender.com/seniors/abc/heartbeat").apply {
            authorization?.let { header("Authorization", it) }
        }.build()

    @Test
    fun addsTheKeyToASeniorRequest() {
        val stamped = DeviceKeyInterceptor.stamp(request(), "secret-key")
        assertEquals("secret-key", stamped.header(DeviceKeyInterceptor.HEADER))
    }

    @Test
    fun leavesAFamilyRequestAlone() {
        val original = request(authorization = "Bearer token")
        val stamped = DeviceKeyInterceptor.stamp(original, "secret-key")
        assertSame(original, stamped)
        assertNull(stamped.header(DeviceKeyInterceptor.HEADER))
    }

    @Test
    fun sendsNothingWhenThePhoneHasNoKeyYet() {
        val original = request()
        assertSame(original, DeviceKeyInterceptor.stamp(original, null))
    }
}
