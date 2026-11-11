package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.SecureKeystoreManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityAndOriginTest {

    private lateinit var keystoreManager: SecureKeystoreManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        keystoreManager = SecureKeystoreManager(context)
    }

    @Test
    fun testInstallationIdIsPersistedUuid() {
        val id1 = keystoreManager.installationId
        val id2 = keystoreManager.installationId
        assertEquals(id1, id2)
        assertTrue(id1.isNotEmpty())
        // Verify it matches UUID format
        val parsed = java.util.UUID.fromString(id1)
        assertEquals(id1, parsed.toString())
    }

    @Test
    fun testUrlOriginValidation() {
        val validHttps = keystoreManager.validateUrl("https://gateway.example.com/api/v1")
        assertTrue(validHttps is SecureKeystoreManager.UrlValidationResult.Valid)
        val valid = validHttps as SecureKeystoreManager.UrlValidationResult.Valid
        assertEquals("https://gateway.example.com", valid.origin)

        val invalidScheme = keystoreManager.validateUrl("ftp://gateway.example.com")
        assertTrue(invalidScheme is SecureKeystoreManager.UrlValidationResult.Invalid)

        val emptyUrl = keystoreManager.validateUrl("")
        assertTrue(emptyUrl is SecureKeystoreManager.UrlValidationResult.Invalid)
    }
}
