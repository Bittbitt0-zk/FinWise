package com.zK.financialguide

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zK.financialguide.ui.theme.FinWiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Interaction and layout tests render fixture data; no financial records are changed. */
@RunWith(AndroidJUnit4::class)
class AffordScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 10)
    private val profile = Profile(income = 3400.0, essentials = 1500.0, savings = 5000.0,
        debtBalance = 0.0, minDebtPayment = 0.0)
    private val plan = FinanceEngine.calculate(profile, emptyList(), today)

    private fun show(dark: Boolean = false, narrow: Boolean = false, fontScale: Float = 1f, onOpenPlan: () -> Unit = {}) {
        compose.setContent {
            FinWiseTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            AffordContent(profile, plan, emptyList(), 0.0, today, onOpenPlan,
                                if (narrow) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    private fun check(price: String, name: String = "Laptop") {
        compose.onNodeWithTag("afford_name").performScrollTo().performTextReplacement(name)
        compose.onNodeWithTag("afford_price").performScrollTo().performTextReplacement(price)
        compose.onNodeWithTag("afford_check").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.getExternalFilesDir(null), "afford-ui-checks").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun oneTimeReceiptAndPlanNavigationWork() {
        var opened = 0
        show(onOpenPlan = { opened++ })
        check("1000")
        compose.onNodeWithText("Fits your plan").assertIsDisplayed()
        compose.onNodeWithText("From extra savings").assertIsDisplayed()
        capture("one-time.png")
        compose.onNodeWithText("Review monthly plan").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun monthlyModeShowsSavingsTradeOff() {
        show()
        compose.onNodeWithText("Monthly").performClick()
        check("1500", "Gym and transport")
        compose.onNodeWithText("Consider the trade-off").assertIsDisplayed()
        compose.onNodeWithText("Long-term savings left").assertIsDisplayed()
        compose.onNodeWithText("$400.00/mo").assertIsDisplayed()
        capture("monthly.png")
    }

    @Test fun invalidPriceIsRejectedAndClearRemovesOldAdvice() {
        show()
        compose.onNodeWithTag("afford_check").assertIsNotEnabled()
        compose.onNodeWithTag("afford_price").performTextReplacement("1e309")
        compose.onNodeWithText("Enter a price of at least $0.01.").assertIsDisplayed()
        compose.onNodeWithTag("afford_check").assertIsNotEnabled()
        check("50")
        compose.onNodeWithTag("afford_price").performScrollTo()
        compose.onNodeWithContentDescription("Clear price").performClick()
        compose.onNodeWithTag("afford_result").assertDoesNotExist()
        compose.onNodeWithTag("afford_check").assertIsNotEnabled()
    }

    @Test fun editingPurchaseClearsPreviousResult() {
        show()
        check("50")
        compose.onNodeWithTag("afford_price").performScrollTo().performTextReplacement("20000")
        compose.onNodeWithTag("afford_result").assertDoesNotExist()
        compose.onNodeWithTag("afford_check").performScrollTo().performClick()
        compose.onNodeWithText("Hold off for now").assertIsDisplayed()
    }

    @Test fun checkedAdviceUpdatesWhenSpendingChanges() {
        val spent = mutableStateOf(0.0)
        compose.setContent {
            FinWiseTheme {
                Surface { AffordContent(profile, plan, emptyList(), spent.value, today, {}) }
            }
        }
        compose.onNodeWithText("Monthly").performClick()
        check("100")
        compose.onNodeWithText("Fits your plan").assertIsDisplayed()
        compose.runOnIdle { spent.value = 2000.0 }
        compose.onNodeWithText("Hold off for now").assertIsDisplayed()
    }

    @Test fun narrowLayoutAndLargeTextKeepMoneyReadable() {
        show(narrow = true, fontScale = 1.4f)
        check("1500", "Laptop and accessories")
        compose.onNodeWithText("Save first").assertIsDisplayed()
        val amounts = compose.onAllNodesWithTag("afford_money")
        repeat(amounts.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            amounts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { assertFalse("Money clipped in a narrow layout", it.hasVisualOverflow) }
        }
        capture("narrow-large-text.png")
    }

    @Test fun darkThemeKeepsWarningReadable() {
        show(dark = true)
        compose.onNodeWithText("Monthly").performClick()
        check("1500")
        compose.onNodeWithText("Consider the trade-off").assertIsDisplayed()
        capture("dark.png")
    }
}
