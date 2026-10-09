package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.media.VisualMediaConfig
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

@Composable
fun VisualMediaSettings() {
    VisualMediaSection(video = true)
    VisualMediaSection(video = false)
}

@Composable
private fun VisualMediaSection(video: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) { VisualMediaConfig.select(context, uri, video) }
            if (!success) Toast.makeText(context, R.string.media_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val enabled = if (video) VisualMediaConfig.videoEnabled else VisualMediaConfig.titleEnabled
    val title = stringResource(if (video) R.string.media_video else R.string.media_title_image)
    val filename = if (video) VisualMediaConfig.videoFilename else VisualMediaConfig.titleFilename
    SegmentedColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), title = title) {
        item {
            SegmentedSwitchItem(title = title, checked = enabled) {
                if (video) VisualMediaConfig.videoEnabled = it else VisualMediaConfig.titleEnabled = it
                VisualMediaConfig.save(context)
            }
        }
        item { MediaFileRow(filename) { picker.launch(if (video) "video/*" else "image/*") } }
        if (video) {
            item(visible = enabled) {
                MediaSlider(stringResource(R.string.media_volume), VisualMediaConfig.videoVolume) {
                    VisualMediaConfig.videoVolume = it; VisualMediaConfig.save(context)
                }
            }
        } else {
            item(visible = enabled) {
                MediaSlider(stringResource(R.string.media_day_opacity), VisualMediaConfig.titleDayOpacity) {
                    VisualMediaConfig.titleDayOpacity = it; VisualMediaConfig.save(context)
                }
            }
            item(visible = enabled) {
                MediaSlider(stringResource(R.string.media_night_opacity), VisualMediaConfig.titleNightOpacity) {
                    VisualMediaConfig.titleNightOpacity = it; VisualMediaConfig.save(context)
                }
            }
            item(visible = enabled) {
                MediaSlider(stringResource(R.string.media_dim), VisualMediaConfig.titleDim) {
                    VisualMediaConfig.titleDim = it; VisualMediaConfig.save(context)
                }
            }
            item(visible = enabled) {
                SegmentedSliderItem(title = stringResource(R.string.media_title_offset), value = VisualMediaConfig.titleOffsetX,
                    valueRange = -2f..2f, valueText = { "${(it * 100).toInt()} dp" }, onValueChangeFinished = {
                        VisualMediaConfig.titleOffsetX = it; VisualMediaConfig.save(context)
                    })
            }
        }
        item(visible = filename != null) { ClearMediaRow { scope.launch { VisualMediaConfig.clear(context, video) } } }
    }
}
