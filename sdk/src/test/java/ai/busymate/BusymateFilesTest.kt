package ai.busymate.whitelabel

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusymateFilesTest {
    private val ext = mapOf("pdf" to "application/pdf", "png" to "image/png")

    @Test fun chatAcceptBecomesPickerTypes() {
        assertArrayEquals(arrayOf("image/*", "application/pdf"),
            BusymateFiles.mimeTypes(arrayOf("image/*,application/pdf"), ext::get))
        assertArrayEquals(arrayOf("application/pdf", "image/png"),
            BusymateFiles.mimeTypes(arrayOf(" .PDF ", ".png", ".unknown"), ext::get))
    }

    @Test fun emptyOrUnknownAcceptMeansAnyFile() {
        assertArrayEquals(arrayOf("*/*"), BusymateFiles.mimeTypes(null, ext::get))
        assertArrayEquals(arrayOf("*/*"), BusymateFiles.mimeTypes(arrayOf(""), ext::get))
        assertArrayEquals(arrayOf("*/*"), BusymateFiles.mimeTypes(arrayOf("image"), ext::get))
    }

    class Plain : WebChromeClient()
    class OwnChooser : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams) = true
    }
    class InheritsOwnChooser : OwnChooserBase()
    open class OwnChooserBase : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams) = false
    }

    @Test fun keepsAHostChooserAndWrapsOnlyHostsWithout() {
        assertFalse(BusymateFiles.overridesChooser(WebChromeClient::class.java))
        assertFalse(BusymateFiles.overridesChooser(Plain::class.java))
        assertTrue(BusymateFiles.overridesChooser(OwnChooser::class.java))
        assertTrue(BusymateFiles.overridesChooser(InheritsOwnChooser::class.java))
    }

    @Test fun capabilityIsExposedOnlyToBusymateAndExactHttpsOrigins() {
        assertEquals(setOf("https://busymate.ai", "https://*.busymate.ai", "https://support.acme.com"),
            BusymateFiles.rules(setOf("https://support.acme.com", "http://insecure.example", "https://*.evil.example")))
    }
}
