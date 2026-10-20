/** First-run and edit-profile form. Blank savings and debt count as zero. */
package com.zK.financialguide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// Optional fields show blank instead of "0.0"
private fun optionalText(v: Double?): String = if (v == null || v == 0.0) "" else v.toString()

@Composable
fun SetupScreen(initial: Profile?, onBack: (() -> Unit)? = null, onSave: (Profile) -> Unit) {
    val editing = initial != null
    var income by remember { mutableStateOf(initial?.income?.toString() ?: "") }
    var essentials by remember { mutableStateOf(initial?.essentials?.toString() ?: "") }
    var savings by remember { mutableStateOf(optionalText(initial?.savings)) }
    var debt by remember { mutableStateOf(optionalText(initial?.debtBalance)) }
    var minDebt by remember { mutableStateOf(optionalText(initial?.minDebtPayment)) }
    var months by remember { mutableIntStateOf(initial?.emergencyMonths ?: 3) }

    // Required: income, essentials. Optional (blank = 0): savings, debt, minimum debt payment.
    val incomeV = income.toDoubleOrNull()
    val essentialsV = essentials.toDoubleOrNull()
    val savingsV = if (savings.isBlank()) 0.0 else savings.toDoubleOrNull()
    val debtV = if (debt.isBlank()) 0.0 else debt.toDoubleOrNull()
    val hasDebt = (debtV ?: 0.0) > 0
    // Minimum payment only counts when there is a debt
    val minDebtV = if (!hasDebt || minDebt.isBlank()) 0.0 else minDebt.toDoubleOrNull()

    val valid = listOf(incomeV, essentialsV, savingsV, debtV, minDebtV).all { it != null && it >= 0 } &&
            (incomeV ?: 0.0) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader(if (editing) "Your finances" else "FinWise", "Monthly financial profile", onBack)
        Text("Income & commitments", style = MaterialTheme.typography.titleMedium)
        NumberField("Monthly income", income) { income = it }
        NumberField("Monthly essentials", essentials) { essentials = it }
        NumberField("Current savings", savings) { savings = it }
        NumberField("Debt balance (optional)", debt) {
            debt = it
            // No debt means no minimum payment
            if ((it.toDoubleOrNull() ?: 0.0) <= 0) minDebt = ""
        }
        NumberField(
            label = "Minimum monthly payment",
            value = minDebt,
            enabled = hasDebt,
            supportingText = if (hasDebt) null else "Not applicable without debt"
        ) { minDebt = it }

        Text("Emergency fund protection", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(3, 4, 6).forEachIndexed { index, m ->
                SegmentedButton(
                    selected = months == m,
                    onClick = { months = m },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("$m mo") }
            }
        }
        LabelRow("Emergency target", money((essentialsV ?: 0.0) * months), AppBlue)

        Button(
            enabled = valid,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = {
                onSave(
                    Profile(
                        income = incomeV!!, essentials = essentialsV!!, savings = savingsV!!,
                        debtBalance = debtV!!, minDebtPayment = minDebtV!!,
                        emergencyMonths = months
                    )
                )
            }
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (editing) "Save changes" else "Create my plan")
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    enabled: Boolean = true,
    supportingText: String? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,   // a disabled field is shown greyed out automatically
        supportingText = supportingText?.let { { Text(it) } },
        prefix = { Text("$") },
        shape = MaterialTheme.shapes.small,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
