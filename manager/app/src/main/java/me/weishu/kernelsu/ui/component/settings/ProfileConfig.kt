package me.weishu.kernelsu.ui.component.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import me.weishu.kernelsu.ksuApp
import java.io.File
import java.util.UUID

/**
 * Snapshot state for the settings-hub identity header: an optional avatar image, a nickname, a
 * signature line and the avatar's opacity.
 *
 * Fields are Compose state so the header recomposes on edit without a ViewModel. Persistence lives
 * in a dedicated prefs file, mirroring [me.weishu.kernelsu.wallpaper.WallpaperConfig]. The avatar
 * is copied into app-private storage so it survives the source document being revoked.
 */
object ProfileConfig {

    private const val PREFS_NAME = "profile"
    private const val KEY_NICKNAME = "profile_nickname"
    private const val KEY_SIGNATURE = "profile_signature"
    private const val KEY_AVATAR = "profile_avatar"
    private const val KEY_AVATAR_OPACITY = "profile_avatar_opacity"
    private const val AVATAR_FILE_NAME = "profile_avatar"

    /** Shown when the user has not set a nickname; mirrors FolkPatch's "FolkPatch" fallback. */
    const val DEFAULT_NICKNAME = "FolkSU"
    const val DEFAULT_AVATAR_OPACITY = 1f
    const val MIN_AVATAR_OPACITY = 0.1f
    const val NICKNAME_MAX_LENGTH = 24
    const val SIGNATURE_MAX_LENGTH = 60

    var nickname by mutableStateOf("")
        private set

    var signature by mutableStateOf("")
        private set

    /** A `file://` URI into app storage, or null when the default avatar is used. */
    var avatarUri by mutableStateOf<String?>(null)
        private set

    var avatarOpacity by mutableFloatStateOf(DEFAULT_AVATAR_OPACITY)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        nickname = prefs.getString(KEY_NICKNAME, "").orEmpty()
        signature = prefs.getString(KEY_SIGNATURE, "").orEmpty()
        avatarUri = prefs.getString(KEY_AVATAR, null)
        avatarOpacity = prefs.getFloat(KEY_AVATAR_OPACITY, DEFAULT_AVATAR_OPACITY)
    }

    fun save(context: Context = ksuApp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_NICKNAME, nickname)
            putString(KEY_SIGNATURE, signature)
            putString(KEY_AVATAR, avatarUri)
            putFloat(KEY_AVATAR_OPACITY, avatarOpacity)
        }
    }

    fun setNickname(value: String, context: Context = ksuApp) {
        nickname = value.take(NICKNAME_MAX_LENGTH)
        save(context)
    }

    fun setSignature(value: String, context: Context = ksuApp) {
        signature = value.take(SIGNATURE_MAX_LENGTH)
        save(context)
    }

    fun setAvatarOpacity(value: Float, context: Context = ksuApp) {
        avatarOpacity = value.coerceIn(MIN_AVATAR_OPACITY, 1f)
        save(context)
    }

    /** Copies [sourceUri] into app storage and points [avatarUri] at the private copy. */
    fun setAvatar(sourceUri: String, context: Context = ksuApp) {
        val target = avatarFile(context)
        val temp = File(target.parentFile, "${target.name}.tmp")
        // Write to a sibling temp file and rename over the target, so a failed copy never leaves a
        // truncated avatar behind (rename is atomic within the same directory).
        val copied = runCatching {
            context.contentResolver.openInputStream(sourceUri.toUri())?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied || temp.length() == 0L || !temp.renameTo(target)) {
            temp.delete()
            return
        }
        // A revision in the URI changes the key the header/preview decode against, so replacing the
        // file at the fixed path still repaints instead of showing the previous avatar.
        avatarUri = target.toUri().toString() + "?revision=" + UUID.randomUUID()
        save(context)
    }

    fun clearAvatar(context: Context = ksuApp) {
        avatarFile(context).delete()
        avatarUri = null
        save(context)
    }

    fun avatarFile(context: Context = ksuApp): File = File(context.filesDir, AVATAR_FILE_NAME)

    fun reset() {
        nickname = ""
        signature = ""
        avatarUri = null
        avatarOpacity = DEFAULT_AVATAR_OPACITY
    }
}
