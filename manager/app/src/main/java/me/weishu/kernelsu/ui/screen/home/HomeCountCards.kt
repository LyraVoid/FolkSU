package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.FolkType

/** The axis a host lays the two count cards along. */
internal enum class CountCardLayout {
    /** Side by side, each taking half the width. */
    Horizontal,

    /** Stacked, each taking half the height of the host. */
    Vertical,
}

/**
 * Which of the two lines carries the card.
 *
 * [Label] reads as a name with a number under it, [Value] as a small caption over a number, so a
 * host can pick the one that suits the size it gives the card.
 */
internal enum class CountCardEmphasis {
    Label,
    Value,
}

internal object CountCardDefaults {
    val ContentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    val IconSize = 20.dp
    val IconTextSpacing = 16.dp
    val PairSpacing = 16.dp
}

/**
 * The superuser and module counts as one pair, so every home layout shows the same two cards and
 * differs only in what it hands over: the axis, the gap between the cards, the room inside them and
 * which of the two lines carries the card.
 */
@Composable
internal fun CountCardPair(
    superuserCount: Int,
    moduleEnabledCount: Int,
    onOpenSuperUser: () -> Unit,
    onOpenModule: () -> Unit,
    layout: CountCardLayout,
    modifier: Modifier = Modifier,
    pairSpacing: Dp = CountCardDefaults.PairSpacing,
    contentPadding: PaddingValues = CountCardDefaults.ContentPadding,
    emphasis: CountCardEmphasis = CountCardEmphasis.Label,
) {
    @Composable
    fun card(modifier: Modifier, icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
        CountCard(
            icon = icon,
            label = label,
            count = count,
            onClick = onClick,
            modifier = modifier,
            contentPadding = contentPadding,
            emphasis = emphasis,
        )
    }

    val superuserLabel = stringResource(R.string.superuser)
    val moduleLabel = stringResource(R.string.module)

    when (layout) {
        CountCardLayout.Horizontal -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(pairSpacing),
        ) {
            card(Modifier.weight(1f), Icons.Outlined.Person, superuserLabel, superuserCount, onOpenSuperUser)
            card(Modifier.weight(1f), Icons.Outlined.Extension, moduleLabel, moduleEnabledCount, onOpenModule)
        }

        CountCardLayout.Vertical -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(pairSpacing),
        ) {
            card(Modifier.fillMaxWidth().weight(1f), Icons.Outlined.Person, superuserLabel, superuserCount, onOpenSuperUser)
            card(Modifier.fillMaxWidth().weight(1f), Icons.Outlined.Extension, moduleLabel, moduleEnabledCount, onOpenModule)
        }
    }
}

/** One tappable count card: an icon, a name and its number along the leading edge. */
@Composable
internal fun CountCard(
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CountCardDefaults.ContentPadding,
    emphasis: CountCardEmphasis = CountCardEmphasis.Label,
) {
    val labelStyle = when (emphasis) {
        CountCardEmphasis.Label -> FolkType.Summary
        CountCardEmphasis.Value -> FolkType.Caption
    }
    val labelColor = when (emphasis) {
        CountCardEmphasis.Label -> MaterialTheme.colorScheme.onSurface
        CountCardEmphasis.Value -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val valueStyle = when (emphasis) {
        CountCardEmphasis.Label -> FolkType.Numeral
        CountCardEmphasis.Value -> FolkType.Title
    }
    val valueColor = when (emphasis) {
        CountCardEmphasis.Label -> MaterialTheme.colorScheme.outline
        CountCardEmphasis.Value -> MaterialTheme.colorScheme.onSurface
    }

    HomeCard(modifier = modifier, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(CountCardDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(CountCardDefaults.IconTextSpacing))
                Column {
                    Text(
                        text = label,
                        style = labelStyle,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = count.toString(),
                        style = valueStyle,
                        color = valueColor
                    )
                }
            }
        }
    }
}
