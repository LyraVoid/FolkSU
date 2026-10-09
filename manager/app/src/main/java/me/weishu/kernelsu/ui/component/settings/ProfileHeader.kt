package me.weishu.kernelsu.ui.component.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.theme.FolkType

/**
 * The settings hub's identity block: a circular avatar followed by the nickname and one line of
 * supporting text (the signature, falling back to the device name when empty).
 *
 * The disc is a [TonalCard] on purpose - in wallpaper mode it joins the same transparency layering
 * as the rest of the page instead of punching an opaque hole. [avatarOpacity] is applied to the
 * disc alone, so a low value reads as a genuinely translucent avatar rather than a faded row.
 */
@Composable
fun ProfileHeader(
    nickname: String,
    signature: String,
    deviceName: String,
    avatarUri: String?,
    avatarOpacity: Float,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarBitmap by produceState<Bitmap?>(initialValue = null, avatarUri) {
        value = withContext(Dispatchers.IO) { loadAvatarBitmap(avatarUri) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TonalCard(
            modifier = Modifier
                .size(64.dp)
                .alpha(avatarOpacity.coerceIn(0f, 1f)),
            shape = CircleShape,
            onClick = onAvatarClick,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nickname,
                style = FolkType.Title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = signature.ifBlank { deviceName },
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Decode the stored avatar into a bitmap. The file is written already square and downscaled by the
 * picker, so a straight decode is enough; any failure simply falls back to the default glyph.
 */
internal fun loadAvatarBitmap(uriString: String?): Bitmap? {
    if (uriString.isNullOrEmpty()) return null
    return try {
        val uri = Uri.parse(uriString)
        ksuApp.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
}
