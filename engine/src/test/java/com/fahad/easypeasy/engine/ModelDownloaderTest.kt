package com.fahad.easypeasy.engine

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "verified offline model".toByteArray()
    private suspend fun spec(): ModelSpec {
        val file = folder.newFile().apply { writeBytes(bytes) }
        return ModelSpec("test-model", "Test", "https://example.invalid/model", ModelDownloader.sha256(file), bytes.size.toLong(), "test")
    }
    @Test fun resumesExistingPartialAndVerifiesHash() = runTest {
        val model = spec()
        val dir = folder.newFolder()
        File(dir, "test-model.part").writeBytes(bytes.take(5).toByteArray())
        val downloader = ModelDownloader { _, offset ->
            assertEquals(5L, offset)
            ModelResponse(206, "bytes 5-${bytes.size - 1}/${bytes.size}", ByteArrayInputStream(bytes.drop(5).toByteArray()))
        }
        assertArrayEquals(bytes, downloader.download(model, dir, { Long.MAX_VALUE }).readBytes())
        assertFalse(File(dir, "test-model.part").exists())
    }
    @Test fun restartsWhenServerIgnoresRange() = runTest {
        val model = spec(); val dir = folder.newFolder()
        File(dir, "test-model.part").writeText("wrong partial")
        val downloader = ModelDownloader { _, _ -> ModelResponse(200, null, ByteArrayInputStream(bytes)) }
        assertArrayEquals(bytes, downloader.download(model, dir, { Long.MAX_VALUE }).readBytes())
    }
    @Test fun corruptModelNeverBecomesInstalled() = runTest {
        val model = spec(); val dir = folder.newFolder()
        val downloader = ModelDownloader { _, _ -> ModelResponse(200, null, ByteArrayInputStream(ByteArray(bytes.size))) }
        try { downloader.download(model, dir, { Long.MAX_VALUE }); fail("Must reject checksum") } catch (_: ModelIntegrityException) { }
        assertFalse(File(dir, "test-model").exists()); assertFalse(File(dir, "test-model.part").exists())
    }
    @Test fun lowStorageNeverStartsNetwork() = runTest {
        val model = spec()
        val downloader = ModelDownloader { _, _ -> error("Network must not start") }
        try { downloader.download(model, folder.newFolder(), { 0 }); fail("Must reject low storage") } catch (_: ModelStorageException) { }
    }
    @Test fun cancellationPreservesPartialAndNeverInstalls() = runTest {
        val model = spec(); val dir = folder.newFolder()
        val downloader = ModelDownloader { _, _ -> ModelResponse(200, null, ByteArrayInputStream(bytes)) }
        try {
            downloader.download(model, dir, { Long.MAX_VALUE }) { throw CancellationException("pause") }
            fail("Must cancel")
        } catch (_: CancellationException) { }
        assertTrue(File(dir, "test-model.part").exists()); assertFalse(File(dir, "test-model").exists())
    }
    @Test fun incorrectRangeIsRejected() = runTest {
        val model = spec(); val dir = folder.newFolder()
        val downloader = ModelDownloader { _, _ -> ModelResponse(206, "bytes 10-20/100", ByteArrayInputStream(bytes)) }
        try { downloader.download(model, dir, { Long.MAX_VALUE }); fail("Must reject range") } catch (_: ModelIntegrityException) { }
        assertFalse(File(dir, "test-model").exists())
    }
}
