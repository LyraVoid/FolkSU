package me.weishu.kernelsu.ui.component.bottombar

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a bottom navigation destination icon. Prefers the user-picked custom icon (when custom
 * icons are enabled and an image is set) and falls back to the default Material vector.
 */
@Composable
fun NavBarIcon(
    destination: BottomBarDestination,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
) {
    val revision by BottomBarIconConfig.revision.collectAsState()
    val customUri = remember(revision, destination.name) {
        if (BottomBarIconConfig.isEnabled) {
            BottomBarIconConfig.getCustomIconUri(destination.name)
        } else {
            null
        }
    }

    if (customUri != null) {
        val bitmap by produceState<Bitmap?>(initialValue = null, customUri) {
            value = withContext(Dispatchers.IO) {
                BottomBarIconConfig.loadIconBitmap(customUri)
            }
        }
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = stringResource(destination.label),
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
            )
            return
        }
    }

    Icon(
        imageVector = if (isSelected) destination.iconSelected else destination.iconNotSelected,
        contentDescription = stringResource(destination.label),
        tint = tint,
        modifier = modifier,
    )
}
