package me.weishu.kernelsu.ui.screen.home

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WorkCardBackgroundDialog
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.wallpaper.AnimatedFileImage
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.isAnimatedImageFile
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceFlag

/** The gap between the tiles of the two-column grid. */
private val GridTileSpacing = 12.dp

/**
 * The two-column grid: a hero status card on the left with the two count cards stacked on the
 * right. On a device without the kernel module there are no counts, so the hero takes the full
 * width.
 */
@Composable
internal fun GridHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    superuserCount: Int,
    moduleEnabledCount: Int,
) {
    if (Natives.isFullFeatured()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(GridTileSpacing)
        ) {
            GridStatusCard(
                state = state,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            CountCardPair(
                superuserCount = superuserCount,
                moduleEnabledCount = moduleEnabledCount,
                onOpenSuperUser = actions.onOpenSuperUser,
                onOpenModule = actions.onOpenModule,
                layout = CountCardLayout.Vertical,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                pairSpacing = GridTileSpacing,
                // The type ladder's line boxes sit closer together than the plain Material ones, so
                // the extra inset is what brings the two tiles to the height this grid is meant to
                // have.
                contentPadding = PaddingValues(18.dp),
                emphasis = CountCardEmphasis.Value,
            )
        }
    } else {
        GridStatusCard(
            state = state,
            actions = actions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The grid hero card. The status icon sits in the top corner while the state title and its mode
 * tag sit against the bottom edge, so the card reads as a tile rather than a list row.
 */
@Composable
private fun GridStatusCard(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

    val workCardSurface = WallpaperConfig.workCardSurface
    val workCardStyle = HomeWorkCardControl.style(
        layout = HomeWorkCardLayout.Grid,
        working = ksuActive,
        surface = workCardSurface,
    )
    val containerColor = workCardStyle.containerColor
    val contentColor = workCardStyle.contentColor ?: contentColorFor(containerColor)
    val backgroundUri = workCardStyle.workCardBackgroundUri

    val statusIcon = when {
        ksuActive -> Icons.Rounded.CheckCircle
        notInstalled -> Icons.Rounded.Warning
        else -> Icons.Rounded.Block
    }
    val statusTitle = when {
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val workingMode = if (ksuActive) {
        when (state.lkmMode) {
            null -> ""
            true -> "LKM"
            else -> "GKI"
        }
    } else ""

    var showWorkCardOptions by remember { mutableStateOf(false) }
    val longPressEnabled = ksuActive && workCardSurface.enabled

    HomeCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        wallpaperRole = workCardStyle.wallpaperRole,
        onClick = {
            if (!state.isLateLoadMode) {
                actions.onInstallClick()
            }
        },
        onLongClick = if (longPressEnabled) {
            { showWorkCardOptions = true }
        } else {
            null
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundUri != null) {
                WorkCardBackgroundImage(uri = backgroundUri, surface = workCardSurface)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!workCardSurface.hasFlag(SurfaceFlag.HideText)) {
                    Text(
                        text = statusTitle,
                        style = FolkType.Title
                    )
                }
                if (ksuActive && workingMode.isNotEmpty()) {
                    if (!workCardSurface.hasFlag(SurfaceFlag.HideMode)) {
                        StatusTag(
                            label = workingMode,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (notInstalled && state.isSELinuxPermissive) {
                    StatusJailbreakButton(onClick = actions.onJailbreakClick)
                }
            }
            if (!workCardSurface.hasFlag(SurfaceFlag.HideIcon)) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusTitle,
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                )
            }
        }
    }

    if (showWorkCardOptions) {
        WorkCardBackgroundDialog(onDismiss = { showWorkCardOptions = false })
    }
}

/**
 * The photo behind the grid work card, dimmed with a black scrim so the overlaid label stays
 * readable. Draws nothing until the stored bitmap is decoded.
 */
@Composable
private fun WorkCardBackgroundImage(uri: String, surface: SurfaceConfig) {
    val isDark = isInDarkTheme()
    val opacity = surface.effectiveOpacity(isDark)
    val path = remember(uri) { Uri.parse(uri).path }
    val file = remember(path) { path?.let { File(it) } }
    if (file != null && isAnimatedImageFile(file)) {
        // Animated images (GIF) play natively; everything else keeps the downsampled bitmap path.
        AnimatedFileImage(
            file = file,
            modifier = Modifier
                .fillMaxSize()
                .alpha(opacity),
        )
    } else {
        val image by produceState<ImageBitmap?>(initialValue = null, path) {
            value = withContext(Dispatchers.IO) {
                path?.let { WallpaperManager.decodeSampled(File(it), 1600)?.asImageBitmap() }
            }
        }
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(opacity),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = surface.dim)),
    )
}
