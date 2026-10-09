package me.weishu.kernelsu.ui.component.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.SurfaceCropDialog
import me.weishu.kernelsu.ui.component.material.folkPressScale
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.theme.FolkType
import kotlin.math.roundToInt

/**
 * Bottom sheet for editing the identity header, laid out like FolkPatch's editor: two avatar
 * choices side by side, an opacity slider, then the two text fields. Edits are kept in local
 * draft state and only committed to [ProfileConfig] on save, so cancel leaves the header untouched.
 *
 * "Restore defaults" resets the text only - the avatar has its own control, so the two concerns
 * stay independent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    onDismiss: () -> Unit,
) {
    var draftNickname by remember { mutableStateOf(ProfileConfig.nickname) }
    var draftSignature by remember { mutableStateOf(ProfileConfig.signature) }
    var draftAvatar by remember { mutableStateOf(ProfileConfig.avatarUri) }
    var draftOpacity by remember { mutableFloatStateOf(ProfileConfig.avatarOpacity) }

    // The system cropper (com.android.camera.action.CROP) cannot write back to our output URI on
    // some OEM galleries (e.g. OnePlus), so the avatar crops in-app like the card surfaces do.
    var pendingCrop by remember { mutableStateOf<Uri?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingCrop = it }
    }

    fun commit() {
        val avatar = draftAvatar
        if (avatar == null) {
            ProfileConfig.clearAvatar()
        } else if (avatar != ProfileConfig.avatarUri) {
            // The stored avatar is a file; copying it onto itself would truncate it, so only
            // write when the choice actually changed.
            ProfileConfig.setAvatar(avatar)
        }
        ProfileConfig.setNickname(draftNickname.trim())
        ProfileConfig.setSignature(draftSignature.trim())
        ProfileConfig.setAvatarOpacity(draftOpacity)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = FolkShape.Corner28,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(id = R.string.profile_edit_title),
                style = FolkType.Title,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarOptionTile(
                    selected = draftAvatar == null,
                    label = stringResource(id = R.string.profile_avatar_default),
                    opacity = draftOpacity,
                    onClick = { draftAvatar = null },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                AvatarOptionTile(
                    selected = draftAvatar != null,
                    label = stringResource(id = R.string.profile_avatar_custom),
                    opacity = draftOpacity,
                    onClick = { pickLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, draftAvatar) {
                        value = loadAvatarBitmap(draftAvatar)
                    }
                    val preview = bitmap
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.profile_avatar_opacity),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(draftOpacity * 100).roundToInt()}%",
                    style = FolkType.Numeral,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Slider(
                value = draftOpacity,
                onValueChange = { draftOpacity = it },
                valueRange = ProfileConfig.MIN_AVATAR_OPACITY..1f,
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileTextField(
                value = draftNickname,
                onValueChange = { draftNickname = it.take(ProfileConfig.NICKNAME_MAX_LENGTH) },
                label = stringResource(id = R.string.profile_nickname),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileTextField(
                value = draftSignature,
                onValueChange = { draftSignature = it.take(ProfileConfig.SIGNATURE_MAX_LENGTH) },
                label = stringResource(id = R.string.profile_signature),
                singleLine = false,
                minLines = 2,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        // Text only: the avatar and its opacity have their own controls.
                        draftNickname = ""
                        draftSignature = ""
                    }
                ) {
                    Text(stringResource(id = R.string.profile_restore_default))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.profile_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        commit()
                        onDismiss()
                    },
                    shape = FolkShape.Corner16
                ) {
                    Text(stringResource(id = R.string.profile_save))
                }
            }
        }
    }

    val cropSource = pendingCrop
    if (cropSource != null) {
        SurfaceCropDialog(
            source = cropSource,
            aspect = 1f,
            onDismiss = { pendingCrop = null },
            onCropped = { cropped ->
                draftAvatar = cropped.toString()
                pendingCrop = null
            },
        )
    }
}

/**
 * One of the two avatar choices: a rounded filled tile with a circular preview and, when selected,
 * a check badge in the corner. The whole tile tints when it is the current mode.
 */
@Composable
private fun AvatarOptionTile(
    selected: Boolean,
    label: String,
    opacity: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(FolkShape.Corner24)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .folkPressScale(interactionSource, true)
            .clickable(
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .alpha(opacity.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            style = FolkType.Summary,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/**
 * Rounded, filled text field without the Material underline, so the editor reads as a Folk panel
 * rather than a stock form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    minLines: Int = 1,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        shape = FolkShape.Corner16,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
