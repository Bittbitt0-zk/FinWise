/** Main screen: status, cash flow, emergency fund, allocation and explanations. */
package com.zK.financialguide

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun percent(x: Double): String = "%.1f%%".format(x)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(vm: FinanceViewModel, onOpenPlan: () -> Unit, onOpenProfile: () -> Unit) {
    val finances by vm.finances.collectAsStateWithLifecycle()
    val profile = finances.profile
    val expenses by vm.monthExpenses.collectAsStateWithLifecycle()
    var showAddSavings by remember { mutableStateOf(false) }
    val p = finances.plan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader("FinWise", monthLabel(finances.calendar.today), actions = {
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Default.Settings, contentDescription = "Profile")
            }
        })

        if (p != null && profile != null) {
            // --- Status pills (small, not a big banner) ---
            val statusColor = when (p.status) {
                Status.HEALTHY -> AppGreen
                Status.NEEDS_ATTENTION -> AppOrange
                Status.AT_RISK -> AppRed
            }
            val statusText = when (p.status) {
                Status.HEALTHY -> "Healthy"
                Status.NEEDS_ATTENTION -> "Needs attention"
                Status.AT_RISK -> "At risk"
            }
            val tierColor = when (p.tier) {
                Tier.STRONG -> AppGreen
                Tier.MODERATE -> AppBlue
                Tier.LIMITED -> AppOrange
                Tier.DEFICIT -> AppRed
            }
            val tierText = when (p.tier) {
                Tier.STRONG -> "Strong cash flow"
                Tier.MODERATE -> "Moderate cash flow"
                Tier.LIMITED -> "Limited cash flow"
                Tier.DEFICIT -> "Cash flow deficit"
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(statusText, statusColor)
                StatusPill(tierText, tierColor)
            }

            // Only a deficit gets a loud banner
            if (p.status == Status.AT_RISK) {
                FinancePanel {
                    Text("Action needed", style = MaterialTheme.typography.titleMedium, color = AppRed)
                    Text(p.explanations.firstOrNull() ?: "")
                }
            }

            // --- Monthly overview ---
            FinancePanel {
                Text("Available cash flow", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(money(p.cashFlow), maxSize = 28, color = if (p.cashFlow < 0) AppRed else AppBlue,
                    tag = "home_cashflow")
                HorizontalDivider()
                LabelRow("Income", money(profile.income))
                LabelRow("Essential expenses", money(profile.essentials))
                LabelRow("Minimum debt payment", money(profile.minDebtPayment))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FinanceMetric("Essentials", percent(p.essentialRatio), Modifier.weight(1f))
                    FinanceMetric("Debt", percent(p.debtRatio), Modifier.weight(1f))
                    FinanceMetric("Cash flow", percent(p.cashFlowRatio), Modifier.weight(1f))
                }
            }


            // --- Emergency fund: shared card background with a status accent ---
            EmergencyFundCard(profile, p, onAddSavings = { showAddSavings = true })

            // --- Where the cash flow goes ---
            FinanceSection("Monthly allocation") {
                LabelRow("Emergency fund", money(p.emergencyContribution))
                LabelRow("Goals", money(p.goalAllocations.sumOf { it.funded }))
                LabelRow("Lifestyle spending", money(p.lifestyleBudget), AppBlue)
                LabelRow("Long-term savings", money(p.longTermSavings), AppBlue)
                if (p.goalShortfall > 0.005) {
                    Text(
                        "Your goals need ${money(p.goalShortfall)} more per month than is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // --- Why this plan ---
            FinanceSection("Why this plan") {
                p.explanations.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }

            // --- Lifestyle budget vs spending ---
            FinanceSection("Lifestyle this month") {
                val spent = expenses.filter { !it.isEssential }.sumOf { it.amount }
                val remaining = p.lifestyleBudget - spent
                val progress = if (p.lifestyleBudget > 0)
                    (spent / p.lifestyleBudget).toFloat().coerceIn(0f, 1f) else if (spent > 0) 1f else 0f
                ProgressBar(progress)
                LabelRow("Budget", money(p.lifestyleBudget))
                LabelRow("Spent", money(spent))
                LabelRow(
                    if (remaining >= 0) "Remaining" else "Over budget by",
                    money(kotlin.math.abs(remaining)),
                    if (remaining < 0) AppRed else AppBlue
                )
            }

            // --- Essential spending check ---
            FinanceSection("Essential spending") {
                val essentialSpent = expenses.filter { it.isEssential }.sumOf { it.amount }
                val over = essentialSpent > profile.essentials
                val progress = if (profile.essentials > 0)
                    (essentialSpent / profile.essentials).toFloat().coerceIn(0f, 1f) else 0f
                ProgressBar(progress)
                LabelRow("Planned essentials", money(profile.essentials))
                LabelRow("Recorded so far", money(essentialSpent))
                if (over) {
                    Text(
                        "Your recorded essential spending is higher than your profile amount. " +
                                "Review your planned essential expenses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppOrange
                    )
                }
            }

            Button(onClick = onOpenPlan, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("View monthly plan")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
            ReminderCard()
        }
    }

    if (showAddSavings) {
        EmergencySavingsDialog(
            emergencyTarget = p?.emergencyTarget ?: 0.0,
            onDismiss = { showAddSavings = false },
            onAdd = { amount -> vm.addToSavings(amount); showAddSavings = false }
        )
    }
}

@Composable
private fun EmergencyFundCard(profile: Profile, plan: Plan, onAddSavings: () -> Unit) {
    val complete = plan.emergencyGap <= 0
    val emergencySaved = minOf(profile.savings, plan.emergencyTarget)
    val extraSavings = maxOf(0.0, profile.savings - plan.emergencyTarget)
    val progress = if (plan.emergencyTarget > 0)
        (emergencySaved / plan.emergencyTarget).toFloat().coerceIn(0f, 1f) else 1f
    val progressText = "${(progress * 100).toInt()}%"
    val accent = if (complete) AppGreen else if (plan.tier == Tier.DEFICIT) AppRed else AppBlue

    FinancePanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Emergency fund", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${profile.emergencyMonths} months of essential expenses",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                StatusPill(if (complete) "Ready" else "Building", accent)
            }

            MoneyText("${money(emergencySaved)} saved", maxSize = 24, color = AppBlue, tag = "emergency_saved")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { ProgressBar(progress) }
                Text(progressText, style = MaterialTheme.typography.labelLarge)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceMetric("Target", money(plan.emergencyTarget), Modifier.weight(1f))
                FinanceMetric(
                    if (complete) "Extra" else "Gap",
                    money(if (complete) extraSavings else plan.emergencyGap),
                    Modifier.weight(1f)
                )
                FinanceMetric(
                    "Monthly",
                    if (plan.emergencyContribution > 0.005) money(plan.emergencyContribution) else money(0.0),
                    Modifier.weight(1f)
                )
            }

            Text(
                when {
                    complete && extraSavings > 0.005 ->
                        "Fully funded. ${money(extraSavings)} sits above the emergency target."
                    complete ->
                        "Fully funded. Your monthly plan no longer allocates money here."
                    plan.emergencyMonthsToComplete != null ->
                        "Saving ${money(plan.emergencyContribution)}/month covers the remaining amount in about " +
                                "${plan.emergencyMonthsToComplete} month${if (plan.emergencyMonthsToComplete == 1) "" else "s"}."
                    else ->
                        "No money can go here until your cash flow is positive."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAddSavings) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add savings")
                }
            }
        }
    }
}

@Composable
private fun EmergencySavingsDialog(
    emergencyTarget: Double,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val amountV = amount.toDoubleOrNull()?.takeIf { it.isFinite() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add emergency savings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This manually updates your savings balance. The first ${money(emergencyTarget)} " +
                            "counts as emergency savings."
                )
                NumberField("Amount", amount) { amount = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountV != null && amountV > 0,
                onClick = { onAdd(amountV!!) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

