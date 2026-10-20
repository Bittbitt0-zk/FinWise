/** Holds app state, exposes the plan, and owns the database. Uses Lifecycle ViewModel and Room. */
package com.zK.financialguide

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarState(val today: LocalDate, val zoneId: ZoneId)

/** One committed snapshot: a screen must never pair a new balance with an old plan. */
data class FinanceState(
    val calendar: CalendarState,
    val loaded: Boolean = false,
    val profile: Profile? = null,
    val goals: List<Goal> = emptyList(),
    val plan: Plan? = null
)

// The one-argument constructor is used by Android; tests inject a database and calendar.
class FinanceViewModel @JvmOverloads constructor(
    app: Application,
    private val db: FinanceDatabase = Room.databaseBuilder(app, FinanceDatabase::class.java, "finance.db")
        .addMigrations(MIGRATION_1_2).build(),
    private val calendarProvider: () -> CalendarState = {
        val zone = ZoneId.systemDefault()
        CalendarState(LocalDate.now(zone), zone)
    },
    private val legacyMonth: YearMonth? = app.getSharedPreferences("auto_progress", Context.MODE_PRIVATE)
        .getString("last_auto_progress_month", null)
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
) : AndroidViewModel(app) {
    private val dao = db.dao()
    private val progressMutex = Mutex()
    private val currentFinances = MutableStateFlow(FinanceState(calendarProvider()))
    val finances: StateFlow<FinanceState> = currentFinances.asStateFlow()

    // The expense query stops 5s after the UI goes away; the snapshot lives with the ViewModel.
    private val sharing = SharingStarted.WhileSubscribed(5000)

    init {
        viewModelScope.launch {
            // Room emissions are invalidation signals, not separate pieces of UI state.
            // Read fresh rows under the same lock so an older emission cannot undo a deposit.
            combine(dao.profile(), dao.goals()) { _, _ -> Unit }
                .collect { commitFinances {} }
        }
    }

    /** Switching months or time zones also switches the expense query. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthExpenses: StateFlow<List<Expense>> = finances
        .map { YearMonth.from(it.calendar.today) to it.calendar.zoneId }
        .distinctUntilChanged()
        .flatMapLatest { (month, zone) ->
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val until = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            dao.expensesBetween(from, until)
        }
        .stateIn(viewModelScope, sharing, emptyList())

    fun saveProfile(p: Profile) {
        updateFinances { dao.saveProfile(p) }
    }

    /** Record money manually added to savings. Emergency savings are not auto-progressed. */
    fun addToSavings(amount: Double) {
        if (!amount.isFinite() || amount <= 0) return
        updateFinances { dao.addSavings(amount) }
    }

    fun addExpense(amount: Double, category: String, description: String, isEssential: Boolean) {
        updateFinances {
            dao.addExpense(
                Expense(
                    amount = amount,
                    category = category,
                    dateMillis = System.currentTimeMillis(),
                    description = description,
                    isEssential = isEssential
                )
            )
        }
    }

    fun deleteExpense(e: Expense) {
        updateFinances { dao.deleteExpense(e) }
    }
    fun saveGoal(g: Goal, original: Goal? = null) {
        updateFinances {
            // Renaming an open goal after a month change must preserve new automatic deposits.
            val saved = if (original != null && g.saved == original.saved) {
                dao.currentGoals().firstOrNull { it.id == g.id }?.saved ?: g.saved
            } else g.saved
            dao.saveGoal(g.copy(saved = saved.coerceIn(0.0, g.target)))
        }
    }

    fun deleteGoal(g: Goal) {
        updateFinances { dao.deleteGoal(g) }
    }

    override fun onCleared() {
        db.close() // release the database when the ViewModel is destroyed
    }

    /** Called on resume and on Android's date, clock and time-zone broadcasts. */
    fun refreshCalendar() {
        updateFinances {}
    }

    private fun updateFinances(update: suspend () -> Unit) {
        viewModelScope.launch { commitFinances(update) }
    }

    /** Publish only after persistence succeeds, without waiting for separate Room queries. */
    private suspend fun commitFinances(update: suspend () -> Unit) {
        progressMutex.withLock {
            val date = calendarProvider()
            val snapshot = db.withTransaction {
                // Catch up before edits so new goals aren't credited for months before creation.
                db.advanceMonthlyGoals(date.today, legacyMonth)
                update()
                val profile = dao.currentProfile()
                val goals = dao.currentGoals()
                FinanceState(
                    calendar = date,
                    loaded = true,
                    profile = profile,
                    goals = goals,
                    plan = profile?.let { FinanceEngine.calculate(it, goals, date.today) }
                )
            }
            currentFinances.value = snapshot
        }
    }
}
