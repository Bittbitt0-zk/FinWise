/** Monthly plan in priority order, with the app's assumptions. */
package com.zK.financialguide

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun fundingStatus(funded: Double, required: Double): String = when {
    required <= 0.005 -> "Nothing needed"
    funded >= required - 0.005 -> "Fully funded"
    funded > 0.005 -> "Partly funded"
    else -> "Not funded"
}

@Composable
fun PlanScreen(vm: FinanceViewModel, onBack: () -> Unit) {
    val finances by vm.finances.collectAsStateWithLifecycle()
    val profile = finances.profile
    val p = finances.plan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader("Monthly plan", monthLabel(finances.calendar.today), onBack)

        if (p != null && profile != null) {
            val goalsFunded = p.goalAllocations.sumOf { it.funded }
            val totalPlanned = profile.essentials + profile.minDebtPayment + p.emergencyContribution +
                    goalsFunded + p.lifestyleBudget + p.longTermSavings
            val balance = profile.income - totalPlanned

            PlanSnapshotCard(profile, p, totalPlanned, balance)

            FinanceSection("Cash-flow allocation") {
                val emergencyDetail = when {
                    p.emergencyGap <= 0 -> "Complete; no monthly contribution needed."
                    p.emergencyContribution > 0 ->
                        "Suggested emergency saving; about ${p.emergencyMonthsToComplete} months left."
                    else -> "Paused until cash flow is positive."
                }
                AllocationRow("1", "Emergency fund", money(p.emergencyContribution), emergencyDetail, AppBlue, p.emergencyContribution, p.cashFlow)

                if (p.goalAllocations.isEmpty()) {
                    AllocationRow("2", "Goals", money(0.0), "No active goals this month.", AppGreen, 0.0, p.cashFlow)
                } else {
                    p.goalAllocations.forEachIndexed { index, a ->
                        AllocationRow(
                            "2.${index + 1}",
                            a.name,
                            money(a.funded),
                            "${fundingStatus(a.funded, a.required)}; needs ${money(a.required)}/month.",
                            if (a.funded >= a.required - 0.005) AppGreen else AppOrange,
                            a.funded,
                            p.cashFlow
                        )
                    }
                }

                AllocationRow(
                    "3",
                    "Lifestyle spending",
                    money(p.lifestyleBudget),
                    "Protected allowance plus 20% of what is left after your emergency fund and goals.",
                    MaterialTheme.colorScheme.primary,
                    p.lifestyleBudget,
                    p.cashFlow
                )
                AllocationRow(
                    "4",
                    "Long-term savings",
                    money(p.longTermSavings),
                    if (p.longTermSavings > 0.005) "Money left after higher priorities."
                    else "Nothing left after higher priorities.",
                    MaterialTheme.colorScheme.secondary,
                    p.longTermSavings,
                    p.cashFlow
                )
            }

            FinanceSection("Fixed commitments") {
                CommitmentRow("Essential expenses", money(profile.essentials), "Housing, food, transport and required basics.")
                CommitmentRow(
                    "Minimum debt payment",
                    money(profile.minDebtPayment),
                    if (profile.minDebtPayment > 0) "Paid before saving decisions." else "No required debt payment."
                )
            }

            FinanceSection("Plan balance") {
                LabelRow("Total planned", money(totalPlanned))
                LabelRow("Your income", money(profile.income))
                LabelRow(if (balance >= -0.005) "Unassigned" else "Short by", money(kotlin.math.abs(balance)))
                Text(
                    when {
                        p.cashFlow < 0 -> "Your fixed commitments exceed income by ${money(-p.cashFlow)}."
                        kotlin.math.abs(balance) <= 0.005 -> "Every dollar is assigned to a clear monthly job."
                        balance > 0 -> "This amount is still free after the current plan."
                        else -> "The plan is over-allocated; reduce goals or spending."
                    },
                    color = if (balance < -0.005 || p.cashFlow < 0) AppRed else AppGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            FinanceSection("Why this plan") {
                p.explanations.forEachIndexed { index, explanation ->
                    ExplanationRow(index + 1, explanation)
                }
            }

            FinanceSection("Rules used") {
                AssumptionRow("Cash flow", "Strong is above 40%, moderate is 20-40%, limited is 0-20%, and 0% or below is a deficit.")
                AssumptionRow("Emergency", "The suggested amount depends on cash flow and never takes more than 60% of money left after base lifestyle allowance.")
                AssumptionRow("Lifestyle", "Starts from 10% of income, capped when cash flow is tight, then gets 20% of leftover money.")
                AssumptionRow("Goals", "Active goals are funded by deadline order, earliest first.")
            }
        }
    }
}

@Composable
private fun PlanSnapshotCard(profile: Profile, plan: Plan, totalPlanned: Double, balance: Double) {
    FinancePanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Available cash flow", style = MaterialTheme.typography.labelLarge)
                    MoneyText(money(plan.cashFlow), maxSize = 28, color = if (plan.cashFlow < 0) AppRed else AppBlue,
                        tag = "plan_cashflow")
                }
                StatusPill(
                    when (plan.tier) {
                        Tier.STRONG -> "Strong"
                        Tier.MODERATE -> "Moderate"
                        Tier.LIMITED -> "Limited"
                        Tier.DEFICIT -> "Deficit"
                    },
                    when (plan.tier) {
                        Tier.STRONG -> AppGreen
                        Tier.MODERATE -> AppBlue
                        Tier.LIMITED -> AppOrange
                        Tier.DEFICIT -> AppRed
                    }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FinanceMetric("Income", money(profile.income), Modifier.weight(1f))
                FinanceMetric("Planned", money(totalPlanned), Modifier.weight(1f))
                FinanceMetric(if (balance >= -0.005) "Left" else "Short", money(kotlin.math.abs(balance)), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AllocationRow(
    number: String,
    title: String,
    amount: String,
    detail: String,
    color: Color,
    value: Double,
    total: Double
) {
    val progress = if (total > 0.005) (value / total).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                contentColor = color,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
                    MoneyText(amount, modifier = Modifier.weight(0.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)
                ProgressBar(progress)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun CommitmentRow(title: String, amount: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
            MoneyText(amount, modifier = Modifier.weight(0.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExplanationRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AssumptionRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
    }
}
