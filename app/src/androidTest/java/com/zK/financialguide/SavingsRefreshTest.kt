package com.zK.financialguide

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Deposits must publish a consistent balance and plan without a calendar refresh. */
@RunWith(AndroidJUnit4::class)
class SavingsRefreshTest {
    private lateinit var db: FinanceDatabase
    private lateinit var vm: FinanceViewModel
    private lateinit var store: ViewModelStore
    private val today = LocalDate.of(2026, 10, 10)
    private val profile = Profile(income = 3400.0, essentials = 1500.0, savings = 0.0,
        debtBalance = 0.0, minDebtPayment = 0.0)

    @Before fun openViewModel(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).build()
        db.dao().saveProfile(profile)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store = ViewModelStore()
            vm = FinanceViewModel(app, db, { CalendarState(today, ZoneId.systemDefault()) }, null)
            store.put("savings-test", vm)
        }
        withTimeout(5000) { vm.finances.first { it.loaded } }
    }

    @After fun closeViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    private suspend fun awaitSavings(amount: Double): FinanceState = withTimeout(5000) {
        vm.finances.first { it.profile?.savings == amount }
    }

    @Test fun depositUpdatesBalanceAndPlanImmediatelyAfterCommit() = runBlocking {
        vm.addToSavings(250.0)
        val state = awaitSavings(250.0)
        assertEquals(4250.0, state.plan!!.emergencyGap, 0.01)
        assertEquals(FinanceEngine.calculate(state.profile!!, state.goals, today), state.plan)
        assertEquals(250.0, db.dao().currentProfile()!!.savings, 0.01)
    }

    @Test fun rapidDepositsNeverPairAnOldBalanceWithANewPlan() = runBlocking {
        val observed = mutableListOf<FinanceState>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.finances.collect { if (it.loaded) observed.add(it) }
        }
        vm.addToSavings(300.0)
        vm.addToSavings(200.0)
        vm.addToSavings(4000.0)
        val final = awaitSavings(4500.0)
        observer.cancelAndJoin()
        observed.forEach { state ->
            assertEquals(FinanceEngine.calculate(state.profile!!, state.goals, state.calendar.today), state.plan)
        }
        assertEquals(0.0, final.plan!!.emergencyGap, 0.01)
        assertEquals(0.0, final.plan.emergencyContribution, 0.01)
        assertEquals(4500.0, db.dao().currentProfile()!!.savings, 0.01)
    }

    @Test fun resettingSavingsAlsoResetsTheFundedStatus() = runBlocking {
        vm.addToSavings(4500.0)
        awaitSavings(4500.0)
        vm.saveProfile(profile)
        val state = awaitSavings(0.0)
        assertEquals(4500.0, state.plan!!.emergencyGap, 0.01)
        assertEquals(FinanceEngine.calculate(profile, emptyList(), today), state.plan)
    }

    @Test fun invalidDepositsDoNotChangeTheStoredBalance() = runBlocking {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -10.0, 0.0).forEach { vm.addToSavings(it) }
        vm.addToSavings(100.0)
        awaitSavings(100.0)
        assertEquals(100.0, db.dao().currentProfile()!!.savings, 0.01)
    }

    @Test fun roomChangesAlsoRefreshTheSharedSnapshot() = runBlocking {
        db.dao().saveProfile(profile.copy(savings = 5000.0))
        val state = awaitSavings(5000.0)
        assertEquals(0.0, state.plan!!.emergencyGap, 0.01)
        assertEquals(1248.0, state.plan.longTermSavings, 0.01)
    }
}
