package com.ali.cs2utility

import com.ali.cs2utility.scene.SceneChunkInput
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.GZIPOutputStream

class SceneChunkInputTest {
    private val payload = byteArrayOf(0x44, 0x32, 0x4d, 0x31, 0, 0, 0, 3)
    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).use { it.write(bytes) }
    }.toByteArray()

    @Test fun originalCompressedAssetIsDecoded() {
        val paths = mutableListOf<String>()
        SceneChunkInput.open("chunks/a.d2m.gz") { paths += it; ByteArrayInputStream(gzip(payload)) }
            .use { assertArrayEquals(payload, it.readBytes()) }
        assertEquals(listOf("chunks/a.d2m.gz"), paths)
    }

    @Test fun missingGzipPathUsesPackagedRawAsset() {
        val paths = mutableListOf<String>()
        SceneChunkInput.open("chunks/a.d2m.gz") {
            paths += it
            if (it.endsWith(".gz")) throw FileNotFoundException(it)
            ByteArrayInputStream(payload)
        }.use { assertArrayEquals(payload, it.readBytes()) }
        assertEquals(listOf("chunks/a.d2m.gz", "chunks/a.d2m"), paths)
    }

    @Test fun compressionIsDetectedFromBytesRatherThanSuffix() {
        SceneChunkInput.open("chunks/a.d2m") { ByteArrayInputStream(gzip(payload)) }
            .use { assertEquals(0x44324d31, it.readInt()) }
        SceneChunkInput.open("chunks/a.d2m.gz") { ByteArrayInputStream(payload) }
            .use { assertEquals(0x44324d31, it.readInt()) }
    }

    @Test fun nonMissingIoFailureDoesNotTryAnotherPath() {
        var calls = 0
        val failure = IOException("read failure")
        try {
            SceneChunkInput.open("chunks/a.d2m.gz") { calls++; throw failure }
            fail("Expected original read failure")
        } catch (e: IOException) { assertSame(failure, e) }
        assertEquals(1, calls)
    }

    @Test fun missingUncompressedPathIsNotRewritten() {
        var calls = 0
        try {
            SceneChunkInput.open("chunks/a.d2m") { calls++; throw FileNotFoundException(it) }
            fail("Expected missing asset")
        } catch (_: FileNotFoundException) { }
        assertEquals(1, calls)
    }

    @Test fun malformedGzipClosesTheUnderlyingStream() {
        var closed = false
        val raw = object : ByteArrayInputStream(byteArrayOf(0x1f, 0x8b.toByte(), 0)) {
            override fun close() { closed = true; super.close() }
        }
        try {
            SceneChunkInput.open("chunks/a.d2m.gz") { raw }
            fail("Expected invalid gzip header")
        } catch (_: IOException) { }
        assertTrue(closed)
    }
}
