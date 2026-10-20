/** Record and delete expenses; essential expenses do not use the lifestyle budget. */
package com.zK.financialguide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("d MMM")

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Food" -> MaterialTheme.colorScheme.tertiary
    "Entertainment", "Bills" -> MaterialTheme.colorScheme.secondary
    "Transport", "Shopping", "Education" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun ExpensesScreen(vm: FinanceViewModel) {
    val expenses by vm.monthExpenses.collectAsStateWithLifecycle()
    val finances by vm.finances.collectAsStateWithLifecycle()
    val plan = finances.plan
    val calendar = finances.calendar
    var showDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    val lifestyleSpent = expenses.filter { !it.isEssential }.sumOf { it.amount }
    val essentialSpent = expenses.filter { it.isEssential }.sumOf { it.amount }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)) {
            ScreenHeader("Expenses", monthLabel(calendar.today), actions = {
                AddRecordButton("Add expense", onClick = { showDialog = true })
            })
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    BudgetSummary(plan?.lifestyleBudget, lifestyleSpent, essentialSpent)
                    if (expenses.isNotEmpty()) {
                        Text("Recorded this month", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (expenses.isEmpty()) {
                item { EmptyExpenses() }
            }

            items(expenses, key = { it.id }) { e ->
                ExpenseRow(e, onDelete = { toDelete = e })
            }
        }

    }

    if (showDialog) {
        AddExpenseDialog(
            onDismiss = { showDialog = false },
            onAdd = { amount, category, description, essential ->
                vm.addExpense(amount, category, description, essential)
                showDialog = false
            }
        )
    }

    toDelete?.let { e ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "${money(e.amount)} - ${e.description.ifBlank { e.category }} will be removed.",
            onConfirm = { vm.deleteExpense(e); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

/** Top card: how much of this month's lifestyle budget is left. */
@Composable
private fun BudgetSummary(budget: Double?, spent: Double, essentialSpent: Double) {
    FinancePanel {
        if (budget == null) return@FinancePanel
        val remaining = budget - spent
        val over = remaining < 0
        Text(if (over) "Over lifestyle budget" else "Available this month",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money(kotlin.math.abs(remaining)), maxSize = 28, color = if (over) AppRed else AppBlue,
            tag = "expenses_available")
        ProgressBar(if (budget > 0) (spent / budget).toFloat() else if (spent > 0) 1f else 0f)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FinanceMetric("Lifestyle budget", money(budget), Modifier.weight(1f))
            FinanceMetric("Lifestyle spent", money(spent), Modifier.weight(1f))
        }
        LabelRow("Essential spending", money(essentialSpent))
        if (budget <= 0.005) Text("No lifestyle allocation while cash flow is not positive.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyExpenses() {
    FinanceEmptyState(Icons.AutoMirrored.Filled.List, "No expenses this month")
}

@Composable
fun ExpenseRow(e: Expense, onDelete: () -> Unit) {
    val color = categoryColor(e.category)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Coloured badge with the category's first letter
            Box(
                Modifier.size(36.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(e.category.firstOrNull()?.toString() ?: "?", color = color, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.description.ifBlank { e.category }, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${e.category} • ${formatDate(e.dateMillis)}" + if (e.isEssential) " • Essential" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(money(e.amount), modifier = Modifier.weight(0.38f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete expense")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onAdd: (Double, String, String, Boolean) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var description by remember { mutableStateOf("") }
    var essential by remember { mutableStateOf(false) }
    val amountValue = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Amount", amount) { amount = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = c == category,
                            onClick = {
                                category = c
                                essential = c in ESSENTIAL_CATEGORIES // sensible default, user can change it
                            },
                            label = { Text(c) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Essential expense", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Rent, groceries, transport... doesn't use your lifestyle budget",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = essential, onCheckedChange = { essential = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountValue != null && amountValue > 0,
                onClick = { onAdd(amountValue!!, category, description.trim(), essential) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
