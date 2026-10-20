package com.zK.financialguide

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/** Simulate Settings date changes while keeping the same ViewModel alive. */
@RunWith(AndroidJUnit4::class)
class CalendarRefreshTest {
    private lateinit var db: FinanceDatabase
    private lateinit var vm: FinanceViewModel
    private lateinit var store: ViewModelStore
    private val start = LocalDate.of(2026, 10, 10)
    private val date = AtomicReference(start)
    private val zone = ZoneId.systemDefault()
    private val p = Profile(income = 3400.0, essentials = 1500.0, savings = 4500.0, debtBalance = 0.0, minDebtPayment = 0.0)
    private fun millis(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val goal = Goal(id = 1, name = "Laptop", target = 1500.0, saved = 0.0, deadlineMillis = millis(start.plusMonths(5)))

    @Before
    fun openViewModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).build()
        db.dao().saveProfile(p)
        db.dao().saveGoal(goal)
        db.advanceMonthlyGoals(start)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store = ViewModelStore()
            vm = FinanceViewModel(app, db, { CalendarState(date.get(), zone) }, null)
            store.put("calendar-test", vm)
        }
    }

    @After
    fun closeViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    @Test
    fun dateChangeUpdatesSavingsCountdownAndPlanWithoutReopening() = runBlocking {
        val next = start.plusMonths(1)
        date.set(next)
        vm.refreshCalendar()
        withTimeout(5000) {
            val state = vm.finances.first { it.calendar.today == next && it.goals.singleOrNull()?.saved == 300.0 }
            val saved = state.goals.single()
            val plan = state.plan!!
            assertEquals(300.0, saved.saved, 0.01)
            assertEquals(4, FinanceEngine.monthsUntil(saved.deadlineMillis, state.calendar.today))
            assertEquals(300.0, plan.goalAllocations.single().funded, 0.01)
            assertEquals(p, state.profile)
        }
        vm.refreshCalendar()
        // This transaction waits for pending Room writes before reading the persisted result.
        db.advanceMonthlyGoals(next)
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
    }

    @Test
    fun dateChangeSwitchesExpensesToNewMonthAndBack() = runBlocking {
        db.dao().addExpense(Expense(amount = 10.0, category = "Food", dateMillis = millis(start), description = "October"))
        db.dao().addExpense(Expense(amount = 20.0, category = "Food", dateMillis = millis(start.plusMonths(1)), description = "November"))
        withTimeout(5000) {
            assertEquals(10.0, vm.monthExpenses.first { it.singleOrNull()?.amount == 10.0 }.single().amount, 0.01)
            date.set(start.plusMonths(1))
            vm.refreshCalendar()
            assertEquals(20.0, vm.monthExpenses.first { it.singleOrNull()?.amount == 20.0 }.single().amount, 0.01)
            date.set(start)
            vm.refreshCalendar()
            assertEquals(10.0, vm.monthExpenses.first { it.singleOrNull()?.amount == 10.0 }.single().amount, 0.01)
        }
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
        assertEquals("2026-11", db.dao().processedMonth())
    }

    @Test
    fun editingGoalDuringDateChangePreservesAutomaticDeposit() = runBlocking {
        date.set(start.plusMonths(1))
        vm.saveGoal(goal.copy(name = "Mac"), original = goal)
        val updated = withTimeout(5000) { vm.finances.first { it.goals.singleOrNull()?.name == "Mac" }.goals.single() }
        assertEquals(300.0, updated.saved, 0.01)
        assertEquals(goal.deadlineMillis, updated.deadlineMillis)
    }
}
