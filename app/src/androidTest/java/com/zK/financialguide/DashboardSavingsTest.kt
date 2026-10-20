package com.zK.financialguide

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Exercise the actual Home dialog and card against an isolated, in-memory database. */
@RunWith(AndroidJUnit4::class)
class DashboardSavingsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: FinanceViewModel
    private lateinit var store: ViewModelStore

    @Before fun showDashboard() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).build()
        db.dao().saveProfile(Profile(income = 3400.0, essentials = 1500.0, savings = 0.0,
            debtBalance = 0.0, minDebtPayment = 0.0))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store = ViewModelStore()
            vm = FinanceViewModel(app, db,
                { CalendarState(LocalDate.of(2026, 10, 10), ZoneId.systemDefault()) }, null)
            store.put("dashboard-test", vm)
        }
        withTimeout(5000) { vm.finances.first { it.loaded } }
        compose.setContent {
            FinWiseTheme { Surface(Modifier.fillMaxSize()) { DashboardScreen(vm, {}, {}) } }
        }
    }

    @After fun closeViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    private fun addSavings(amount: String, total: Double) {
        compose.onNodeWithText("Add savings").performScrollTo().performClick()
        compose.onNodeWithText("Amount").performTextReplacement(amount)
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { vm.finances.value.profile?.savings == total }
        compose.waitForIdle()
    }

    @Test fun manualDepositsUpdateTheVisibleCardWithoutReopeningOrRefreshing() {
        addSavings("3000", 3000.0)
        compose.onNodeWithText("$3,000.00 saved").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Building").assertIsDisplayed()
        compose.onNodeWithText("66%").assertIsDisplayed()

        addSavings("1500", 4500.0)
        compose.onNodeWithText("$4,500.00 saved").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("Fully funded. Your monthly plan no longer allocates money here.")
            .performScrollTo().assertIsDisplayed()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "afford-ui-checks").apply { mkdirs() }
        File(directory, "home-savings.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
