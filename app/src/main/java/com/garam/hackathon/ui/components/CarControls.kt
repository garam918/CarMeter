package com.garam.hackathon.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The minimum touch target for in-car controls. */
val TouchTarget = 64.dp

/** Corners close to the squarer Pleos system controls. */
val ControlShape = RoundedCornerShape(12.dp)

private val ButtonPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        contentPadding = ButtonPadding,
        modifier = modifier.heightIn(min = TouchTarget),
    ) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.heightIn(min = TouchTarget),
    ) { Text(text) }
}

/** For actions that delete: outlined in the error colour, never filled. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.38f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = TouchTarget),
    ) { Text(text) }
}

/** A square icon button, such as the previous and next period arrows. */
@Composable
fun NavIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(TouchTarget),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(32.dp))
    }
}

/** Mutually exclusive options in one bar, the selected one filled like the Pleos system tabs. */
@Composable
fun <T> SegmentedTabs(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(ControlShape)
            .border(1.5.dp, colors.outline, ControlShape)
            .selectableGroup(),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = TouchTarget)
                    .widthIn(min = 112.dp)
                    .background(if (isSelected) colors.primary else colors.surface)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(horizontal = 24.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) colors.onPrimary else colors.onSurface)
            }
        }
    }
}

enum class BadgeTone { NEUTRAL, ACCENT, WARM }

/** A short status label. */
@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.NEUTRAL) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (tone) {
        BadgeTone.NEUTRAL -> colors.surfaceVariant to colors.onSurfaceVariant
        BadgeTone.ACCENT -> colors.primaryContainer to colors.onPrimaryContainer
        BadgeTone.WARM -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** A titled block of content on a quiet background. */
@Composable
fun Section(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/** A thin bar scaled to [fraction] of the available width. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier, thickness: Dp = 16.dp) {
    Box(modifier.fillMaxWidth().height(thickness)) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(thickness)
                    .background(color, RoundedCornerShape(thickness / 2)),
            )
        }
    }
}
