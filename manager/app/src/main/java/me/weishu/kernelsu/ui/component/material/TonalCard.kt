package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

/**
 * A rounded card that participates in the unified transparency layering controller.
 *
 * When wallpaper mode is off this is a plain [Card] with [containerColor]. When it is on, the card is
 * drawn through [FolkWallpaperSurface] with [wallpaperRole], so its fill shares one alpha with every
 * other panel instead of staying opaque.
 *
 * The default is derived from the fill: a plain neutral surface ([MaterialTheme.colorScheme.surfaceBright])
 * joins the wallpaper layering, while a card that carries a semantic container colour
 * (errorContainer/secondaryContainer/...) keeps that colour untouched. Pass `wallpaperRole` explicitly
 * to override (for example `null` for the theme preview miniature).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = MaterialTheme.shapes.large,
    wallpaperRole: WallpaperSurfaceRole? =
        if (containerColor == MaterialTheme.colorScheme.surfaceBright) WallpaperSurfaceRole.Group else null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val clickModifier = when {
        onLongClick != null -> Modifier
            .clip(shape)
            .folkPressScale(interactionSource, enabled)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick?.invoke()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onLongClick()
                },
                interactionSource = interactionSource,
                indication = null,
            )

        onClick != null -> Modifier
            .clip(shape)
            .folkPressScale(interactionSource, enabled)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                interactionSource = interactionSource,
                indication = null,
            )

        else -> Modifier
    }

    if (wallpaperRole != null) {
        FolkWallpaperSurface(
            role = wallpaperRole,
            shape = shape,
            modifier = modifier.then(clickModifier),
            fallbackColor = containerColor,
            fallbackContentColor = contentColor,
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.then(clickModifier),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            shape = shape,
        ) {
            content()
        }
    }
}
