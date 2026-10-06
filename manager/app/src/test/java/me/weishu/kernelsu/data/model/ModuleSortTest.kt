package me.weishu.kernelsu.data.model

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleSortTest {

    private val collator = Collator.getInstance(Locale.ROOT)

    private fun facts(
        id: String,
        name: String = id,
        meta: Boolean = false,
        webUi: Boolean = false,
        action: Boolean = false,
    ) = ModuleSortFacts(
        id = id,
        name = name,
        metaModule = meta,
        hasWebUi = webUi,
        hasActionScript = action,
    )

    private fun sorted(
        modules: List<ModuleSortFacts>,
        priorities: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet(),
    ): List<String> = modules
        .sortedWith(moduleSortComparator(collator, priorities))
        .map { it.id }

    @Test
    fun `group detection prefers the more specific kind`() {
        // A metamodule named like an Xposed module is still a metamodule.
        assertEquals(
            ModuleSortGroup.MetaModule,
            facts("zygisk_meta", name = "LSPosed Meta", meta = true, webUi = true).sortGroup(),
        )
        assertEquals(
            ModuleSortGroup.Zygisk,
            facts("zygisknext", name = "LSPosed helper", webUi = true).sortGroup(),
        )
        assertEquals(
            ModuleSortGroup.LSPosed,
            facts("some_xposed", name = "LSPosed", webUi = true).sortGroup(),
        )
        assertEquals(ModuleSortGroup.WebUi, facts("web", webUi = true, action = true).sortGroup())
        assertEquals(ModuleSortGroup.ActionScript, facts("act", action = true).sortGroup())
        assertNull(facts("plain").sortGroup())
    }

    @Test
    fun `priority order ranks ticked groups above the alphabet`() {
        val modules = listOf(
            facts("aaa_plain"),
            facts("zzz_action", action = true),
            facts("mmm_web", webUi = true),
            facts("zzz_lsposed", name = "LSPosed"),
            facts("mmm_zygisk"),
            facts("zzz_meta", meta = true),
        )

        assertEquals(
            listOf("zzz_meta", "mmm_zygisk", "zzz_lsposed", "mmm_web", "zzz_action", "aaa_plain"),
            sorted(modules),
        )
    }

    @Test
    fun `unticked groups fall below the ticked ones and sort by id`() {
        val modules = listOf(
            facts("aaa_plain"),
            facts("bbb_web", webUi = true),
            facts("ccc_action", action = true),
        )

        // Only the web UI group is lifted; the action group now sorts with the rest by id.
        assertEquals(
            listOf("bbb_web", "aaa_plain", "ccc_action"),
            sorted(modules, setOf(ModuleSortGroup.WebUi)),
        )
    }

    @Test
    fun `unticking everything is plain alphabetical order`() {
        val modules = listOf(facts("bbb_web", webUi = true), facts("aaa_plain"))
        assertEquals(listOf("aaa_plain", "bbb_web"), sorted(modules, emptySet()))
    }

    @Test
    fun `priority store round trips and treats an empty choice as a choice`() {
        val all = ModuleSortPriorityGroups.toSet()
        assertEquals(all, ModuleSortPriorityStore.decode(ModuleSortPriorityStore.encode(all)))
        assertEquals(
            setOf(ModuleSortGroup.Zygisk, ModuleSortGroup.WebUi),
            ModuleSortPriorityStore.decode(
                ModuleSortPriorityStore.encode(setOf(ModuleSortGroup.WebUi, ModuleSortGroup.Zygisk)),
            ),
        )
        assertEquals(all, ModuleSortPriorityStore.decode(null))
        assertEquals(emptySet<ModuleSortGroup>(), ModuleSortPriorityStore.decode(""))
        // Values from another version are ignored, and all groups is the safer reading.
        assertEquals(all, ModuleSortPriorityStore.decode("unknown"))
        assertEquals(all, ModuleSortPriorityStore.decode("unknown,zeta"))
    }

    @Test
    fun `priority store ignores unknown tokens beside known ones`() {
        assertEquals(
            setOf(ModuleSortGroup.LSPosed),
            ModuleSortPriorityStore.decode("lsposed,whatever"),
        )
    }

    @Test
    fun `custom order comparator puts listed ids first and the rest by id`() {
        val modules = listOf(facts("a"), facts("b"), facts("c"))
        val ordered = modules.sortedWith(customOrderComparator(collator, listOf("c", "a"))).map { it.id }
        assertEquals(listOf("c", "a", "b"), ordered)
    }

    @Test
    fun `custom order store round trips and drops blanks`() {
        assertEquals(
            listOf("alpha", "beta"),
            ModuleCustomOrderStore.decode(ModuleCustomOrderStore.encode(listOf("alpha", "", "beta", "alpha"))),
        )
        assertEquals(emptyList<String>(), ModuleCustomOrderStore.decode(null))
        assertEquals(emptyList<String>(), ModuleCustomOrderStore.decode(""))
    }

    @Test
    fun `reconcile keeps installed order and appends new modules`() {
        assertEquals(
            listOf("b", "c", "a"),
            reconcileCustomOrder(order = listOf("b", "gone", "c"), moduleIds = listOf("a", "b", "c")),
        )
    }

    @Test
    fun `reconcile leaves an unset order unset`() {
        assertEquals(emptyList<String>(), reconcileCustomOrder(order = emptyList(), moduleIds = listOf("a", "b")))
    }
}
