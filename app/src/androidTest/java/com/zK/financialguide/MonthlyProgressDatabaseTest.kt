package com.zK.financialguide

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Uses isolated databases; the user's finance.db and phone clock are never changed. */
@RunWith(AndroidJUnit4::class)
class MonthlyProgressDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: FinanceDatabase
    private val databaseNames = mutableListOf<String>()
    private val start = LocalDate.of(2026, 10, 10)
    private val p = Profile(
        income = 3400.0, essentials = 1500.0, savings = 4500.0,
        debtBalance = 0.0, minDebtPayment = 0.0
    )

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun goal() = Goal(id = 1, name = "Laptop", target = 1500.0, saved = 0.0, deadlineMillis = millis(start.plusMonths(5)))

    @Before
    fun openDatabase() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        db.close()
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    private suspend fun seed() {
        db.dao().saveProfile(p)
        db.dao().saveGoal(goal())
        db.advanceMonthlyGoals(start)
    }

    @Test
    fun simultaneousRefreshesApplyOneMonthlyContribution() = runBlocking {
        seed()
        coroutineScope {
            repeat(5) { launch { db.advanceMonthlyGoals(start.plusMonths(1)) } }
        }
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
        assertEquals("2026-11", db.dao().processedMonth())
        assertEquals(p, db.dao().currentProfile())
    }

    @Test
    fun missedMonthsAndBackwardClockPreserveCheckpoint() = runBlocking {
        seed()
        db.advanceMonthlyGoals(start.plusMonths(3))
        db.advanceMonthlyGoals(start)
        db.advanceMonthlyGoals(start.plusMonths(3))
        assertEquals(900.0, db.dao().currentGoals().single().saved, 0.01)
        assertEquals("2027-01", db.dao().processedMonth())
        db.advanceMonthlyGoals(start.plusMonths(4))
        assertEquals(1200.0, db.dao().currentGoals().single().saved, 0.01)
    }

    @Test
    fun legacyPreferencesCheckpointIsImportedOnlyOnce() = runBlocking {
        db.dao().saveProfile(p)
        db.dao().saveGoal(goal())
        val legacy = YearMonth.from(start)
        db.advanceMonthlyGoals(start.plusMonths(1), legacy)
        db.advanceMonthlyGoals(start.plusMonths(1), legacy)
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
        assertEquals("2026-11", db.dao().processedMonth())
    }

    @Test
    fun failedTransactionRollsBackGoalsAndCheckpointTogether() = runBlocking {
        seed()
        val failed = runCatching {
            db.withTransaction {
                db.advanceMonthlyGoals(start.plusMonths(1))
                error("Simulated interruption before commit")
            }
        }
        assertTrue(failed.isFailure)
        assertEquals(0.0, db.dao().currentGoals().single().saved, 0.01)
        assertEquals("2026-10", db.dao().processedMonth())
        db.advanceMonthlyGoals(start.plusMonths(1))
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
    }

    @Test
    fun databaseReopenDoesNotRepeatContribution() = runBlocking {
        val name = "finwise-reopen-${UUID.randomUUID()}.db"
        databaseNames += name
        db.close()
        db = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        seed()
        db.advanceMonthlyGoals(start.plusMonths(1))
        db.close()
        db = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        db.advanceMonthlyGoals(start.plusMonths(1))
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
        assertEquals("2026-11", db.dao().processedMonth())
    }

    @Test
    fun expenseQueriesExcludeOtherMonthsIncludingFutureDates() = runBlocking {
        val october = LocalDate.of(2026, 10, 1)
        val november = october.plusMonths(1)
        listOf(october.minusDays(1), october, november, november.plusMonths(1)).forEachIndexed { index, date ->
            db.dao().addExpense(Expense(amount = (index + 1).toDouble(), category = "Food", dateMillis = millis(date), description = "Test"))
        }
        val octExpenses = db.dao().expensesBetween(millis(october), millis(november)).first()
        val novExpenses = db.dao().expensesBetween(millis(november), millis(november.plusMonths(1))).first()
        assertEquals(listOf(2.0), octExpenses.map { it.amount })
        assertEquals(listOf(3.0), novExpenses.map { it.amount })
    }

    @Test
    fun rapidManualDepositsAccumulateWithoutChangingOtherProfileFields() = runBlocking {
        db.dao().saveProfile(p)
        coroutineScope {
            repeat(5) { launch { db.dao().addSavings(100.0) } }
        }
        assertEquals(p.copy(savings = 5000.0), db.dao().currentProfile())
    }

    @Test
    fun noProfileDoesNotCreateAProgressCheckpoint() = runBlocking {
        db.advanceMonthlyGoals(start)
        assertNull(db.dao().processedMonth())
    }

    @Test
    fun versionOneMigrationPreservesExistingData() = runBlocking {
        val name = "finwise-migration-${UUID.randomUUID()}.db"
        databaseNames += name
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE Profile (id INTEGER NOT NULL PRIMARY KEY, income REAL NOT NULL, essentials REAL NOT NULL, savings REAL NOT NULL, debtBalance REAL NOT NULL, minDebtPayment REAL NOT NULL, emergencyMonths INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE Expense (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, category TEXT NOT NULL, dateMillis INTEGER NOT NULL, description TEXT NOT NULL, isEssential INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE Goal (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, target REAL NOT NULL, saved REAL NOT NULL, deadlineMillis INTEGER NOT NULL)")
            old.execSQL("INSERT INTO Profile VALUES (1, 3400, 1500, 4500, 0, 0, 3)")
            old.execSQL("INSERT INTO Goal VALUES (1, 'Laptop', 1500, 300, ?)", arrayOf(millis(start.plusMonths(5))))
            old.execSQL("INSERT INTO Expense VALUES (1, 25, 'Food', ?, 'Lunch', 0)", arrayOf(millis(start)))
            old.version = 1
        }
        db.close()
        db = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        assertEquals(p, db.dao().currentProfile())
        assertEquals(goal().copy(saved = 300.0), db.dao().currentGoals().single())
        assertEquals(25.0, db.dao().expensesBetween(millis(start.withDayOfMonth(1)), millis(start.withDayOfMonth(1).plusMonths(1))).first().single().amount, 0.01)
        assertNull(db.dao().processedMonth())
        db.advanceMonthlyGoals(start, YearMonth.from(start))
        assertEquals("2026-10", db.dao().processedMonth())
        assertEquals(300.0, db.dao().currentGoals().single().saved, 0.01)
    }
}
