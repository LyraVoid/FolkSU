package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
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
 * One card of the Focus board: the icon-and-title header, a hairline, then the rows. The action
 * button in the header is the card's only control.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeTileCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HomeCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerIconModifier = Modifier.size(32.dp)
                when {
                    iconRes != null -> Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = headerIconModifier,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    icon != null -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = headerIconModifier,
                        tint = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}
