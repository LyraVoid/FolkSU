package me.weishu.kernelsu.ui.screen.themestore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.theme.FolkType

/**
 * A theme preview tile shared by the store and the local library. It keeps the artwork's own aspect
 * ratio (no cropping) and labels it with a soft bottom gradient, so unknown image sizes simply
 * stagger instead of being flattened into a fixed box.
 */
@Composable
fun ThemePreviewCard(
    name: String,
    model: Any?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = FolkShape.Corner16,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Box {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                loading = { PreviewPlaceholder() },
                error = { PreviewPlaceholder(failed = true) },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = name,
                    style = FolkType.Caption,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PreviewPlaceholder(failed: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (failed) Icons.Outlined.BrokenImage else Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A centered empty state with the palette mark, shared by both theme screens. */
@Composable
fun EmptyThemes(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = FolkType.Summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
