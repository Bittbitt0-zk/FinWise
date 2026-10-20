package com.zK.financialguide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** Collect live finances; purchase input and presentation belong to [AffordContent]. */
@Composable
fun AffordScreen(vm: FinanceViewModel, onOpenPlan: () -> Unit) {
    val finances by vm.finances.collectAsStateWithLifecycle()
    val expenses by vm.monthExpenses.collectAsStateWithLifecycle()
    AffordContent(finances.profile, finances.plan, finances.goals,
        expenses.filter { !it.isEssential }.sumOf { it.amount }, finances.calendar.today, onOpenPlan)
}

/**
 * Save the user's inputs and check intent, never a verdict. Checked advice is recalculated
 * from live data so rotation, date changes and budget updates cannot retain stale advice.
 * Plain parameters also let UI tests run without touching the user's database.
 */
@Composable
internal fun AffordContent(
    profile: Profile?, plan: Plan?, goals: List<Goal>, lifestyleSpent: Double,
    today: LocalDate, onOpenPlan: () -> Unit, modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val priceFocus = remember { FocusRequester() }
    val resultPosition = remember { BringIntoViewRequester() }
    val priceValue = price.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.01 }
    val invalidPrice = price.isNotBlank() && priceValue == null
    val budget = remember(profile, plan, lifestyleSpent) {
        if (profile != null && plan != null) FinanceEngine.affordBudget(profile, plan, lifestyleSpent) else null
    }
    val canCheck = profile != null && plan != null && priceValue != null
    val result = remember(checked, profile, plan, goals, lifestyleSpent, today, priceValue, recurring) {
        if (checked && profile != null && plan != null && priceValue != null)
            FinanceEngine.canAfford(profile, plan, priceValue, recurring, lifestyleSpent, goals, today)
        else null
    }
    val check: () -> Unit = { if (canCheck) { focus.clearFocus(); keyboard?.hide(); checked = true } }
    LaunchedEffect(result) { if (result != null) resultPosition.bringIntoView() }

    Column(modifier.fillMaxSize().testTag("afford_content").imePadding()
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenHeader("Can I afford this?", monthLabel(today))
        AffordBudgetSummary(budget, plan, recurring)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your purchase", style = MaterialTheme.typography.titleMedium, letterSpacing = 0.sp)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("One-time", "Monthly").forEachIndexed { index, label ->
                    SegmentedButton(selected = recurring == (index == 1),
                        onClick = { recurring = index == 1; checked = false },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = SegmentedButtonDefaults.itemShape(index, 2), icon = {
                            SegmentedButtonDefaults.Icon(recurring == (index == 1)) {
                                Icon(if (index == 0) Icons.Default.ShoppingCart else Icons.Default.DateRange,
                                    contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }) { Text(label, letterSpacing = 0.sp) }
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it; checked = false },
                label = { Text("Purchase name (optional)") },
                leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { priceFocus.requestFocus() }),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().testTag("afford_name"))
            OutlinedTextField(value = price, onValueChange = { price = it; checked = false },
                label = { Text(if (recurring) "Monthly price" else "Price") }, prefix = { Text("$") },
                suffix = if (recurring) { { Text("/ month") } } else null,
                trailingIcon = if (price.isNotEmpty()) { { IconButton(onClick = { price = ""; checked = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear price")
                } } } else null,
                isError = invalidPrice, supportingText = if (invalidPrice) { { Text("Enter a price of at least $0.01.") } } else null,
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { check() }), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(priceFocus).testTag("afford_price"))
            Button(onClick = check, enabled = canCheck, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("afford_check")) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check affordability", letterSpacing = 0.sp)
            }
        }
        if (result != null && priceValue != null) {
            AffordAdviceCard(name, priceValue, recurring, result, onOpenPlan, Modifier.bringIntoViewRequester(resultPosition))
        } else if (plan != null) AffordCommitments(plan, onOpenPlan)
        Spacer(Modifier.height(4.dp))
    }
}

/** The summary and the verdict consume the same engine-calculated spendable figures. */
@Composable
private fun AffordBudgetSummary(budget: AffordBudget?, plan: Plan?, recurring: Boolean) {
    val deficit = plan != null && plan.cashFlow <= 0
    FinancePanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (deficit) "Monthly cash flow" else if (recurring) "Available each month" else "Available now",
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
            if (budget == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        val available = if (deficit) plan!!.cashFlow else budget?.let {
            it.lifestyleRemaining + if (recurring) it.longTermAvailable else it.extraSavings
        }
        AffordMoneyText(available?.let { money(it) } ?: "...", 28,
            color = if (deficit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AffordBudgetMetric("Lifestyle left", budget?.lifestyleRemaining, "This month's allowance", Modifier.weight(1f))
            AffordBudgetMetric(if (recurring) "Monthly savings" else "Extra savings",
                if (recurring) budget?.longTermAvailable else budget?.extraSavings,
                if (recurring) "Long-term allocation" else "Above emergency target", Modifier.weight(1f))
        }
        if (deficit || (budget?.overspent ?: 0.0) > 0.005) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(if (deficit) "New spending is paused while cash flow is not positive."
                    else "Overspent by ${money(budget!!.overspent)} this month. This is included in the available amounts.",
                    style = MaterialTheme.typography.bodySmall, letterSpacing = 0.sp)
            }
        }
    }
}

@Composable
private fun AffordBudgetMetric(label: String, amount: Double?, caption: String, modifier: Modifier) {
    Column(modifier.heightIn(min = 82.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, letterSpacing = 0.sp)
        AffordMoneyText(amount?.let { money(it) } ?: "...", 20)
        Text(caption, style = MaterialTheme.typography.labelSmall, minLines = 2,
            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
    }
}

/** Text and icons both identify the verdict; neutral surfaces keep the explanation readable. */
@Composable
private fun AffordAdviceCard(name: String, price: Double, recurring: Boolean, result: AffordResult,
    onOpenPlan: () -> Unit, modifier: Modifier) {
    val accent = when (result.verdict) {
        Verdict.YES -> AppGreen
        Verdict.YES_BUT -> AppOrange
        Verdict.NOT_YET -> MaterialTheme.colorScheme.primary
        Verdict.NO -> MaterialTheme.colorScheme.error
    }
    val icon = when (result.verdict) {
        Verdict.YES -> Icons.Default.CheckCircle
        Verdict.YES_BUT -> Icons.Default.Warning
        Verdict.NOT_YET -> Icons.Default.DateRange
        Verdict.NO -> Icons.Default.Close
    }
    val title = when (result.verdict) {
        Verdict.YES -> "Fits your plan"
        Verdict.YES_BUT -> "Consider the trade-off"
        Verdict.NOT_YET -> if (recurring) "Wait for more room" else "Save first"
        Verdict.NO -> "Hold off for now"
    }
    Card(modifier.fillMaxWidth().testTag("afford_result").semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = accent, letterSpacing = 0.sp)
                    result.waitMonths?.let { months -> Text("About $months month${if (months == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, letterSpacing = 0.sp) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name.trim().ifBlank { "This purchase" }, style = MaterialTheme.typography.titleMedium, letterSpacing = 0.sp)
                AffordMoneyText(money(price) + if (recurring) " / month" else "", 24)
            }
            Text(result.reason, style = MaterialTheme.typography.bodyMedium, letterSpacing = 0.sp)
            if (result.details.isNotEmpty()) {
                HorizontalDivider()
                result.details.forEach { detail ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(detail.label, Modifier.weight(0.56f), style = MaterialTheme.typography.bodyMedium, letterSpacing = 0.sp)
                        AffordMoneyText(money(detail.amount) + if (detail.perMonth) "/mo" else "", 16,
                            Modifier.weight(0.44f), textAlign = TextAlign.End)
                    }
                }
            }
            HorizontalDivider()
            AffordPlanLink(onOpenPlan, Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun AffordCommitments(plan: Plan, onOpenPlan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text("Protected in your plan", style = MaterialTheme.typography.labelLarge, letterSpacing = 0.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Emergency target", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, letterSpacing = 0.sp)
            AffordMoneyText(money(plan.emergencyTarget), 14, Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Planned goals", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, letterSpacing = 0.sp)
            AffordMoneyText("${money(plan.goalAllocations.sumOf { it.funded })}/mo", 14, Modifier.weight(1f), textAlign = TextAlign.End)
        }
        AffordPlanLink(onOpenPlan, Modifier.align(Alignment.End))
    }
}

@Composable
private fun AffordPlanLink(onOpenPlan: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onOpenPlan, modifier = modifier) {
        Text("Review monthly plan", letterSpacing = 0.sp)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** Fit complete monetary values rather than truncating financial information in narrow columns. */
@Composable
private fun AffordMoneyText(text: String, maxSize: Int, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface, textAlign: TextAlign = TextAlign.Start) {
    MoneyText(text, modifier, maxSize, color, textAlign, tag = "afford_money")
}
