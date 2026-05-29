package com.mokie.timelogdemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Page large title block — used at the top of each tab.
@Composable
fun PageTitle(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Section header used inside a screen body, restrained.
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

// iOS inset-grouped list container. Children rows should not have horizontal padding.
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        content()
    }
}

// A list row inside an InsetGroup — single tap target, optional trailing.
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    primary: String,
    secondary: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(modifier = Modifier.padding(end = 12.dp)) { leading() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 12.dp)) { trailing() }
        }
    }
}

// Thin horizontal divider matching inset-row inset.
@Composable
fun RowDivider(insetStart: Dp = 16.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        Spacer(Modifier.width(insetStart))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

// Lightweight empty/placeholder state.
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Thin progress bar used in Review.
@Composable
fun ThinBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(height)
                    .background(color)
            )
        }
    }
}

// Tabular numbers feature string — applied via TextStyle.fontFeatureSettings.
const val TabularNumFeature = "tnum 1, zero 1"

/** Word joiner after each colon so line breaking cannot split HH:MM:SS at `:`. */
private fun nonBreakingTimerText(hms: String): String =
    hms.replace(":", ":\u2060")

private val timerLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

private fun timerTextStyle(base: TextStyle, fontSize: TextUnit): TextStyle {
    return base.copy(
        fontFeatureSettings = TabularNumFeature,
        fontSize = fontSize,
        lineHeight = fontSize,
        lineHeightStyle = timerLineHeightStyle,
        letterSpacing = (fontSize.value * -0.021f).sp
    )
}

/**
 * Large monospace timer that scales down on narrow screens so HH:MM:SS never wraps.
 * Outer slot height is capped so layout cannot allocate a second line.
 */
@Composable
fun TabularTimerText(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp
) {
    val base = MaterialTheme.typography.displayLarge
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val displayText = remember(text) { nonBreakingTimerText(text) }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val pad = when {
            maxWidth < 340.dp -> 12.dp
            maxWidth < 400.dp -> 16.dp
            else -> horizontalPadding
        }
        val maxWidthPx = with(density) { (maxWidth - pad * 2).toPx().coerceAtLeast(1f) }
        val maxFontSize = when {
            maxWidth < 340.dp -> 44.sp
            maxWidth < 400.dp -> 56.sp
            else -> base.fontSize
        }
        val measuredSize = measureTimerFontSize(
            text = displayText,
            maxWidthPx = maxWidthPx,
            maxFontSize = maxFontSize,
            base = base,
            textMeasurer = textMeasurer
        )
        var fontSize by remember(displayText, maxWidthPx, measuredSize) {
            mutableStateOf(measuredSize)
        }
        LaunchedEffect(displayText, maxWidthPx, measuredSize) {
            fontSize = measuredSize
        }
        val slotHeight = (fontSize.value * 1.1f).coerceIn(44f, 80f).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(slotHeight)
                .clipToBounds()
                .padding(horizontal = pad),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = timerTextStyle(base, fontSize),
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { layout ->
                    if (
                        (layout.didOverflowWidth || layout.lineCount > 1) &&
                        fontSize.value > 20f
                    ) {
                        fontSize = (fontSize.value - 1f).sp
                    }
                }
            )
        }
    }
}

private fun measureTimerFontSize(
    text: String,
    maxWidthPx: Float,
    maxFontSize: TextUnit,
    base: TextStyle,
    textMeasurer: TextMeasurer
): TextUnit {
    if (maxWidthPx <= 0f) return maxFontSize
    val cap = minOf(maxFontSize.value, base.fontSize.value)
    var lo = 20f
    var hi = cap
    var best = lo
    while (lo <= hi) {
        val mid = (lo + hi) / 2f
        val style = timerTextStyle(base, mid.sp)
        val width = textMeasurer.measure(
            text = text,
            style = style,
            softWrap = false,
            overflow = TextOverflow.Clip,
            maxLines = 1,
            constraints = Constraints(maxWidth = Int.MAX_VALUE)
        ).size.width
        if (width <= maxWidthPx * 0.92f) {
            best = mid
            lo = mid + 0.25f
        } else {
            hi = mid - 0.25f
        }
    }
    return best.sp
}
