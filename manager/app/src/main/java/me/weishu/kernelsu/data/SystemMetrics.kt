package me.weishu.kernelsu.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Battery level/temperature and CPU temperature, refreshed on a timer by the home screen.
 *
 * Every field is nullable: a source that is unavailable (no battery broadcast, no readable
 * thermal zone) reports `null` instead of a fabricated zero.
 */
@Immutable
data class DeviceStatus(
    val batteryLevelPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val cpuTemperatureC: Float? = null,
    val cpuFrequencies: List<CpuFrequency> = emptyList(),
)

/**
 * One CPU core's clock, in kilohertz, as reported by `/sys/devices/system/cpu/cpuN/cpufreq`.
 *
 * The pairs are what the stats board groups into clusters, so a core whose `cpufreq` node is
 * missing simply does not appear.
 */
@Immutable
data class CpuFrequency(
    val coreIndex: Int,
    val currentFreqKHz: Long,
    val maxFreqKHz: Long,
) {
    /** How far the core currently sits towards its own ceiling, in `0f..1f`. */
    val usedFraction: Float
        get() = if (maxFreqKHz > 0L) (currentFreqKHz.toFloat() / maxFreqKHz).coerceIn(0f, 1f) else 0f
}

/**
 * Internal storage and memory usage, including zram/swap when the device has them.
 *
 * Sizes are in bytes. [zramTotalBytes]/[swapTotalBytes] are `null` (not zero) when the device has
 * no such device, so the UI can hide the row entirely.
 */
@Immutable
data class StorageStatus(
    val dataUsedBytes: Long = 0L,
    val dataTotalBytes: Long = 0L,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val zramUsedBytes: Long? = null,
    val zramTotalBytes: Long? = null,
    val swapUsedBytes: Long? = null,
    val swapTotalBytes: Long? = null,
) {
    val dataUsedFraction: Float
        get() = if (dataTotalBytes > 0L) (dataUsedBytes.toFloat() / dataTotalBytes).coerceIn(0f, 1f) else 0f

    val ramUsedFraction: Float
        get() = if (ramTotalBytes > 0L) (ramUsedBytes.toFloat() / ramTotalBytes).coerceIn(0f, 1f) else 0f

    val zramUsedFraction: Float
        get() = if ((zramTotalBytes ?: 0L) > 0L) (zramUsedBytes!!.toFloat() / zramTotalBytes!!).coerceIn(0f, 1f) else 0f

    val swapUsedFraction: Float
        get() = if ((swapTotalBytes ?: 0L) > 0L) (swapUsedBytes!!.toFloat() / swapTotalBytes!!).coerceIn(0f, 1f) else 0f
}

/**
 * The recent samples of the stats board, oldest first, each capped by the collector loop.
 *
 * They are the same values as [DeviceStatus]/[StorageStatus] at an earlier poll, kept only so the
 * wave charts have a curve to draw.
 */
@Immutable
data class MetricsHistory(
    val cpuTemperature: List<Float> = emptyList(),
    val memoryUsage: List<Float> = emptyList(),
    val batteryLevel: List<Float> = emptyList(),
)

/** The live metrics the home screen renders; either half may be absent. */
@Immutable
data class HomeMetrics(
    val device: DeviceStatus? = null,
    val storage: StorageStatus? = null,
    val history: MetricsHistory = MetricsHistory(),
)

/**
 * Reads device/storage metrics from the kernel's public interfaces only — no root, no shell.
 *
 * - battery: the sticky `ACTION_BATTERY_CHANGED` broadcast;
 * - CPU temperature: the first `cpu*` thermal zone under `/sys/class/thermal`;
 * - CPU clocks: the `cpufreq` node of every core under `/sys/devices/system/cpu`;
 * - memory: `MemTotal`/`MemAvailable` in `/proc/meminfo`, plus `/proc/swaps` for zram and swap;
 * - internal storage: `statvfs` on the data partition.
 */
object SystemMetricsCollector {

    private const val KILOBYTE = 1024L
    private const val CPU_SYSFS = "/sys/devices/system/cpu"
    private const val CPU_DIR_PREFIX_LENGTH = 3
    private val CPU_DIR_PATTERN = Regex("cpu\\d+")

    fun collectDeviceStatus(context: Context): DeviceStatus = DeviceStatus(
        batteryLevelPercent = readBatteryLevelPercent(context),
        batteryTemperatureC = readBatteryTemperatureC(context),
        cpuTemperatureC = readCpuTemperatureC(),
        cpuFrequencies = readCpuFrequencies(),
    )

    fun collectStorageStatus(): StorageStatus {
        val data = readDataUsage()
        val memory = readMemoryUsage()
        val swaps = readSwapUsage()
        return StorageStatus(
            dataUsedBytes = data.first,
            dataTotalBytes = data.second,
            ramUsedBytes = memory.first,
            ramTotalBytes = memory.second,
            zramUsedBytes = swaps.zramUsed,
            zramTotalBytes = swaps.zramTotal,
            swapUsedBytes = swaps.swapUsed,
            swapTotalBytes = swaps.swapTotal,
        )
    }

    private fun batteryIntent(context: Context): Intent? = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    private fun readBatteryLevelPercent(context: Context): Int? {
        val intent = batteryIntent(context) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    private fun readBatteryTemperatureC(context: Context): Float? {
        val intent = batteryIntent(context) ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return null
        return tenths / 10f
    }

    /**
     * SoC/CPU temperature in celsius: the first thermal zone named `cpu*` under
     * `/sys/class/thermal`, falling back to a `soc`/`aoss`/`tsens` zone. Apps cannot read
     * `/proc/stat` on Android, so an instantaneous CPU load needs root and is not used.
     */
    private fun readCpuTemperatureC(): Float? {
        val zones = runCatching { File("/sys/class/thermal").listFiles() }.getOrNull() ?: return null
        val typed = zones
            .filter { it.name.startsWith("thermal_zone") }
            .mapNotNull { zone ->
                val type = runCatching { File(zone, "type").readText().trim() }.getOrNull()
                type?.takeIf { it.isNotEmpty() }?.let { zone to it }
            }
        val zone = typed.firstOrNull { it.second.startsWith("cpu") }
            ?: typed.firstOrNull { it.second.contains("soc") }
            ?: typed.firstOrNull { it.second.contains("aoss") || it.second.contains("tsens") }
            ?: return null
        val raw = runCatching { File(zone.first, "temp").readText().trim().toFloatOrNull() }.getOrNull()
            ?: return null
        return raw / 1000f
    }

    /**
     * The current and maximum clock of every core that exposes a `cpufreq` node, ordered by core.
     *
     * `scaling_cur_freq` is the governor's view of the live clock, so it is preferred over
     * `cpuinfo_cur_freq`; the ceiling comes from `cpuinfo_max_freq` and falls back to the scaling
     * limit. Cores without the node (or without read permission) are skipped.
     */
    private fun readCpuFrequencies(): List<CpuFrequency> {
        val cores = runCatching {
            File(CPU_SYSFS)
                .listFiles { file -> CPU_DIR_PATTERN.matches(file.name) }
                ?.sortedBy { it.name.drop(CPU_DIR_PREFIX_LENGTH).toIntOrNull() ?: Int.MAX_VALUE }
        }.getOrNull() ?: return emptyList()

        return cores.mapNotNull { core ->
            val index = core.name.drop(CPU_DIR_PREFIX_LENGTH).toIntOrNull() ?: return@mapNotNull null
            val cpufreq = File(core, "cpufreq")
            val current = readLong(File(cpufreq, "scaling_cur_freq"))
                ?: readLong(File(cpufreq, "cpuinfo_cur_freq"))
                ?: return@mapNotNull null
            val max = readLong(File(cpufreq, "cpuinfo_max_freq"))
                ?: readLong(File(cpufreq, "scaling_max_freq"))
                ?: current
            CpuFrequency(coreIndex = index, currentFreqKHz = current, maxFreqKHz = max)
        }
    }

    private fun readLong(file: File): Long? =
        runCatching { file.readText().trim().toLongOrNull() }.getOrNull()

    /** Internal storage (used, total) in bytes, from `statvfs` on the data partition. */
    private fun readDataUsage(): Pair<Long, Long> = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val available = stat.availableBlocksLong * stat.blockSizeLong
        (total - available).coerceAtLeast(0L) to total
    }.getOrDefault(0L to 0L)

    /** RAM (used, total) in bytes: `MemTotal - MemAvailable`. */
    private fun readMemoryUsage(): Pair<Long, Long> {
        val values = readMeminfo() ?: return 0L to 0L
        val total = values["MemTotal"] ?: return 0L to 0L
        val available = values["MemAvailable"] ?: values["MemFree"] ?: return 0L to 0L
        return (total - available).coerceAtLeast(0L) to total
    }

    /** `/proc/meminfo` as a map of key to bytes (the file reports kibibytes). */
    private fun readMeminfo(): Map<String, Long>? = runCatching {
        buildMap {
            File("/proc/meminfo").forEachLine { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEachLine
                val key = line.substring(0, separator)
                val kb = line.substring(separator + 1).trim().substringBefore(' ').toLongOrNull()
                if (kb != null) put(key, kb * KILOBYTE)
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private data class SwapUsage(
        val zramUsed: Long?,
        val zramTotal: Long?,
        val swapUsed: Long?,
        val swapTotal: Long?,
    )

    /** Splits `/proc/swaps` rows into zram vs. real swap; each is null when absent. */
    private fun readSwapUsage(): SwapUsage {
        var zramUsed = 0L
        var zramTotal = 0L
        var swapUsed = 0L
        var swapTotal = 0L
        runCatching {
            File("/proc/swaps").forEachLine { line ->
                val parts = line.trim().split(' ').filter { it.isNotEmpty() }
                // Header row starts with "Filename"; a data row is: name type size used priority.
                if (parts.size < 4 || parts.first().startsWith("Filename")) return@forEachLine
                val sizeKb = parts[2].toLongOrNull() ?: return@forEachLine
                val usedKb = parts[3].toLongOrNull() ?: return@forEachLine
                if (parts.first().contains("zram")) {
                    zramTotal += sizeKb * KILOBYTE
                    zramUsed += usedKb * KILOBYTE
                } else {
                    swapTotal += sizeKb * KILOBYTE
                    swapUsed += usedKb * KILOBYTE
                }
            }
        }
        return SwapUsage(
            zramUsed = zramUsed.takeIf { zramTotal > 0L },
            zramTotal = zramTotal.takeIf { it > 0L },
            swapUsed = swapUsed.takeIf { swapTotal > 0L },
            swapTotal = swapTotal.takeIf { it > 0L },
        )
    }
}
