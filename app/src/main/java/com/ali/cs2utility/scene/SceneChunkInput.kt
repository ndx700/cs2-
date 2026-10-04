package com.ali.cs2utility.scene

import java.io.DataInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

/** Accept both source gzip assets and Android-packaged, expanded D2M1 assets. */
internal object SceneChunkInput {
    fun open(path: String, openAsset: (String) -> InputStream): DataInputStream {
        val raw = try {
            openAsset(path)
        } catch (missing: FileNotFoundException) {
            if (!path.endsWith(".gz")) throw missing
            openAsset(path.removeSuffix(".gz"))
        }
        val probe = PushbackInputStream(raw.buffered(65536), 2)
        try {
            val first = probe.read()
            val second = probe.read()
            if (second >= 0) probe.unread(second)
            if (first >= 0) probe.unread(first)
            val decoded = if (first == 0x1f && second == 0x8b) {
                GZIPInputStream(probe, 65536)
            } else {
                probe
            }
            return DataInputStream(decoded.buffered(65536))
        } catch (failure: Throwable) {
            try { probe.close() } catch (closing: Throwable) { failure.addSuppressed(closing) }
            throw failure
        }
    }
}
