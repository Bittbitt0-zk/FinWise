/** Create, edit and track goals and show whether each is affordable. */
package com.zK.financialguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val dueFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun GoalsScreen(vm: FinanceViewModel) {
    val finances by vm.finances.collectAsStateWithLifecycle()
    val goals = finances.goals
    val calendar = finances.calendar
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var toDelete by remember { mutableStateOf<Goal?>(null) }
    val p = finances.plan

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)) {
            ScreenHeader("Goals", monthLabel(calendar.today), actions = {
                AddRecordButton("Add goal", onClick = { editing = null; showDialog = true })
            })
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (goals.isEmpty()) {
                item { EmptyGoals() }
            } else {
                if (p != null) {
                    item { GoalsSummary(goals, p, calendar.today) }
                }
                items(goals, key = { it.id }) { g ->
                    GoalCard(
                        goal = g,
                        allocation = p?.goalAllocations?.firstOrNull { it.goalId == g.id },
                        today = calendar.today,
                        onEdit = { editing = g; showDialog = true },
                        onDelete = { toDelete = g }
                    )
                }
            }
        }

    }

    if (showDialog) {
        val current = editing
        GoalDialog(
            initial = current,
            today = calendar.today,
            zoneId = calendar.zoneId,
            onDismiss = { showDialog = false },
            onSave = { name, target, saved, deadline ->
                vm.saveGoal(
                    Goal(
                        id = current?.id ?: 0, name = name, target = target,
                        saved = saved, deadlineMillis = deadline
                    ),
                    original = current
                )
                showDialog = false
            }
        )
    }

    toDelete?.let { g ->
        ConfirmDialog(
            title = "Delete goal?",
            message = "\"${g.name}\" and its saved progress will be removed.",
            onConfirm = { vm.deleteGoal(g); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

/** Top card: can the user afford all active goals? */
@Composable
private fun GoalsSummary(goals: List<Goal>, p: Plan, today: LocalDate) {
    val active = goals.count { it.saved < it.target }
    if (active == 0) {
        FinancePanel {
            Text("All of your goals are complete.", color = AppGreen, style = MaterialTheme.typography.titleMedium)
            LabelRow("Monthly contribution", money(0.0), AppBlue)
        }
        return
    }
    val needed = p.goalAllocations.sumOf { it.required }
    val funded = p.goalAllocations.sumOf { it.funded }
    val overdue = goals.count {
        it.saved < it.target && FinanceEngine.deadlineDate(it.deadlineMillis).isBefore(today)
    }
    val (message, color) = when {
        p.tier == Tier.DEFICIT ->
            "Goals cannot be funded until your monthly cash flow is positive." to AppRed
        overdue > 0 ->
            "$overdue goal${if (overdue == 1) " is" else "s are"} overdue. Update the deadline or target." to AppOrange
        p.goalShortfall > 0.005 ->
            ("Your goals need ${money(p.goalShortfall)} more per month than is available, so they cannot " +
                    "all be reached by their deadlines. Consider later deadlines, smaller targets, or lower " +
                    "spending.") to AppOrange
        else -> "All of your goals are affordable within their deadlines." to AppGreen
    }
    FinancePanel {
        Text("$active active goal${if (active == 1) "" else "s"}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText("${money(funded)} / month", maxSize = 28, color = AppBlue, tag = "goals_monthly")
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FinanceMetric("Required monthly", money(needed), Modifier.weight(1f))
            FinanceMetric("Shortfall", money(p.goalShortfall), Modifier.weight(1f))
        }
        Text(message, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyGoals() {
    FinanceEmptyState(Icons.Default.Star, "No goals yet")
}

@Composable
fun GoalCard(
    goal: Goal,
    allocation: GoalAllocation?,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val completed = goal.saved >= goal.target
    val remaining = maxOf(0.0, goal.target - goal.saved)
    val shownSaved = minOf(goal.saved, goal.target)
    val deadline = FinanceEngine.deadlineDate(goal.deadlineMillis)
    val overdue = deadline.isBefore(today)
    val monthsLeft = FinanceEngine.monthsUntil(goal.deadlineMillis, today)
    val progress = if (goal.target > 0) (shownSaved / goal.target).toFloat().coerceIn(0f, 1f) else 1f
    val due = deadline.format(dueFormat)
    val required = allocation?.required ?: (remaining / maxOf(1, monthsLeft))
    val funded = allocation?.funded ?: 0.0

    val (statusText, statusColor) = when {
        completed -> "Completed" to AppGreen
        overdue -> "Overdue" to AppOrange
        deadline == today -> "Due today" to AppOrange
        monthsLeft == 0 -> "Due this month" to AppOrange
        funded >= required - 0.005 -> "On track" to AppGreen
        funded > 0.005 -> "Partly funded" to AppOrange
        else -> "Not funded" to AppRed
    }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    StatusPill(statusText, statusColor)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit goal") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete goal") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FinanceMetric("Saved", money(shownSaved), Modifier.weight(1f), emphasize = true)
                FinanceMetric("Target", money(goal.target), Modifier.weight(1f))
            }
            ProgressBar(progress)
            Text("${(progress * 100).toInt()}% complete", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (completed) {
                Text("Goal reached. No more monthly contribution is needed.", color = AppGreen,
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                val timing = when {
                    overdue -> "Overdue"
                    deadline == today -> "Today"
                    monthsLeft == 0 -> "This month"
                    else -> "$monthsLeft month${if (monthsLeft == 1) "" else "s"} left"
                }
                Text("Due $due  ·  $timing", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LabelRow("Required per month", money(required))
                LabelRow("Planned per month", money(funded), AppBlue)
                Text(
                    when {
                        overdue -> "This deadline has passed. Update it to plan the remaining ${money(remaining)}."
                        funded >= required - 0.005 ->
                            "Affordable: ${money(required)}/month is included in your plan."
                        funded > 0.005 -> {
                            val needed = ceil(remaining / funded - 1e-9).toInt()
                            "Only ${money(funded)}/month is available for this goal. At that rate, about " +
                                    "$needed months are needed; your current deadline is $due."
                        }
                        else -> "No money is available for this goal right now. Higher priorities use " +
                                "your cash flow first."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun GoalDialog(
    initial: Goal?,
    today: LocalDate,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(initial?.target?.toString() ?: "") }
    var saved by remember {
        val initialSaved = initial?.let { minOf(it.saved, it.target) } ?: 0.0
        mutableStateOf(if (initialSaved == 0.0) "" else initialSaved.toString())
    }
    // Retain the original deadline even if the calendar changes while the dialog is open.
    val initialMonths = remember(initial) {
        initial?.let { FinanceEngine.monthsUntil(it.deadlineMillis, today) }
    }
    var months by remember { mutableStateOf(initialMonths?.toString() ?: "") }

    val targetV = target.toDoubleOrNull()
    val savedV = if (saved.isBlank()) 0.0 else saved.toDoubleOrNull()
    val monthsV = months.toIntOrNull()
    val validMonths = monthsV != null &&
            (monthsV >= 1 || (initialMonths == 0 && monthsV == 0))
    val valid = name.isNotBlank() && targetV != null && targetV.isFinite() && targetV > 0 &&
            savedV != null && savedV.isFinite() && savedV >= 0 && savedV <= targetV && validMonths

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Goal name") }, singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField("Target amount", target) { target = it }
                NumberField("Already saved", saved) { saved = it }
                OutlinedTextField(
                    value = months, onValueChange = { months = it },
                    label = { Text("Months until deadline") }, singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val deadline = if (initial != null && monthsV == initialMonths) {
                        initial.deadlineMillis
                    } else {
                        today.plusMonths(monthsV!!.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    }
                    onSave(name.trim(), targetV!!, savedV!!, deadline)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
