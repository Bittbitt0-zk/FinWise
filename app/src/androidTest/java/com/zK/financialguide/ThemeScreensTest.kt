package com.zK.financialguide

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zK.financialguide.ui.theme.FinWiseTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Real navigation with isolated fixtures; screenshots never require changing user records. */
@RunWith(AndroidJUnit4::class)
class ThemeScreensTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: FinanceViewModel
    private lateinit var store: ViewModelStore
    private val today = LocalDate.of(2026, 10, 10)
    private val zone = ZoneId.systemDefault()
    private val profile = Profile(income = 3400.0, essentials = 1500.0, savings = 2000.0,
        debtBalance = 0.0, minDebtPayment = 0.0)
    private fun millis(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Before fun openViewModel(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).build()
        db.dao().saveProfile(profile)
        db.dao().saveGoal(Goal(id = 1, name = "Laptop and accessories", target = 1500.0, saved = 300.0,
            deadlineMillis = millis(today.plusMonths(5))))
        db.dao().saveGoal(Goal(id = 2, name = "Camera", target = 300.0, saved = 300.0,
            deadlineMillis = millis(today.plusMonths(2))))
        db.dao().addExpense(Expense(amount = 180.0, category = "Shopping", description = "Clothing and shoes",
            dateMillis = millis(today)))
        db.dao().addExpense(Expense(amount = 120.0, category = "Food", description = "Groceries",
            isEssential = true, dateMillis = millis(today)))
        db.advanceMonthlyGoals(today)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store = ViewModelStore()
            vm = FinanceViewModel(app, db, { CalendarState(today, zone) }, null)
            store.put("theme-test", vm)
        }
        withTimeout(5000) { vm.finances.first { it.loaded } }
    }

    @After fun closeViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    private fun show(dark: Boolean = false, narrow: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            FinWiseTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(if (narrow) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                                AppRoot(vm)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tab(name: String) {
        compose.onNodeWithContentDescription(name, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        if (name == "Expenses") {
            compose.waitUntil(5000) { vm.monthExpenses.value.size == 2 }
            compose.waitForIdle()
        }
    }

    private fun layouts(tag: String): List<TextLayoutResult> {
        val result = mutableListOf<TextLayoutResult>()
        val nodes = compose.onAllNodesWithTag(tag)
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        }
        return result
    }

    private fun checkMoneyLayouts() {
        listOf("money_value", "expenses_available", "home_cashflow", "emergency_saved",
            "goals_monthly", "plan_cashflow", "afford_money").forEach { tag ->
            layouts(tag).forEach { assertFalse("Clipped monetary text: $tag", it.hasVisualOverflow) }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "theme-ui-checks").apply { mkdirs() }
        File(directory, name).outputStream().use {
            // Capture Android's composed windows, including dialogs and their dimmed background.
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            checkNotNull(screenshot).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun expensesUseTheExactSampledBlueAndCorrectRemainingBudget() {
        show()
        tab("Expenses")
        val expected = money(vm.finances.value.plan!!.lifestyleBudget - 180.0)
        compose.onNodeWithTag("expenses_available").assertIsDisplayed()
        compose.onNodeWithText(expected).assertIsDisplayed()
        assertEquals(Color(0xFF505D87), layouts("expenses_available").single().layoutInput.style.color)
        checkMoneyLayouts()
        capture("expenses.png")
    }

    @Test fun allTabsShareTheLightThemeAndRemainUsable() {
        show()
        capture("home.png")
        checkMoneyLayouts()
        listOf("Expenses", "Goals", "Afford?", "Learn").forEach { name ->
            tab(name)
            checkMoneyLayouts()
            capture("${name.removeSuffix("?").lowercase()}.png")
        }
        compose.onNodeWithText("How FinWise Builds Your Plan").assertIsDisplayed()
    }

    @Test fun narrowScreensWithLargeTextKeepFinancialValuesReadable() {
        show(narrow = true, fontScale = 1.4f)
        checkMoneyLayouts()
        capture("narrow-home.png")
        listOf("Expenses", "Goals", "Afford?", "Learn").forEach { name ->
            tab(name)
            checkMoneyLayouts()
            capture("narrow-${name.removeSuffix("?").lowercase()}.png")
        }
    }

    @Test fun darkThemeUsesReadableAccentsAcrossScreens() {
        show(dark = true)
        capture("dark-home.png")
        listOf("Expenses", "Goals", "Afford?", "Learn").forEach { name ->
            tab(name)
            checkMoneyLayouts()
            capture("dark-${name.removeSuffix("?").lowercase()}.png")
        }
    }

    @Test fun monthlyPlanHasWorkingBackNavigation() {
        show()
        compose.onNodeWithText("View monthly plan").performScrollTo().performClick()
        compose.onNodeWithText("Monthly plan").assertIsDisplayed()
        checkMoneyLayouts()
        capture("plan.png")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("FinWise").performScrollTo().assertIsDisplayed()
    }

    @Test fun profileRetainsItsInputsAndBackNavigation() {
        show()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Your finances").assertIsDisplayed()
        compose.onNodeWithText("2000.0").assertIsDisplayed()
        capture("profile.png")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("FinWise").assertIsDisplayed()
        assertEquals(profile, vm.finances.value.profile)
    }

    @Test fun lessonDisclosureExpandsAndCollapses() {
        show()
        tab("Learn")
        compose.onNodeWithText("How FinWise Builds Your Plan").performClick()
        capture("lesson.png")
        compose.onNodeWithText("How FinWise Builds Your Plan").performClick()
        compose.onNodeWithText("Emergency Funds").assertIsDisplayed()
    }

    @Test fun dialogsRemainAccessibleWithoutWritingFinancialRecords() {
        show(narrow = true, fontScale = 1.4f)
        tab("Expenses")
        compose.onNodeWithContentDescription("Add expense").performClick()
        compose.onNodeWithText("Amount").performTextReplacement("50")
        capture("expense-dialog.png")
        compose.onNodeWithText("Cancel").performClick()
        tab("Goals")
        compose.onNodeWithContentDescription("Add goal").performClick()
        compose.onNodeWithText("Goal name").performTextReplacement("New target")
        capture("goal-dialog.png")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(2, vm.finances.value.goals.size)
        assertEquals(profile, vm.finances.value.profile)
    }

    @Test fun firstRunProfileAndEmergencyProtectionStillWork() {
        var saved: Profile? = null
        compose.setContent {
            FinWiseTheme { Surface { SetupScreen(initial = null, onSave = { saved = it }) } }
        }
        compose.onNodeWithText("Monthly income").performTextReplacement("3400")
        compose.onNodeWithText("Monthly essentials").performTextReplacement("1500")
        compose.onNodeWithText("6 mo").performScrollTo().performClick()
        compose.onNodeWithText("$9,000.00").performScrollTo().assertIsDisplayed()
        capture("setup.png")
        compose.onNodeWithText("Create my plan").performScrollTo().performClick()
        val created = saved!!
        assertEquals(6, created.emergencyMonths)
        assertEquals(3400.0, created.income, 0.01)
        assertEquals(0.0, created.savings, 0.01)
    }
}
