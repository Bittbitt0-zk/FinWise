/** Room database and DAO. Uses the Room library. */
package com.zK.financialguide

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

@Dao
interface FinanceDao {
    @Query("SELECT * FROM Profile WHERE id = 1")
    fun profile(): Flow<Profile?>

    @Query("SELECT * FROM Profile WHERE id = 1")
    suspend fun currentProfile(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(p: Profile)

    @Query("UPDATE Profile SET savings = savings + :amount WHERE id = 1")
    suspend fun addSavings(amount: Double)

    @Query("SELECT * FROM Expense WHERE dateMillis >= :from AND dateMillis < :until ORDER BY dateMillis DESC")
    fun expensesBetween(from: Long, until: Long): Flow<List<Expense>>

    @Insert
    suspend fun addExpense(e: Expense)

    @Delete
    suspend fun deleteExpense(e: Expense)

    @Query("SELECT * FROM Goal ORDER BY deadlineMillis")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal ORDER BY deadlineMillis")
    suspend fun currentGoals(): List<Goal>

    @Upsert
    suspend fun saveGoal(g: Goal)

    @Upsert
    suspend fun saveGoals(goals: List<Goal>)

    @Delete
    suspend fun deleteGoal(g: Goal)

    @Query("SELECT processedMonth FROM MonthlyProgress WHERE id = 1")
    suspend fun processedMonth(): String?

    @Upsert
    suspend fun saveMonthlyProgress(progress: MonthlyProgress)
}

@Database(
    entities = [Profile::class, Expense::class, Goal::class, MonthlyProgress::class],
    version = 2,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
}

/** Add the checkpoint table without changing existing profiles, expenses or goals. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS MonthlyProgress (id INTEGER NOT NULL PRIMARY KEY, processedMonth TEXT NOT NULL)")
    }
}

/** The checkpoint and all changed goals commit or roll back together. */
internal suspend fun FinanceDatabase.advanceMonthlyGoals(now: LocalDate, legacyMonth: YearMonth? = null) {
    withTransaction {
        val dao = dao()
        val profile = dao.currentProfile() ?: return@withTransaction
        val original = dao.currentGoals()
        val storedMonth = dao.processedMonth()
        // Import the old preferences checkpoint only until Room has its own checkpoint.
        val lastMonth = storedMonth?.let { YearMonth.parse(it) } ?: legacyMonth
        val result = FinanceEngine.catchUpGoalProgress(profile, original, lastMonth, now)
        val originalById = original.associateBy { it.id }
        val changed = result.goals.filter { originalById[it.id] != it }
        if (changed.isNotEmpty()) dao.saveGoals(changed)
        if (storedMonth != result.processedMonth.toString()) {
            dao.saveMonthlyProgress(MonthlyProgress(processedMonth = result.processedMonth.toString()))
        }
    }
}
