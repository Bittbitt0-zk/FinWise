/** Shared presentation only. Financial calculations remain in FinanceEngine. */
package com.zK.financialguide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Semantic accents remain legible on both theme surfaces; progress always uses primary.
val AppGreen: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
        Color(0xFF81C995) else Color(0xFF246B3D)
val AppOrange: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
val AppRed: Color
    @Composable get() = MaterialTheme.colorScheme.error
val AppBlue: Color
    @Composable get() = MaterialTheme.colorScheme.primary

fun money(x: Double): String = if (x < 0) "-\$%,.2f".format(-x) else "\$%,.2f".format(x)

private val screenMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
fun monthLabel(today: LocalDate): String = today.format(screenMonthFormat)

/** The same heading, date/subtitle and optional navigation on every screen. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** A fixed touch target for record creation, pinned with Expenses/Goals headings. */
@Composable
fun AddRecordButton(description: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.Default.Add, contentDescription = description)
    }
}

/** A framed financial summary, not a wrapper for whole page sections or other cards. */
@Composable
fun FinancePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Ordinary page sections stay unframed, with consistent spacing and a quiet divider. */
@Composable
fun FinanceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Keep complete amounts readable on narrow layouts instead of truncating financial digits. */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    maxSize: Int = 16,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    tag: String = "money_value"
) {
    Text(text, modifier.fillMaxWidth().testTag(tag), color = color,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = maxSize.sp, stepSize = 1.sp),
        maxLines = 1, lineHeight = (maxSize + 8).sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp, textAlign = textAlign)
}

@Composable
fun FinanceMetric(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(value, maxSize = 20, color = if (emphasize) AppBlue else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LabelRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
        MoneyText(value, modifier = Modifier.weight(0.42f), color = valueColor, textAlign = TextAlign.End)
    }
}

/** All determinate progress lines use the same brand accent, including completed goals. */
@Composable
fun ProgressBar(progress: Float) {
    val color = MaterialTheme.colorScheme.primary
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color, trackColor = color.copy(alpha = 0.16f), drawStopIndicator = {})
}

/** Status always has a written label as well as a semantic colour. */
@Composable
fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun FinanceEmptyState(icon: ImageVector, title: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = AppBlue, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
