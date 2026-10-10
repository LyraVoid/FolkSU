package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class FptContainerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun customFontExportsWithProtocolNameAndRetainsBytes() {
        val font = temporary.newFile("custom_font_123.ttf")
        val payload = byteArrayOf(0, 1, 0, 0, 0, 1, 0, 16)
        font.writeBytes(payload)
        val name = "${FontAsset.base}.${FontAsset.extension(font)}"
        assertEquals("font.ttf", name)
        val json = JSONObject().put("fontMode", "custom").put("isFontEnabled", true)
        val encrypted = FptContainer.write(json, mapOf(name to font))
        val fpEntries = fpImport(encrypted)
        assertArrayEquals(payload, fpEntries.getValue("font.ttf"))
        assertFalse(fpEntries.containsKey("font.jpg"))
        val (imported, files) = FptContainer.read(ByteArrayInputStream(encrypted), temporary.newFolder())
        assertEquals("custom", imported.getString("fontMode"))
        assertArrayEquals(payload, files.getValue("font.ttf").readBytes())
    }
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256")
        .digest("FolkPatchThemeSecretKey2025".toByteArray()), "AES")

    // Independent producer/consumer using the FP wire protocol, not FptContainer.write/read.
    private fun fpExport(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
        val iv = ByteArray(16) { it.toByte() }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return iv + cipher.doFinal(output.toByteArray())
    }

    private fun fpImport(bytes: ByteArray): Map<String, ByteArray> {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(bytes.copyOfRange(0, 16)))
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(cipher.doFinal(bytes, 16, bytes.size - 16))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    @Test fun fpToSuToFpRetainsConfigurationMetadataAndExactResourceNames() {
        val json = JSONObject().put("customColor", "sakura").put("nightModeEnabled", true)
            .put("nightModeFollowSys", false).put("useSystemDynamicColor", false)
            .put("colorGenerationMode", "custom").put("colorStandard", "M3E_2025")
            .put("colorStyle", "EXPRESSIVE").put("colorContrast", "HIGH")
            .put("appLanguage", "zh-TW").put("statsTopLayout", "grid")
            .put("isListWorkingCardModeHidden", true).put("musicFilename", "song.v2.mp3")
            .put("futureField", JSONObject().put("version", 2))
        ThemeMetadata("Round trip", "tablet", "2.3", "Author", "Description").write(json)
        val payload = byteArrayOf(1, 2, 3, 4)
        val (imported, files) = FptContainer.read(ByteArrayInputStream(fpExport(mapOf(
            "theme.json" to json.toString().toByteArray(), "song.v2.mp3" to payload,
            "background_kernel.png" to payload))), temporary.newFolder())
        val result = fpImport(FptContainer.write(imported, files))
        val returned = JSONObject(String(result.getValue("theme.json")))
        assertEquals(json.toString(), returned.toString())
        assertEquals(ThemeMetadata.read(json), ThemeMetadata.read(returned))
        assertArrayEquals(payload, result["song.v2.mp3"])
        assertArrayEquals(payload, result["background_kernel.png"])
    }

    @Test fun pureConfigurationThemeHasOnlyThemeJson() {
        val bytes = FptContainer.write(JSONObject().put("fontMode", "system"), emptyMap())
        assertEquals(setOf("theme.json"), fpImport(bytes).keys)
        assertTrue(FptContainer.read(ByteArrayInputStream(bytes), temporary.newFolder()).second.isEmpty())
    }

    @Test fun pathTraversalAndMissingConfigAreRejected() {
        for (entries in listOf(mapOf("../outside" to byteArrayOf(1)), mapOf("resource" to byteArrayOf(1)))) {
            assertThrows(Exception::class.java) {
                FptContainer.read(ByteArrayInputStream(fpExport(entries)), temporary.newFolder())
            }
        }
    }

    @Test fun truncatedOrCorruptContainerIsRejected() {
        val bytes = fpExport(mapOf("theme.json" to "{}".toByteArray()))
        assertThrows(Exception::class.java) {
            FptContainer.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 1)), temporary.newFolder())
        }
        // Corrupt the first ciphertext block: the decrypted local header is no longer a zip entry.
        bytes[16] = (bytes[16].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            FptContainer.read(ByteArrayInputStream(bytes), temporary.newFolder())
        }
    }

    @Test fun streamingWriteProducesAContainerTheReaderAccepts() {
        val resource = temporary.newFile("background.png")
        val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
        resource.writeBytes(payload)
        val json = JSONObject().put("isBackgroundEnabled", true).put("meta_type", "tablet")
        val target = ByteArrayOutputStream()
        FptContainer.write(json, mapOf("background.png" to resource), target)
        val bytes = target.toByteArray()
        assertEquals(0, bytes.size % 16)
        val (imported, files) = FptContainer.read(ByteArrayInputStream(bytes), temporary.newFolder())
        assertEquals("tablet", imported.getString("meta_type"))
        assertArrayEquals(payload, files.getValue("background.png").readBytes())
    }

    @Test fun misalignedContainerLengthIsRejected() {
        val bytes = fpExport(mapOf("theme.json" to "{}".toByteArray()))
        assertThrows(Exception::class.java) {
            FptContainer.read(ByteArrayInputStream(bytes + ByteArray(8)), temporary.newFolder())
        }
    }

    @Test fun ciphertextBudgetCountsBulkAndSingleByteReads() {
        val bulk = BoundedInputStream(ByteArrayInputStream(ByteArray(9)), 8)
        assertEquals(4, bulk.read(ByteArray(4)))
        assertEquals(4, bulk.read(ByteArray(4)))
        assertThrows(IllegalArgumentException::class.java) { bulk.read() }
        val single = BoundedInputStream(ByteArrayInputStream(ByteArray(3)), 2)
        assertEquals(0, single.read())
        assertEquals(0, single.read())
        assertThrows(IllegalArgumentException::class.java) { single.read(ByteArray(1)) }
    }

    @Test fun invalidExportEntryIsRejectedBeforeWritingToTarget() {
        val file = temporary.newFile("resource")
        file.writeBytes(byteArrayOf(1))
        val target = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            FptContainer.write(JSONObject(), mapOf("../outside" to file), target)
        }
        assertEquals(0, target.size())
    }

    @Test fun invalidFinalPaddingIsRejectedEvenAfterACompleteZip() {
        val zipBytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("theme.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        // Preserve the complete archive but deliberately end the decrypted payload in zero,
        // which is never a valid PKCS5 padding length.
        val plain = zipBytes.copyOf((zipBytes.size / 16 + 1) * 16)
        val iv = ByteArray(16)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        assertThrows(Exception::class.java) {
            FptContainer.read(ByteArrayInputStream(iv + cipher.doFinal(plain)), temporary.newFolder())
        }
    }
}
