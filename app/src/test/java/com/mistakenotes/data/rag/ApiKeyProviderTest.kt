package com.mistakenotes.data.rag

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ApiKeyProviderTest {

    @Test
    fun `hasKey returns false when no key stored`() = runTest {
        val provider = ApiKeyProvider(ApplicationProvider.getApplicationContext())
        assertFalse(provider.hasKey())
    }

    @Test
    fun `setKey and hasKey round-trip`() = runTest {
        val provider = ApiKeyProvider(ApplicationProvider.getApplicationContext())
        provider.setKey("sk-test-12345")
        assertTrue(provider.hasKey())
        assertEquals("sk-test-12345", provider.get())
    }

    @Test
    fun `clearKey makes hasKey return false`() = runTest {
        val provider = ApiKeyProvider(ApplicationProvider.getApplicationContext())
        provider.setKey("sk-test-12345")
        provider.clearKey()
        assertFalse(provider.hasKey())
    }
}
