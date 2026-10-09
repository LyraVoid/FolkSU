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
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            FptContainer.read(ByteArrayInputStream(bytes), temporary.newFolder())
        }
    }
}
