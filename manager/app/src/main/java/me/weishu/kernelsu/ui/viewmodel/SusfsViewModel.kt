package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.susfs.SusfsRepository
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.data.susfs.SusfsApplyReport
import me.weishu.kernelsu.ui.util.RootShell
import me.weishu.kernelsu.ui.util.RootShellStatus
import org.json.JSONObject

data class SusfsUiState(
    val status: SusfsStatus = SusfsStatus(),
    val config: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val report: SusfsApplyReport? = null,
    val savedNotice: Boolean = false,
)

class SusfsViewModel(private val repository: SusfsRepository = SusfsRepository()) : ViewModel() {
    private val mutable = MutableStateFlow(SusfsUiState())
    val state = mutable.asStateFlow()
    private var token = 0L

    init {
        viewModelScope.launch {
            combine(RootShell.status, RootShell.generation) { status, generation -> status to generation }
                .collect { (status, _) ->
                    if (status == RootShellStatus.Ready) refresh()
                    else {
                        token++
                        mutable.update { it.copy(status = SusfsStatus(protocol = "root_unavailable"), config = null, busy = false, report = null, savedNotice = false) }
                    }
                }
        }
    }

    fun refresh() {
        if (mutable.value.busy) return
        val request = ++token
        val generation = RootShell.generation.value
        viewModelScope.launch {
            try {
                val status = repository.status()
                val config = if (status.error == null) repository.export() else null
                if (request == token && generation == RootShell.generation.value) {
                    mutable.update { it.copy(status = status, config = config) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == token && generation == RootShell.generation.value) mutable.update {
                    it.copy(status = SusfsStatus(protocol = "root_unavailable", error = e.message), config = null)
                }
            }
        }
    }

    private fun operation(applying: Boolean = false, block: suspend () -> String) {
        if (mutable.value.busy) return
        val request = ++token
        val generation = RootShell.generation.value
        mutable.update { it.copy(busy = true, message = null, savedNotice = false, report = if (applying) null else it.report) }
        viewModelScope.launch {
            try {
                val result = block()
                val report = if (applying) SusfsApplyReport.parse(result) else null
                if (request == token && generation == RootShell.generation.value) {
                    mutable.update { it.copy(message = null, report = report ?: it.report, savedNotice = !applying) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == token && generation == RootShell.generation.value) {
                    mutable.update { it.copy(message = e.message ?: "Operation timed out; refresh status before retrying") }
                }
            }
            finally {
                if (request == token) {
                    mutable.update { it.copy(busy = false) }
                    refresh()
                }
            }
        }
    }

    fun save(text: String) {
        if (!mutable.value.status.detected || mutable.value.status.protocol != "compatible") return
        operation { repository.save(text) }
    }
    fun apply() {
        if (!mutable.value.status.writable) return
        operation(applying = true) { repository.apply() }
    }
    fun change(edit: (JSONObject) -> Unit) {
        if (!mutable.value.status.detected || mutable.value.status.protocol != "compatible") return
        // Read fresh on-disk data rather than writing the screen's stale snapshot. This is not a
        // cross-process transaction: the daemon validates and atomically saves the complete file.
        operation {
            val json = JSONObject(repository.export())
            edit(json)
            repository.save(json.toString())
        }
    }
    fun message(text: String) { mutable.update { it.copy(message = text) } }
}
