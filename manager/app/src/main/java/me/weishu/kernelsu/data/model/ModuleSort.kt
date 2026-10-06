package me.weishu.kernelsu.data.model

import java.text.Collator

/**
 * A kind of installed module, in the order the list should read. The pieces the rest of the system
 * is built on come first, so the list reads roughly like the boot order instead of the alphabet.
 *
 * The stored string of each group is what the priority preference holds, so a value written by a
 * newer version is ignored rather than breaking the order.
 */
enum class ModuleSortGroup(val value: String) {
    /** A module other modules attach to; a device can only have one. */
    MetaModule("metamodule"),

    /** A module that provides or loads the Zygisk runtime. */
    Zygisk("zygisk"),

    /** A module that hooks through the Xposed framework. */
    LSPosed("lsposed"),

    /** A module with a screen of its own. */
    WebUi("webui"),

    /** A module with a script to run. */
    ActionScript("action"),
}

/**
 * The groups a reader can tick, in the order they are ranked while all of them are ticked. A group
 * that is not ticked does not disappear; it only stops being lifted above the alphabet.
 */
val ModuleSortPriorityGroups: List<ModuleSortGroup> = listOf(
    ModuleSortGroup.MetaModule,
    ModuleSortGroup.Zygisk,
    ModuleSortGroup.LSPosed,
    ModuleSortGroup.WebUi,
    ModuleSortGroup.ActionScript,
)

/** Whether [id] looks like a module that carries the Zygisk runtime. */
fun isZygiskModule(id: String): Boolean = id.contains("zygisk", ignoreCase = true)

/** Whether [name] looks like a module built on the Xposed framework. */
fun isLSPosedModule(name: String): Boolean = name.contains("LSPosed", ignoreCase = true)

/**
 * The facts the order is decided from. A module carries them; this file reads no disk.
 *
 * [enabled] is consulted only when enabled-first is on. Without that option a module that is
 * switched off keeps the place its kind earns, because sinking it to the bottom would move it out
 * from under the finger that just toggled it - which is exactly when someone is looking for it.
 */
data class ModuleSortFacts(
    val id: String,
    val name: String,
    val metaModule: Boolean,
    val hasWebUi: Boolean,
    val hasActionScript: Boolean,
    val enabled: Boolean = true,
)

/** Which group [this] belongs to, or `null` when no group claims it. */
fun ModuleSortFacts.sortGroup(): ModuleSortGroup? = when {
    metaModule -> ModuleSortGroup.MetaModule
    isZygiskModule(id) -> ModuleSortGroup.Zygisk
    isLSPosedModule(name) -> ModuleSortGroup.LSPosed
    hasWebUi -> ModuleSortGroup.WebUi
    hasActionScript -> ModuleSortGroup.ActionScript
    else -> null
}

/**
 * Orders the list by the ticked groups in [priorities], then, when [enabledFirst] is on, by whether
 * a module is switched on, then by id in the reader's own alphabet. A group that was not ticked
 * does not disappear; its modules fall in with everything else below the ticked ones, still in the
 * alphabet among themselves.
 *
 * The enabled-first step ranks within a group, so a switched-off module of a highly ranked kind
 * still precedes a switched-on module of a lower one.
 */
fun moduleSortComparator(
    collator: Collator,
    priorities: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet(),
    enabledFirst: Boolean = false,
): Comparator<ModuleSortFacts> {
    val ranking = ModuleSortPriorityGroups.filter { it in priorities }
    return compareBy<ModuleSortFacts> { facts ->
        val index = facts.sortGroup()?.let(ranking::indexOf) ?: -1
        index.takeIf { it >= 0 } ?: ranking.size
    }.thenBy { facts -> enabledFirst && !facts.enabled }
        .thenBy(collator) { it.id }
}

/**
 * Orders by [order] first, then by id for anything the order does not mention. Ids absent from the
 * order sort to the end, so an order written before a module was installed still reads stably.
 */
fun customOrderComparator(
    collator: Collator,
    order: List<String>,
): Comparator<ModuleSortFacts> {
    val positions = order.withIndex().associate { it.value to it.index }
    return compareBy<ModuleSortFacts> { positions[it.id] ?: Int.MAX_VALUE }
        .thenBy(collator) { it.id }
}

/**
 * Keeps the relative order of [order], drops ids no longer installed, and appends ids not yet in
 * it in [moduleIds] order. An empty [order] stays empty: a reader who never set a custom order
 * does not get one from the first refresh.
 */
fun reconcileCustomOrder(order: List<String>, moduleIds: List<String>): List<String> {
    if (order.isEmpty()) return emptyList()

    val installed = moduleIds.toHashSet()
    val kept = order.filter { it in installed }
    val keptIds = kept.toHashSet()
    return kept + moduleIds.filter { it !in keptIds }
}

/**
 * Reads and writes the ticked groups. Both ends are pure so the stored shape stays testable
 * without a device: an empty string is a reader who unticked everything, while no stored value
 * at all is a reader who has never opened the picker, and gets all of them.
 */
object ModuleSortPriorityStore {
    const val Key = "module_sort_groups"

    val Default: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet()

    fun encode(groups: Set<ModuleSortGroup>): String =
        ModuleSortPriorityGroups.filter { it in groups }.joinToString(",") { it.value }

    fun decode(stored: String?): Set<ModuleSortGroup> {
        if (stored == null) return Default

        val tokens = stored.split(',').map(String::trim).filter(String::isNotEmpty)
        val known = tokens.mapNotNullTo(mutableSetOf()) { token ->
            ModuleSortPriorityGroups.firstOrNull { it.value == token }
        }

        // A value holding nothing we know is not a reader's choice; it is a preference from some
        // other version of this screen, and all of them is the safer reading.
        return if (known.isEmpty() && tokens.isNotEmpty()) Default else known
    }
}

/**
 * Reads and writes the custom order, one module id per line. An empty string means no custom
 * order is set.
 */
object ModuleCustomOrderStore {
    const val Key = "module_sort_custom_order"

    fun encode(ids: List<String>): String =
        ids.filter(String::isNotBlank).distinct().joinToString("\n")

    fun decode(stored: String?): List<String> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split('\n').filter(String::isNotEmpty).distinct()
    }
}
