package com.pup.seenior.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressFormPookTest {

    @Test
    fun `a stored pook is read back only when the address reads back to a barangay`() {
        val form = AddressForm()
        // No dataset is loaded in a unit test, so the address cannot be matched and the pook goes.
        form.fillFrom("1 Street, U.P. Campus, Quezon City, NCR, NCR", "U.P. Campus", 7, "Aguinaldo")
        assertNull(form.pook)
        assertFalse(form.showsPook)
    }

    @Test
    fun `picking the barangay clears any pook and is not what makes the form dirty by itself`() {
        val form = AddressForm()
        form.onBarangaySelected("U.P. CAMPUS")
        assertTrue(form.showsPook)
        assertNull(form.pook)
    }

    @Test
    fun `only U P Campus offers pooks, whatever the spelling or case`() {
        val form = AddressForm()
        listOf("U.P. CAMPUS", "U.P. Campus", "UP Campus").forEach {
            form.onBarangaySelected(it)
            assertTrue(it, form.showsPook)
        }
        form.onBarangaySelected("Holy Spirit")
        assertFalse(form.showsPook)
        assertEquals(emptyList<Any>(), form.pookOptions)
    }
}
