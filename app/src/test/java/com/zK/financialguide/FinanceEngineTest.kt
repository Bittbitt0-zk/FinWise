package com.zK.financialguide

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceEngineTest {

    private fun profile(
        income: Double, essentials: Double, savings: Double,
        minDebt: Double = 0.0, months: Int = 3
    ) = Profile(
        income = income, essentials = essentials, savings = savings,
        debtBalance = 0.0, minDebtPayment = minDebt, emergencyMonths = months
    )

    private fun total(p: Plan) =
        p.emergencyContribution + p.goalAllocations.sumOf { it.funded } +
                p.lifestyleBudget + p.longTermSavings

    // CASE A: strong cash flow, incomplete emergency fund, one goal
    @Test
    fun caseA_strongCashFlow() {
        val now = LocalDate.of(2025, 10, 1)
        val deadline = now.plusMonths(6).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val goal = Goal(id = 1, name = "Laptop", target = 1200.0, saved = 0.0, deadlineMillis = deadline)

        val plan = FinanceEngine.calculate(
            profile(3000.0, 1500.0, 2000.0, minDebt = 200.0), listOf(goal), now
        )

        assertEquals(Tier.STRONG, plan.tier)
        assertEquals(1300.0, plan.cashFlow, 0.01)
        assertEquals(43.3, plan.cashFlowRatio, 0.1)
        assertEquals(2500.0, plan.emergencyGap, 0.01)
        assertEquals(500.0, plan.emergencyContribution, 0.01)   // 2500 / 5 months
        assertEquals(5, plan.emergencyMonthsToComplete)
        assertEquals(360.0, plan.lifestyleBudget, 0.01)
        assertEquals(200.0, plan.goalAllocations[0].funded, 0.01)
        assertEquals(240.0, plan.longTermSavings, 0.01)
        assertEquals(Status.NEEDS_ATTENTION, plan.status)
        assertEquals(plan.cashFlow, total(plan), 0.01)           // never allocates more than cash flow
    }

    // CASE B: moderate cash flow, 6-month target, large gap
    @Test
    fun caseB_moderateCashFlow() {
        val plan = FinanceEngine.calculate(
            profile(3000.0, 1500.0, 0.0, minDebt = 600.0, months = 6), emptyList()
        )

        assertEquals(Tier.MODERATE, plan.tier)
        assertEquals(900.0, plan.cashFlow, 0.01)
        assertEquals(9000.0, plan.emergencyTarget, 0.01)
        assertEquals(348.0, plan.lifestyleBudget, 0.01)
        assertEquals(360.0, plan.emergencyContribution, 0.01)   // capped at 60% of 600
        assertEquals(25, plan.emergencyMonthsToComplete)
        assertEquals(192.0, plan.longTermSavings, 0.01)
        assertEquals(plan.cashFlow, total(plan), 0.01)
    }

    // CASE C: almost-complete emergency fund -> short timeline
    @Test
    fun caseC_almostCompleteEmergencyFund() {
        val plan = FinanceEngine.calculate(
            profile(3000.0, 1500.0, 8000.0, minDebt = 200.0, months = 6), emptyList()
        )

        assertEquals(1000.0, plan.emergencyGap, 0.01)
        assertTrue(plan.emergencyMonthsToComplete!! <= 3)
        assertEquals(plan.cashFlow, total(plan), 0.01)
    }

    @Test
    fun emergencyContributionStaysStableAsSavingsProgresses() {
        val first = FinanceEngine.calculate(
            profile(3000.0, 1500.0, 2000.0, minDebt = 200.0), emptyList()
        )
        val afterRecommendedDeposit = FinanceEngine.calculate(
            profile(3000.0, 1500.0, 2500.0, minDebt = 200.0), emptyList()
        )

        assertEquals(500.0, first.emergencyContribution, 0.01)
        assertEquals(5, first.emergencyMonthsToComplete)
        assertEquals(500.0, afterRecommendedDeposit.emergencyContribution, 0.01)
        assertEquals(4, afterRecommendedDeposit.emergencyMonthsToComplete)
    }

    @Test
    fun monthlyProgressAppliesPlannedGoalSavingsOnly() {
        val now = LocalDate.of(2025, 10, 1)
        val deadline = now.plusMonths(6).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val goal = Goal(id = 1, name = "Laptop", target = 1200.0, saved = 0.0, deadlineMillis = deadline)

        val progressed = FinanceEngine.applyMonthlyProgress(
            profile(3000.0, 1500.0, 2000.0, minDebt = 200.0),
            listOf(goal),
            now
        )

        assertEquals(2000.0, progressed.profile.savings, 0.01)
        assertEquals(200.0, progressed.goals[0].saved, 0.01)
    }

    @Test
    fun monthlyProgressCapsSavingsAtTargets() {
        val now = LocalDate.of(2025, 10, 1)
        val deadline = now.plusMonths(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val goal = Goal(id = 1, name = "Laptop", target = 1200.0, saved = 9999.0, deadlineMillis = deadline)

        val progressed = FinanceEngine.applyMonthlyProgress(
            profile(3000.0, 1500.0, 4490.0),
            listOf(goal),
            now
        )

        assertEquals(4490.0, progressed.profile.savings, 0.01)
        assertEquals(1200.0, progressed.goals[0].saved, 0.01)
    }

    // CASE D: zero cash flow
    @Test
    fun caseD_noCashFlow() {
        val plan = FinanceEngine.calculate(profile(3000.0, 3000.0, 0.0), emptyList())

        assertEquals(Tier.DEFICIT, plan.tier)
        assertEquals(Status.AT_RISK, plan.status)
        assertEquals(0.0, plan.emergencyContribution, 0.01)
        assertEquals(0.0, plan.longTermSavings, 0.01)
        assertEquals(0.0, plan.lifestyleBudget, 0.01)
        assertTrue(plan.explanations.isNotEmpty())
    }

    // CASE E: negative cash flow
    @Test
    fun caseE_negativeCashFlow() {
        val plan = FinanceEngine.calculate(profile(3000.0, 3200.0, 500.0), emptyList())

        assertEquals(-200.0, plan.cashFlow, 0.01)
        assertEquals(Status.AT_RISK, plan.status)
        assertEquals(0.0, plan.emergencyContribution, 0.01)
        assertTrue(plan.explanations.any { it.contains("exceed") })
    }

    // Lifestyle allowance shrinks when cash flow is weak
    @Test
    fun lifestyleAllowanceIsCappedByCashFlow() {
        val plan = FinanceEngine.calculate(profile(3000.0, 2900.0, 0.0), emptyList())

        assertEquals(Tier.LIMITED, plan.tier)
        assertEquals(54.0, plan.lifestyleBudget, 0.01)
        assertEquals(plan.cashFlow, total(plan), 0.01)
    }

    // Complete fund and no goals -> healthy
    @Test
    fun completeFundNoGoalsIsHealthy() {
        val plan = FinanceEngine.calculate(profile(3000.0, 1000.0, 3000.0), emptyList())

        assertEquals(Status.HEALTHY, plan.status)
        assertEquals(0.0, plan.emergencyGap, 0.01)
        assertNotNull(plan.explanations)
    }

    private val start = LocalDate.of(2026, 10, 10)

    private fun datedGoal(target: Double = 1500.0, saved: Double = 0.0, deadline: LocalDate = start.plusMonths(5)) =
        Goal(
            id = 1, name = "Laptop", target = target, saved = saved,
            deadlineMillis = deadline.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

    @Test
    fun countdownAndContributionStayStableWithinCalendarMonth() {
        val goal = datedGoal()
        val p = profile(3400.0, 1500.0, 4500.0)
        listOf(start, start.plusDays(1), start.withDayOfMonth(31)).forEach { day ->
            assertEquals(5, FinanceEngine.monthsUntil(goal.deadlineMillis, day))
            assertEquals(300.0, FinanceEngine.calculate(p, listOf(goal), day).goalAllocations.single().required, 0.01)
        }
        assertEquals(4, FinanceEngine.monthsUntil(goal.deadlineMillis, LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun firstUseStartsCheckpointWithoutInventingPastDeposits() {
        val goal = datedGoal()
        val result = FinanceEngine.catchUpGoalProgress(
            profile(3400.0, 1500.0, 4500.0), listOf(goal), null, start
        )
        assertEquals(0.0, result.goals.single().saved, 0.01)
        assertEquals(YearMonth.from(start), result.processedMonth)
    }

    @Test
    fun nextMonthAppliesOneDepositAndKeepsMonthlyPlanStable() {
        val p = profile(3400.0, 1500.0, 4500.0)
        val goal = datedGoal()
        val next = start.plusMonths(1)
        val result = FinanceEngine.catchUpGoalProgress(p, listOf(goal), YearMonth.from(start), next)
        val before = FinanceEngine.calculate(p, listOf(goal), start)
        val after = FinanceEngine.calculate(p, result.goals, next)
        assertEquals(300.0, result.goals.single().saved, 0.01)
        assertEquals(4, FinanceEngine.monthsUntil(goal.deadlineMillis, next))
        assertEquals(300.0, after.goalAllocations.single().required, 0.01)
        assertEquals(before.longTermSavings, after.longTermSavings, 0.01)
        assertEquals(4500.0, p.savings, 0.01)
    }

    @Test
    fun repeatedRefreshDoesNotApplySameMonthTwice() {
        val p = profile(3400.0, 1500.0, 4500.0)
        val next = start.plusMonths(1)
        val first = FinanceEngine.catchUpGoalProgress(p, listOf(datedGoal()), YearMonth.from(start), next)
        val again = FinanceEngine.catchUpGoalProgress(p, first.goals, first.processedMonth, next.plusDays(1))
        assertEquals(first, again)
    }

    @Test
    fun missedMonthsAreAppliedInOrder() {
        val result = FinanceEngine.catchUpGoalProgress(
            profile(3400.0, 1500.0, 4500.0), listOf(datedGoal()), YearMonth.from(start), start.plusMonths(3)
        )
        assertEquals(900.0, result.goals.single().saved, 0.01)
        assertEquals(YearMonth.of(2027, 1), result.processedMonth)
    }

    @Test
    fun backwardClockDoesNotRewindCheckpointOrRepeatDeposits() {
        val p = profile(3400.0, 1500.0, 4500.0)
        val first = FinanceEngine.catchUpGoalProgress(p, listOf(datedGoal()), YearMonth.from(start), start.plusMonths(2))
        val backward = FinanceEngine.catchUpGoalProgress(p, first.goals, first.processedMonth, start)
        val forward = FinanceEngine.catchUpGoalProgress(p, backward.goals, backward.processedMonth, start.plusMonths(2))
        assertEquals(first, backward)
        assertEquals(first, forward)
    }

    @Test
    fun catchUpStopsAtGoalTarget() {
        val result = FinanceEngine.catchUpGoalProgress(
            profile(3400.0, 1500.0, 4500.0), listOf(datedGoal()), YearMonth.from(start), start.plusMonths(10)
        )
        assertEquals(1500.0, result.goals.single().saved, 0.01)
    }

    @Test
    fun dueThisMonthHasZeroCountdownButFiniteContribution() {
        val goal = datedGoal(deadline = start.plusDays(5))
        val plan = FinanceEngine.calculate(profile(3400.0, 1500.0, 4500.0), listOf(goal), start)
        assertEquals(0, FinanceEngine.monthsUntil(goal.deadlineMillis, start))
        assertEquals(1500.0, plan.goalAllocations.single().required, 0.01)
        assertTrue(plan.longTermSavings.isFinite())
    }

    @Test
    fun overdueGoalIsFlaggedEvenWhenRemainingAmountCanBeFunded() {
        val goal = datedGoal(target = 100.0, deadline = start.minusDays(1))
        val plan = FinanceEngine.calculate(profile(3400.0, 1500.0, 4500.0), listOf(goal), start)
        assertEquals(0, FinanceEngine.monthsUntil(goal.deadlineMillis, start))
        assertEquals(Status.NEEDS_ATTENTION, plan.status)
        assertTrue(plan.explanations.any { it.contains("overdue") })
    }

    @Test
    fun deficitMonthsAdvanceCheckpointWithoutAddingGoalSavings() {
        val result = FinanceEngine.catchUpGoalProgress(
            profile(1000.0, 1500.0, 0.0), listOf(datedGoal()), YearMonth.from(start), start.plusMonths(2)
        )
        assertEquals(0.0, result.goals.single().saved, 0.01)
        assertEquals(YearMonth.from(start.plusMonths(2)), result.processedMonth)
    }
}
