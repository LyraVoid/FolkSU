package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.media.MusicConfig
import me.weishu.kernelsu.media.MusicManager
import me.weishu.kernelsu.media.SoundEffectConfig
import me.weishu.kernelsu.media.SoundEffectManager
import me.weishu.kernelsu.media.VibrationConfig
import me.weishu.kernelsu.media.VibrationManager
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedItemContainer
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

private val MediaGroupModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)

@Composable
fun MultimediaCategoryContent() {
    MusicSettingsSection()
    SoundSettingsSection(startup = false)
    SoundSettingsSection(startup = true)
    val context = LocalContext.current
    SegmentedColumn(modifier = MediaGroupModifier, title = stringResource(R.string.media_vibration)) {
        item {
            SegmentedSwitchItem(title = stringResource(R.string.media_vibration), checked = VibrationConfig.isVibrationEnabled) {
                VibrationConfig.setEnabledState(it)
                VibrationConfig.save(context)
            }
        }
        item(visible = VibrationConfig.isVibrationEnabled) {
            FeedbackScope(VibrationConfig.scope) { VibrationConfig.setScopeValue(it); VibrationConfig.save(context) }
        }
        item(visible = VibrationConfig.isVibrationEnabled) {
            MediaSlider(stringResource(R.string.media_intensity), VibrationConfig.vibrationIntensity) {
                VibrationConfig.setIntensityValue(it); VibrationConfig.save(context); VibrationManager.vibrate(context)
            }
        }
    }
}

@Composable
private fun MusicSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) { MusicConfig.saveMusicFile(context, uri) }
            if (success) MusicManager.reload() else Toast.makeText(context, R.string.media_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val playing by MusicManager.isPlaying.collectAsStateWithLifecycle()
    val position by MusicManager.currentPosition.collectAsStateWithLifecycle()
    val duration by MusicManager.duration.collectAsStateWithLifecycle()
    val hasError by MusicManager.hasError.collectAsStateWithLifecycle()
    SegmentedColumn(modifier = MediaGroupModifier, title = stringResource(R.string.media_music)) {
        item {
            SegmentedSwitchItem(title = stringResource(R.string.media_music), checked = MusicConfig.isMusicEnabled) {
                MusicConfig.setMusicEnabledState(it); MusicConfig.save(context); MusicManager.reload()
            }
        }
        item { MediaFileRow(MusicConfig.musicFilename) { picker.launch("audio/*") } }
        item(visible = hasError) {
            SegmentedListItem(headlineContent = { Text(stringResource(R.string.media_play_failed)) })
        }
        item(visible = MusicConfig.isMusicEnabled) {
            SegmentedSwitchItem(title = stringResource(R.string.media_autoplay), checked = MusicConfig.isAutoPlayEnabled) {
                MusicConfig.setAutoPlayEnabledState(it); MusicConfig.save(context); MusicManager.reload()
            }
        }
        item(visible = MusicConfig.isMusicEnabled) {
            SegmentedSwitchItem(title = stringResource(R.string.media_loop), checked = MusicConfig.isLoopingEnabled) {
                MusicConfig.setLoopingEnabledState(it); MusicConfig.save(context); MusicManager.updateLooping(it)
            }
        }
        item(visible = MusicConfig.isMusicEnabled) {
            MediaSlider(stringResource(R.string.media_volume), MusicConfig.volume) {
                MusicConfig.setVolumeValue(it); MusicConfig.save(context); MusicManager.updateVolume(it)
            }
        }
        item(visible = MusicConfig.isMusicEnabled && MusicConfig.musicFilename != null) {
            SegmentedListItem(onClick = { MusicManager.toggle() }, headlineContent = {
                Text(stringResource(if (playing) R.string.media_pause else R.string.media_play))
            })
        }
        item(visible = duration > 0) {
            SegmentedItemContainer {
                Column(Modifier.padding(16.dp)) {
                    Text("${mediaTime(position)} / ${mediaTime(duration)}")
                    val slider = rememberSliderState(value = position.toFloat(), trackRange = 0f..duration.toFloat().coerceAtLeast(1f))
                    LaunchedEffect(position) { if (!slider.isDragging) slider.value = position.toFloat() }
                    Slider(state = slider, onValueChange = { slider.value = it },
                        onValueChangeFinished = { MusicManager.seekTo(slider.value.toInt()) })
                }
            }
        }
        item(visible = MusicConfig.musicFilename != null) {
            ClearMediaRow { MusicManager.stop(); MusicConfig.clearMusic(context) }
        }
    }
}

@Composable
private fun SoundSettingsSection(startup: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) {
                if (startup) SoundEffectConfig.saveStartupSoundFile(context, uri) else SoundEffectConfig.saveSoundEffectFile(context, uri)
            }
            if (!success) Toast.makeText(context, R.string.media_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val enabled = if (startup) SoundEffectConfig.isStartupSoundEnabled else SoundEffectConfig.isSoundEffectEnabled
    val source = if (startup) SoundEffectConfig.startupSourceType else SoundEffectConfig.sourceType
    val presets = if (startup) SoundEffectConfig.STARTUP_PRESETS else SoundEffectConfig.PRESETS
    val preset = if (startup) SoundEffectConfig.startupPresetName else SoundEffectConfig.presetName
    val filename = if (startup) SoundEffectConfig.startupSoundFilename else SoundEffectConfig.soundEffectFilename
    SegmentedColumn(modifier = MediaGroupModifier, title = stringResource(if (startup) R.string.media_startup else R.string.media_sound)) {
        item {
            SegmentedSwitchItem(title = stringResource(if (startup) R.string.media_startup else R.string.media_sound), checked = enabled) {
                if (startup) SoundEffectConfig.setStartupEnabledState(it) else SoundEffectConfig.setEnabledState(it)
                SoundEffectConfig.save(context)
            }
        }
        item(visible = enabled) {
            SegmentedDropdownItem(title = stringResource(R.string.media_source),
                items = listOf(stringResource(R.string.media_local), stringResource(R.string.media_preset)),
                selectedIndex = if (source == SoundEffectConfig.SOURCE_TYPE_PRESET) 1 else 0,
                onItemSelected = {
                    val value = if (it == 1) SoundEffectConfig.SOURCE_TYPE_PRESET else SoundEffectConfig.SOURCE_TYPE_LOCAL
                    if (startup) SoundEffectConfig.setStartupSourceTypeValue(value) else SoundEffectConfig.setSourceTypeValue(value)
                    SoundEffectConfig.save(context)
                })
        }
        item(visible = enabled && source == SoundEffectConfig.SOURCE_TYPE_LOCAL) { MediaFileRow(filename) { picker.launch("audio/*") } }
        item(visible = enabled && source == SoundEffectConfig.SOURCE_TYPE_PRESET) {
            SegmentedDropdownItem(title = stringResource(R.string.media_preset), items = presets,
                selectedIndex = presets.indexOf(preset).coerceAtLeast(0), onItemSelected = {
                    if (startup) SoundEffectConfig.setStartupPresetNameValue(presets[it]) else SoundEffectConfig.setPresetNameValue(presets[it])
                    SoundEffectConfig.save(context)
                })
        }
        item(visible = enabled && !startup) {
            FeedbackScope(SoundEffectConfig.scope) { SoundEffectConfig.setScopeValue(it); SoundEffectConfig.save(context) }
        }
        item(visible = enabled) {
            SegmentedListItem(onClick = {
                if (startup) SoundEffectManager.playStartup(context) else SoundEffectManager.play(context)
            }, headlineContent = { Text(stringResource(R.string.media_play)) })
        }
        item(visible = filename != null) {
            ClearMediaRow {
                if (startup) SoundEffectConfig.clearStartupSound(context) else SoundEffectConfig.clearSoundEffect(context)
                SoundEffectManager.release()
            }
        }
    }
}

@Composable
private fun FeedbackScope(value: String, onChange: (String) -> Unit) {
    SegmentedDropdownItem(title = stringResource(R.string.media_scope),
        items = listOf(stringResource(R.string.media_global), stringResource(R.string.media_navigation)),
        selectedIndex = if (value == "bottom_bar") 1 else 0,
        onItemSelected = { onChange(if (it == 1) "bottom_bar" else "global") })
}

@Composable
internal fun MediaFileRow(filename: String?, onClick: () -> Unit) {
    SegmentedListItem(onClick = onClick, headlineContent = { Text(stringResource(R.string.media_select_file)) },
        supportingContent = { Text(filename ?: stringResource(R.string.media_no_file)) })
}

@Composable
internal fun ClearMediaRow(onClick: () -> Unit) {
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    SegmentedListItem(onClick = { confirm = true }, headlineContent = { Text(stringResource(R.string.media_clear)) })
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.media_clear)) },
        text = { Text(stringResource(R.string.media_clear_confirm)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirm = false; onClick() }) { Text(stringResource(R.string.media_clear)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun MediaSlider(title: String, value: Float, onChange: (Float) -> Unit) {
    SegmentedSliderItem(title = title, value = value, valueRange = 0f..1f,
        valueText = { "${(it * 100).toInt()}%" }, onValueChangeFinished = onChange)
}

private fun mediaTime(milliseconds: Int): String = "%d:%02d".format(milliseconds / 60000, milliseconds / 1000 % 60)
