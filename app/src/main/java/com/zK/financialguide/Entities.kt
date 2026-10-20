/** Room entities for the user's profile, expenses and goals. */
package com.zK.financialguide

import androidx.room.Entity
import androidx.room.PrimaryKey

// Single-row table (id is always 1) holding the user's financial info
@Entity
data class Profile(
    @PrimaryKey val id: Int = 1,
    val income: Double,
    val essentials: Double,
    val savings: Double,
    val debtBalance: Double,
    val minDebtPayment: Double,
    val emergencyMonths: Int = 3
)

@Entity
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val dateMillis: Long,
    val description: String,
    val isEssential: Boolean = false
)

@Entity
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double,
    val deadlineMillis: Long

)

/** A single checkpoint, committed together with automatic goal contributions. */
@Entity
data class MonthlyProgress(
    @PrimaryKey val id: Int = 1,
    val processedMonth: String
)

val CATEGORIES = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Education", "Other")
// Spending in these categories is treated as essential, so it doesn't count against the lifestyle budget
val ESSENTIAL_CATEGORIES = setOf("Bills", "Education")
