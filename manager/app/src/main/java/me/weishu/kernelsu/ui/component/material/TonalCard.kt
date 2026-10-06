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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    val haptic = LocalHapticFeedback.current
    when {
        onLongClick != null -> Card(
            modifier = modifier
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
                ),
            colors = colors,
            shape = shape,
        ) { content() }

        onClick != null -> Card(
            modifier = modifier
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
                ),
            colors = colors,
            shape = shape,
        ) { content() }

        else -> Card(
            modifier = modifier,
            colors = colors,
            shape = shape,
        ) { content() }
    }
}
