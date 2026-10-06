package me.weishu.kernelsu.data.modulestore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoParsersTest {

    private val officialIndex = """
        [
          {
            "name": "Alpha",
            "version": "1.2.3",
            "url": "https://cdn.example.com/alpha.zip",
            "description": "Chinese summary",
            "description_en": "English summary",
            "parameter": 1
          },
          {
            "name": "Beta",
            "version": "0.1",
            "url": "file:///data/local/tmp/beta.zip",
            "description": "Local only",
            "description_en": "Should be dropped"
          }
        ]
    """.trimIndent()

    @Test
    fun `official index prefers localized description`() {
        val chinese = parseOfficialModules(officialIndex, "zh").single()
        assertEquals("Chinese summary", chinese.description)
    }

    @Test
    fun `official index prefers english description for non zh`() {
        val english = parseOfficialModules(officialIndex, "en").single()
        assertEquals("English summary", english.description)
    }

    @Test
    fun `official index drops non http entries and synthesises a release`() {
        val modules = parseOfficialModules(officialIndex, "zh")
        assertEquals(listOf("Alpha"), modules.map { it.id })
        val release = modules.single().latestRelease
        assertEquals("https://cdn.example.com/alpha.zip", release?.downloadUrl)
        assertEquals("1.2.3", release?.version)
    }

    private val clusterIndex = """
        [
          {
            "name": "First",
            "url": "https://example.com/repo1/",
            "description": "one",
            "modules_count": 3,
            "cover": "https://example.com/cover.png"
          },
          {
            "name": "NoUrl",
            "description": "missing url",
            "modules_count": 9
          },
          {
            "name": "Already",
            "url": "$DEFAULT_REPOSITORY_URL",
            "description": "default already listed",
            "modules_count": 5
          }
        ]
    """.trimIndent()

    @Test
    fun `cluster index requires url and reads module count`() {
        val repositories = parseRepositories(clusterIndex)
        assertEquals(listOf("First", "Already"), repositories.map { it.name })
        assertEquals(3, repositories.first().modulesCount)
        assertEquals("https://example.com/repo1/", repositories.first().url)
        assertEquals("https://example.com/cover.png", repositories.first().cover)
    }

    @Test
    fun `default repository prepended when absent`() {
        val parsed = parseRepositories("""[{"name":"First","url":"https://example.com/repo1/"}]""")
        val merged = withDefaultRepository(parsed)
        assertEquals(DEFAULT_REPOSITORY_URL, merged.first().url)
        assertEquals(parsed.size + 1, merged.size)
    }

    @Test
    fun `default repository not duplicated when already present`() {
        val parsed = parseRepositories(clusterIndex)
        val merged = withDefaultRepository(parsed)
        assertEquals(parsed.map { it.url }, merged.map { it.url })
        assertEquals(1, merged.count { it.url == DEFAULT_REPOSITORY_URL })
    }

    @Test
    fun `default merge keeps duplicate entries from the fetched list`() {
        val parsed = parseRepositories(
            """
                [{"name":"A","url":"https://example.com/a/"},
                 {"name":"A again","url":"https://example.com/a/"}]
            """.trimIndent(),
        )
        val merged = withDefaultRepository(parsed)
        assertEquals(3, merged.size)
        assertEquals(2, merged.count { it.url == "https://example.com/a/" })
    }

    private val repoBase = "https://repo.example.com/modules/"

    private val magiskIndex = """
        {
          "modules": [
            {
              "id": "tracked",
              "name": "Tracked",
              "version": "9.0",
              "versionCode": 90,
              "author": "Someone",
              "description": "track values win",
              "license": "MIT",
              "homepage": "https://fallback.example.com",
              "source": "https://fallback.example.com/src",
              "support": "https://fallback.example.com/support",
              "track": {
                "license": "Apache-2.0",
                "homepage": "https://track.example.com",
                "source": "https://track.example.com/src",
                "support": "https://track.example.com/support"
              },
              "versions": [
                {"version": "8.0", "versionCode": 80, "zipUrl": "zip/tracked-8.0.zip", "changelog": "older", "timestamp": 1700000000.0},
                {"version": "9.0", "versionCode": 90, "zipUrl": "zip/tracked-9.0.zip", "changelog": "newer", "timestamp": 1700000100.0},
                {"version": "10.0", "versionCode": 100, "zipUrl": "zip/tracked-10.0.zip", "changelog": "newest", "timestamp": 1700000200.0}
              ]
            },
            {
              "id": "no-download",
              "name": "NoDownload",
              "version": "1.0",
              "versionCode": 1,
              "description": "no versions"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `magisk track values win over top level`() {
        val module = parseMagiskModules(magiskIndex, repoBase).first { it.id == "tracked" }
        assertEquals("Apache-2.0", module.license)
        assertEquals("https://track.example.com", module.homepage)
        assertEquals("https://track.example.com/src", module.source)
        assertEquals("https://track.example.com/support", module.support)
    }

    @Test
    fun `magisk relative zip url resolves against repo base`() {
        val module = parseMagiskModules(magiskIndex, repoBase).first { it.id == "tracked" }
        val release = module.releases.first { it.versionCode == 90L }
        assertEquals("https://repo.example.com/modules/zip/tracked-9.0.zip", release.downloadUrl)
        assertEquals("newer", release.changelog)
        assertEquals(1700000100.0, release.timestamp, 0.0)
    }

    @Test
    fun `magisk latest release matches module version code first`() {
        val module = parseMagiskModules(magiskIndex, repoBase).first { it.id == "tracked" }
        assertEquals(90L, module.latestRelease?.versionCode)
        assertEquals("9.0", module.latestRelease?.version)
    }

    @Test
    fun `magisk latest release falls back to highest version code`() {
        val json = """
            {"modules":[{"id":"m","name":"M","versionCode":0,"versions":[
              {"version":"1.0","versionCode":10,"zipUrl":"a.zip"},
              {"version":"2.0","versionCode":20,"zipUrl":"b.zip"}]}]}
        """.trimIndent()
        val module = parseMagiskModules(json, repoBase).single()
        assertEquals(20L, module.latestRelease?.versionCode)
    }

    @Test
    fun `magisk module without downloadable version has no latest release`() {
        val module = parseMagiskModules(magiskIndex, repoBase).first { it.id == "no-download" }
        assertNull(module.latestRelease)
        assertTrue(module.releases.isEmpty())
    }

    @Test
    fun `resolve modules url appends json path to a base`() {
        assertEquals(
            "https://repo.example.com/json/modules.json",
            resolveRepoModulesUrl("https://repo.example.com"),
        )
        assertEquals(
            "https://repo.example.com/json/modules.json",
            resolveRepoModulesUrl("https://repo.example.com/"),
        )
    }

    @Test
    fun `resolve modules url keeps an explicit json url`() {
        assertEquals(
            "https://repo.example.com/custom/modules.json",
            resolveRepoModulesUrl("https://repo.example.com/custom/modules.json"),
        )
    }
}
