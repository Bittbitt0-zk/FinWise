package com.zK.financialguide

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class Status { HEALTHY, NEEDS_ATTENTION, AT_RISK }

/** How much financial flexibility the user has, based on the cash flow ratio. */
enum class Tier { DEFICIT, LIMITED, MODERATE, STRONG }
enum class Verdict { YES, YES_BUT, NOT_YET, NO }

/** Numeric advice is kept separate from its wording so the UI never parses a reason string. */
data class AffordDetail(val label: String, val amount: Double, val perMonth: Boolean = false)
data class AffordResult(
    val verdict: Verdict,
    val reason: String,
    val details: List<AffordDetail> = emptyList(),
    val waitMonths: Int? = null
)
data class AffordBudget(
    val lifestyleRemaining: Double,
    val extraSavings: Double,
    val longTermAvailable: Double,
    val overspent: Double,
    val savingsBalance: Double
)
data class GoalAllocation(
    val goalId: Long, val name: String,
    val required: Double, val funded: Double
)

data class Plan(
    val cashFlow: Double,
    val cashFlowRatio: Double,          // percent of income
    val essentialRatio: Double,         // percent of income
    val debtRatio: Double,              // percent of income
    val tier: Tier,
    val emergencyTarget: Double,
    val emergencyGap: Double,
    val emergencyContribution: Double,
    val emergencyMonthsToComplete: Int?, // null when complete or not funded
    val goalAllocations: List<GoalAllocation>,
    val goalShortfall: Double,
    val lifestyleBudget: Double,
    val longTermSavings: Double,
    val status: Status,
    val reasons: List<String>,          // short issues for the status banner
    val explanations: List<String>      // "why" text for each recommendation
)

data class ProgressedFinances(val profile: Profile, val goals: List<Goal>)
data class MonthlyGoalProgress(val goals: List<Goal>, val processedMonth: YearMonth)

/**
 * Deterministic rule-based engine: the same input always gives the same plan.
 * DETECT -> ANALYZE -> PRIORITIZE -> ALLOCATE -> EXPLAIN. No AI involved.
 *
 * The thresholds below are application rules chosen to make the plan practical
 * and explainable. They are NOT universal financial laws.
 */
object FinanceEngine {
    // Cash-flow tiers (percent of income)
    private const val LIMITED_MAX_RATIO = 20.0
    private const val MODERATE_MAX_RATIO = 40.0

    // Emergency fund: months to rebuild a COMPLETELY empty fund, by tier.
    // Smaller gaps get proportionally shorter timelines.
    private const val BASE_MONTHS_STRONG = 9.0
    private const val BASE_MONTHS_MODERATE = 12.0
    private const val BASE_MONTHS_LIMITED = 18.0
    private const val EMERGENCY_MAX_SHARE = 0.6   // of money left after lifestyle allowance

    // Lifestyle allowance
    private const val LIFESTYLE_BASE_SHARE = 0.10          // of income
    private const val LIFESTYLE_MAX_CASHFLOW_SHARE = 0.5   // never more than half of cash flow
    private const val LIFESTYLE_LEFTOVER_SHARE = 0.20  // of money left after emergency fund and goals
    fun deadlineDate(deadlineMillis: Long): LocalDate =
        Instant.ofEpochMilli(deadlineMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Calendar months match the contributions applied when each month ends. */
    fun monthsUntil(deadlineMillis: Long, now: LocalDate = LocalDate.now()): Int =
        maxOf(0, ChronoUnit.MONTHS.between(
            YearMonth.from(now), YearMonth.from(deadlineDate(deadlineMillis))
        ).toInt())

    private fun usd(x: Double) = "\$%,.0f".format(x)

    private fun tierFor(cashFlow: Double, ratio: Double) = when {
        cashFlow <= 0 -> Tier.DEFICIT
        ratio <= LIMITED_MAX_RATIO -> Tier.LIMITED
        ratio <= MODERATE_MAX_RATIO -> Tier.MODERATE
        else -> Tier.STRONG
    }

    fun calculate(p: Profile, goals: List<Goal>, now: LocalDate = LocalDate.now()): Plan {
        // ---- 1. DETECT ----
        val cashFlow = p.income - p.essentials - p.minDebtPayment
        fun pct(x: Double) = if (p.income > 0) x / p.income * 100 else 0.0
        val cashFlowRatio = pct(cashFlow)
        val essentialRatio = pct(p.essentials)
        val debtRatio = pct(p.minDebtPayment)
        val target = p.essentials * p.emergencyMonths
        val gap = maxOf(0.0, target - p.savings)

        // Required monthly amount for every unfinished goal, earliest deadline first
        val required = goals
            .filter { it.target > it.saved }
            .sortedBy { it.deadlineMillis }
            .map { it to (it.target - it.saved) / maxOf(1, monthsUntil(it.deadlineMillis, now)) }
        val overdue = required.count { deadlineDate(it.first.deadlineMillis).isBefore(now) }

        // ---- 2. ANALYZE ----
        val tier = tierFor(cashFlow, cashFlowRatio)

        // Deficit: never recommend saving more than the user has
        if (tier == Tier.DEFICIT) {
            val why = if (cashFlow < 0) {
                "Your expenses currently exceed your income by ${usd(-cashFlow)} per month. " +
                        "Building savings is not sustainable until your monthly cash flow becomes positive."
            } else {
                "Your essential expenses and debt payments currently use 100% of your income, " +
                        "leaving no available cash flow for additional savings. Focus on reducing " +
                        "expenses or increasing income before creating a savings plan."
            }
            return Plan(
                cashFlow, cashFlowRatio, essentialRatio, debtRatio, tier,
                target, gap, 0.0, null,
                required.map { (g, r) -> GoalAllocation(g.id, g.name, r, 0.0) },
                required.sumOf { it.second },
                0.0, 0.0, Status.AT_RISK,
                listOf(if (cashFlow < 0) "Cash flow is negative." else "Cash flow is zero."),
                listOf(
                    why,
                    "No emergency savings, goal contributions or long-term savings are " +
                            "recommended until your cash flow is positive."
                )
            )
        }

        // ---- 3. PRIORITIZE + 4. ALLOCATE ----
        // Lifestyle allowance is protected first, but never more than half of cash flow
        val baseAllowance = p.income * LIFESTYLE_BASE_SHARE
        val allowance = minOf(baseAllowance, cashFlow * LIFESTYLE_MAX_CASHFLOW_SHARE)
        val usable = cashFlow - allowance

        // Emergency fund: keep the monthly target stable as savings grows.
        var emergency = 0.0
        var monthsToComplete: Int? = null
        var capped = false
        if (gap > 0) {
            val baseMonths = when (tier) {
                Tier.STRONG -> BASE_MONTHS_STRONG
                Tier.MODERATE -> BASE_MONTHS_MODERATE
                else -> BASE_MONTHS_LIMITED
            }
            val ideal = minOf(gap, target / baseMonths)
            val cap = usable * EMERGENCY_MAX_SHARE
            capped = ideal > cap
            emergency = minOf(ideal, cap)
            if (emergency > 0) monthsToComplete = ceil(gap / emergency - 1e-9).toInt()
        }

        // Goals get what is left, earliest deadline first
        var pool = usable - emergency
        val allocations = required.map { (g, req) ->
            val funded = minOf(req, maxOf(0.0, pool))
            pool -= funded
            GoalAllocation(g.id, g.name, req, funded)
        }
        val shortfall = allocations.sumOf { it.required - it.funded }

        // What is left after higher priorities: 20% more for lifestyle, the rest to long-term savings
        val leftover = maxOf(0.0, pool)
        val extraLifestyle = leftover * LIFESTYLE_LEFTOVER_SHARE
        val lifestyle = allowance + extraLifestyle
        val longTerm = leftover - extraLifestyle

        // ---- 5. EXPLAIN ----
        val tierWord = tier.name.lowercase()
        val explanations = mutableListOf<String>()
        val allowanceText = if (allowance < baseAllowance - 0.005) {
            "reduced from the usual 10% of your income because your cash flow is tight"
        } else {
            "10% of your income"
        }
        explanations += "Your lifestyle budget is ${usd(lifestyle)}/month: a protected allowance of " +
                "${usd(allowance)} ($allowanceText)" +
                (if (extraLifestyle > 0.005) {
                    " plus ${usd(extraLifestyle)}, which is 20% of the money left after your emergency fund and goals."
                } else {
                    "."
                })

        explanations += if (gap <= 0) {
            "Your emergency fund is complete (${usd(p.savings)} of ${usd(target)}), so no more money is allocated to it."
        } else {
            "Recommended emergency contribution: ${usd(emergency)}/month because you have $tierWord cash flow " +
                    "and your fund still needs ${usd(gap)}. At this rate you are estimated to reach your " +
                    "${usd(target)} target in about ${monthsToComplete ?: 0} months." +
                    if (capped) " This is limited so goals and savings are not crowded out." else ""
        }

        if (allocations.isNotEmpty()) {
            explanations += if (overdue > 0) {
                "$overdue goal${if (overdue == 1) " is" else "s are"} overdue. " +
                        "Update the deadline or target; monthly funding cannot meet a deadline that has passed."
            } else if (shortfall > 0.005) {
                "Your goals need ${usd(shortfall)} more per month than is available, so they cannot all be " +
                        "reached by their deadlines. Consider later deadlines, smaller targets, or lower spending."
            } else {
                "Your goals are fully funded at ${usd(allocations.sumOf { it.funded })}/month."
            }
        }

        explanations += if (longTerm > 0.005) {
            "The remaining ${usd(longTerm)}/month goes to long-term savings."
        } else {
            "No money is left for long-term savings yet; higher priorities use all of your available cash flow."
        }

        val reasons = mutableListOf<String>()
        if (gap > 0) reasons += "Emergency fund is incomplete."
        if (shortfall > 0.005) reasons += "Goals are not fully affordable by their deadlines."
        if (overdue > 0) reasons += "A goal deadline has passed."
        if (tier == Tier.LIMITED) reasons += "Your cash flow is limited."
        val status = if (reasons.isEmpty()) Status.HEALTHY else Status.NEEDS_ATTENTION

        return Plan(
            cashFlow, cashFlowRatio, essentialRatio, debtRatio, tier,
            target, gap, emergency, monthsToComplete,
            allocations, shortfall, lifestyle, longTerm,
            status, reasons, explanations
        )
    }

    fun applyMonthlyProgress(
        p: Profile,
        goals: List<Goal>,
        now: LocalDate = LocalDate.now()
    ): ProgressedFinances {
        // Month-to-month progress is applied only to goals. Emergency savings are manual
        // deposits, so this function preserves Profile.savings exactly as entered.
        val cappedGoals = goals.map { it.copy(saved = it.saved.coerceIn(0.0, it.target)) }
        val plan = calculate(p, cappedGoals, now)
        val updatedGoals = cappedGoals.map { goal ->
            val funded = plan.goalAllocations.firstOrNull { it.goalId == goal.id }?.funded ?: 0.0
            goal.copy(saved = minOf(goal.target, goal.saved + funded))
        }
        return ProgressedFinances(p, updatedGoals)
    }

    /** Catch up completed months. A backward clock change never rewinds the checkpoint. */
    fun catchUpGoalProgress(
        p: Profile,
        goals: List<Goal>,
        lastProcessedMonth: YearMonth?,
        now: LocalDate
    ): MonthlyGoalProgress {
        val currentMonth = YearMonth.from(now)
        var month = lastProcessedMonth ?: currentMonth
        var updated = goals.map { it.copy(saved = it.saved.coerceIn(0.0, it.target)) }
        while (month.isBefore(currentMonth)) {
            updated = applyMonthlyProgress(p, updated, month.atDay(1)).goals
            month = month.plusMonths(1)
        }
        return MonthlyGoalProgress(updated, month)
    }

    /**
     * Overspending uses this month's long-term allocation first, then existing savings.
     * The same figures drive both the verdict and the purchase screen's budget summary.
     */
    fun affordBudget(p: Profile, plan: Plan, lifestyleSpent: Double): AffordBudget {
        val overspent = maxOf(0.0, lifestyleSpent - plan.lifestyleBudget)
        val uncovered = maxOf(0.0, overspent - plan.longTermSavings)
        val balance = maxOf(0.0, p.savings - uncovered)
        return AffordBudget(
            lifestyleRemaining = maxOf(0.0, plan.lifestyleBudget - lifestyleSpent),
            extraSavings = maxOf(0.0, balance - plan.emergencyTarget),
            longTermAvailable = maxOf(0.0, plan.longTermSavings - overspent),
            overspent = overspent,
            savingsBalance = balance
        )
    }

    /** Read-only purchase advice. Forecasts assume the stored plan and spending level continue. */
    fun canAfford(
        p: Profile,
        plan: Plan,
        price: Double,
        recurring: Boolean,
        lifestyleSpent: Double,
        goals: List<Goal> = emptyList(),
        now: LocalDate = LocalDate.now()
    ): AffordResult {
        if (!price.isFinite() || price < 0.01) {
            return AffordResult(Verdict.NO, "Enter a price of at least ${money(0.01)}.")
        }
        if (!plan.cashFlow.isFinite() || !lifestyleSpent.isFinite() || !p.savings.isFinite()) {
            return AffordResult(Verdict.NO, "Update your financial details before checking this purchase.")
        }
        if (plan.cashFlow <= 0) {
            return AffordResult(
                Verdict.NO,
                "Your monthly cash flow is not positive, so new spending would add financial stress. " +
                        "Improve your cash flow first."
            )
        }
        val budget = affordBudget(p, plan, lifestyleSpent)
        return if (recurring) recurringVerdict(p, plan, price, lifestyleSpent, budget, goals, now)
        else oneTimeVerdict(p, plan, price, budget, goals, now)
    }

    private fun oneTimeVerdict(
        p: Profile, plan: Plan, price: Double, budget: AffordBudget, goals: List<Goal>, now: LocalDate
    ): AffordResult {
        val remainingLifestyle = budget.lifestyleRemaining
        val freeSavings = budget.extraSavings
        val usableSavings = maxOf(0.0, budget.savingsBalance - p.essentials)
        val safeNow = remainingLifestyle + freeSavings
        val usableNow = remainingLifestyle + usableSavings
        val flex = budget.longTermAvailable

        if (price <= remainingLifestyle) {
            return AffordResult(
                Verdict.YES,
                "This fits within your remaining lifestyle budget. Your emergency savings and planned " +
                        "goal contributions are unchanged.",
                listOf(
                    AffordDetail("From lifestyle money", price),
                    AffordDetail("Lifestyle left afterward", remainingLifestyle - price)
                )
            )
        }

        if (price <= safeNow) {
            val fromLifestyle = minOf(price, remainingLifestyle)
            val fromSavings = maxOf(0.0, price - remainingLifestyle)
            return AffordResult(
                Verdict.YES,
                "Your lifestyle money and savings above the emergency target cover this purchase. " +
                        "The emergency portion stays protected.",
                listOf(
                    AffordDetail("From lifestyle money", fromLifestyle),
                    AffordDetail("From extra savings", fromSavings),
                    AffordDetail("Savings left afterward", budget.savingsBalance - fromSavings)
                )
            )
        }

        val shortfallToSafe = price - safeNow
        if (flex > 0) {
            val months = maxOf(1, ceil(shortfallToSafe / flex - 1e-9).toInt())
            if (months <= 12) {
                return AffordResult(
                    Verdict.NOT_YET,
                    "Set aside the remaining ${money(shortfallToSafe)} before buying. At your current " +
                            "available savings rate, that takes about $months month${if (months == 1) "" else "s"} " +
                            "while protecting emergency savings.",
                    listOf(
                        AffordDetail("Available now", safeNow),
                        AffordDetail("Still to save", shortfallToSafe),
                        AffordDetail("Available saving rate", flex, perMonth = true)
                    ),
                    waitMonths = months
                )
            }
        }

        if (price <= usableNow) {
            val fromSavings = maxOf(0.0, price - remainingLifestyle)
            val emergencyUsed = maxOf(0.0, fromSavings - freeSavings)
            val savingsAfter = budget.savingsBalance - fromSavings
            // Recalculate the emergency recommendation for the balance after the purchase.
            val rebuildRate = calculate(p.copy(savings = savingsAfter), goals, now).emergencyContribution
            return if (rebuildRate > 0) {
                val delay = ceil(emergencyUsed / rebuildRate - 1e-9).toInt()
                AffordResult(
                    Verdict.YES_BUT,
                    "Only consider this if it is important: ${money(emergencyUsed)} would come from your " +
                            "emergency fund. Replacing that amount at the revised contribution of " +
                            "${money(rebuildRate)}/month takes about $delay month${if (delay == 1) "" else "s"}.",
                    listOf(
                        AffordDetail("Emergency savings used", emergencyUsed),
                        AffordDetail("Savings left afterward", savingsAfter),
                        AffordDetail("Revised emergency saving", rebuildRate, perMonth = true)
                    )
                )
            } else {
                AffordResult(
                    Verdict.NO,
                    "Do not buy this yet. It would reduce your emergency savings, and your plan leaves no " +
                            "monthly room to rebuild them."
                )
            }
        }

        if (flex > 0) {
            val months = maxOf(1, ceil(shortfallToSafe / flex - 1e-9).toInt())
            return AffordResult(
                Verdict.NO,
                "${money(price)} is too much right now. You can spend ${money(safeNow)} without touching " +
                        "your emergency fund, but the remaining ${money(shortfallToSafe)} would take about " +
                        "$months months to save at ${money(flex)}/month.",
                listOf(AffordDetail("Available now", safeNow), AffordDetail("Uncovered amount", shortfallToSafe))
            )
        }
        return AffordResult(
            Verdict.NO,
            "This exceeds the money available without taking from your emergency fund or goals. " +
                    "There is no available long-term allocation to close the gap right now.",
            listOf(AffordDetail("Available now", safeNow), AffordDetail("Uncovered amount", shortfallToSafe))
        )
    }

    private fun recurringVerdict(
        p: Profile, plan: Plan, price: Double, lifestyleSpent: Double,
        budget: AffordBudget, goals: List<Goal>, now: LocalDate
    ): AffordResult {
        val remainingLifestyle = budget.lifestyleRemaining
        val lt = budget.longTermAvailable
        val fromLongTerm = maxOf(0.0, price - remainingLifestyle)
        return when {
            price <= remainingLifestyle -> AffordResult(
                Verdict.YES,
                "This fits within your remaining lifestyle budget. Available long-term savings stay at " +
                        "${money(lt)}/month.",
                listOf(
                    AffordDetail("From lifestyle money", price, perMonth = true),
                    AffordDetail("Lifestyle left afterward", remainingLifestyle - price, perMonth = true),
                    AffordDetail("Long-term savings left", lt, perMonth = true)
                )
            )
            fromLongTerm <= lt * 0.5 -> AffordResult(
                Verdict.YES,
                "Your lifestyle money covers part of the cost. The rest uses less than or equal to half " +
                        "of your available long-term allocation; emergency and goal contributions are unchanged.",
                recurringDetails(price, remainingLifestyle, fromLongTerm, lt)
            )
            fromLongTerm <= lt -> AffordResult(
                Verdict.YES_BUT,
                "This uses more than half of your available long-term allocation. Starting it would reduce " +
                        "future savings to ${money(lt - fromLongTerm)}/month.",
                recurringDetails(price, remainingLifestyle, fromLongTerm, lt)
            )
            else -> {
                val wait = recurringWaitMonths(p, plan, price, lifestyleSpent, goals, now)
                if (wait != null) AffordResult(
                    Verdict.NOT_YET,
                    "At the current spending level, this may fit in about $wait month${if (wait == 1) "" else "s"} " +
                            "if you follow the planned savings contributions. Recheck then: the estimate " +
                            "accounts for emergency savings and goal deadlines.",
                    listOf(
                        AffordDetail("Available each month now", remainingLifestyle + lt, perMonth = true),
                        AffordDetail("Monthly gap now", price - remainingLifestyle - lt, perMonth = true)
                    ),
                    waitMonths = wait
                ) else AffordResult(
                    Verdict.NO,
                    "This exceeds the monthly money available after your recorded spending and protected " +
                            "commitments. Lower the monthly cost or reduce other spending, then recheck.",
                    listOf(
                        AffordDetail("Available each month", remainingLifestyle + lt, perMonth = true),
                        AffordDetail("Monthly gap", price - remainingLifestyle - lt, perMonth = true)
                    )
                )
            }
        }
    }

    private fun recurringDetails(price: Double, lifestyle: Double, fromSavings: Double, available: Double) = listOf(
        AffordDetail("From lifestyle money", minOf(price, lifestyle), perMonth = true),
        AffordDetail("From long-term allocation", fromSavings, perMonth = true),
        AffordDetail("Long-term savings left", available - fromSavings, perMonth = true)
    )

    /** Pure, bounded forecast. Null means no supported estimate, not that it can never fit. */
    private fun recurringWaitMonths(
        p: Profile, plan: Plan, price: Double, spent: Double, goals: List<Goal>, now: LocalDate
    ): Int? {
        if (price > plan.cashFlow) return null
        // Without the active goal data, do not promise that its allocation becomes spendable.
        if (plan.goalAllocations.any { allocation -> goals.none { it.id == allocation.goalId } }) return null
        var projectedProfile = p
        var projectedGoals = goals
        var date = now.withDayOfMonth(1)
        for (month in 1..12) {
            val monthlyPlan = calculate(projectedProfile, projectedGoals, date)
            val available = affordBudget(projectedProfile, monthlyPlan, spent)
            // A date estimate is unreliable if spending already cuts into protected contributions.
            if (available.overspent > monthlyPlan.longTermSavings + 0.005) return null
            projectedGoals = applyMonthlyProgress(projectedProfile, projectedGoals, date).goals
            projectedProfile = projectedProfile.copy(savings = projectedProfile.savings + monthlyPlan.emergencyContribution)
            date = date.plusMonths(1)
            val futurePlan = calculate(projectedProfile, projectedGoals, date)
            val futureBudget = affordBudget(projectedProfile, futurePlan, spent)
            if (price <= futureBudget.lifestyleRemaining + futureBudget.longTermAvailable) return month
        }
        return null
    }
}
