package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig

/** A full-width tonal surface, the one shape every card on the home screen shares. */
@Composable
internal fun HomeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    wallpaperRole: WallpaperSurfaceRole? =
        if (containerColor == MaterialTheme.colorScheme.surfaceBright) WallpaperSurfaceRole.Group else null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TonalCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        wallpaperRole = wallpaperRole,
        shape = FolkShape.Corner20,
        onClick = onClick,
        onLongClick = onLongClick,
        content = content,
    )
}

/**
 * Whether the enclosing [HomeTileCard] is painting a photo behind its content, so nested rows can
 * switch to a high-contrast palette.
 */
internal val LocalHomeTileCardOverImage = staticCompositionLocalOf { false }

/**
 * One card of the Focus board: the icon-and-title header, a hairline, then the rows. The action
 * button in the header is the card's only control. When [background] carries an image the card
 * paints it behind the content and drops to white-on-photo colours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeTileCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    action: (@Composable () -> Unit)? = null,
    background: SurfaceConfig? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backgroundUri = background?.imageUri?.takeIf { it.isNotEmpty() }
    val overImage = backgroundUri != null
    val containerColor = if (overImage) Color.Transparent else MaterialTheme.colorScheme.surfaceBright
    HomeCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = if (overImage) Color.White else contentColorFor(containerColor),
        wallpaperRole = if (overImage) null else WallpaperSurfaceRole.Group,
    ) {
        Box {
            if (background != null && backgroundUri != null) {
                SurfaceBackgroundImage(uri = backgroundUri, surface = background)
            }
            CompositionLocalProvider(LocalHomeTileCardOverImage provides overImage) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val headerIconModifier = Modifier.size(32.dp)
                        val headerIconTint = if (overImage) Color.White else MaterialTheme.colorScheme.primary
                        when {
                            iconRes != null -> Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = headerIconModifier,
                                tint = headerIconTint,
                            )

                            icon != null -> Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = headerIconModifier,
                                tint = headerIconTint,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            modifier = Modifier.weight(1f),
                        )
                        if (action != null) {
                            action()
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = if (overImage) {
                            Color.White.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        content()
                    }
                }
            }
        }
    }
}
