package com.zK.financialguide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AffordTest {

    private fun setup(savings: Double = 0.0, income: Double = 3400.0, essentials: Double = 1500.0): Pair<Profile, Plan> {
        val p = Profile(
            income = income, essentials = essentials, savings = savings,
            debtBalance = 0.0, minDebtPayment = 0.0, emergencyMonths = 3
        )
        return p to FinanceEngine.calculate(p, emptyList())
    }

    private fun verdict(
        setup: Pair<Profile, Plan>, price: Double, recurring: Boolean = false, spent: Double = 0.0
    ) = FinanceEngine.canAfford(setup.first, setup.second, price, recurring, spent).verdict

    @Test fun smallPurchaseFitsLifestyle() =
        assertEquals(Verdict.YES, verdict(setup(), 50.0))

    @Test fun bigPurchaseNotYet() =         // Shortfall after lifestyle budget takes about 1 month
        assertEquals(Verdict.NOT_YET, verdict(setup(), 1000.0))

    @Test fun hugePurchaseIsNo() =          // Waiting would take more than a year
        assertEquals(Verdict.NO, verdict(setup(), 20000.0))

    @Test fun lifestylePlusExtraSavingsIsSafeToUse() =
        assertEquals(Verdict.YES, verdict(setup(savings = 5000.0), 1000.0))

    @Test fun waitsBeforeTouchingEmergencySavings() =
        assertEquals(Verdict.NOT_YET, verdict(setup(savings = 5000.0), 1500.0))

    @Test fun emergencySavingsIsYesButWhenCleanWaitIsTooLong() =
        assertEquals(
            Verdict.YES_BUT,
            verdict(setup(savings = 5000.0, income = 1600.0, essentials = 1500.0), 1200.0)
        )

    @Test fun deficitIsAlwaysNo() =
        assertEquals(Verdict.NO, verdict(setup(income = 3000.0, essentials = 3000.0), 10.0))

    @Test fun recurringCases() {
        val s = setup()                      // long-term savings 848, emergency contribution 500
        assertEquals(Verdict.YES, verdict(s, 100.0, recurring = true))
        assertEquals(Verdict.YES, verdict(s, 600.0, recurring = true))
        assertEquals(Verdict.YES_BUT, verdict(s, 1200.0, recurring = true))
        assertEquals(Verdict.NOT_YET, verdict(s, 1800.0, recurring = true))
        assertEquals(Verdict.NO, verdict(s, 2000.0, recurring = true))
    }

    @Test fun overspendingBeyondCashFlowDoesNotApproveNewMonthlyCost() {
        assertEquals(Verdict.NO, verdict(setup(savings = 5000.0), 100.0, recurring = true, spent = 2000.0))
    }

    @Test fun overspendingReducesAvailableLongTermAllocation() {
        val (p, plan) = setup(savings = 5000.0)
        val budget = FinanceEngine.affordBudget(p, plan, 1000.0)
        assertEquals(348.0, budget.overspent, 0.01)
        assertEquals(900.0, budget.longTermAvailable, 0.01)
        val result = FinanceEngine.canAfford(p, plan, 600.0, true, 1000.0)
        assertEquals(Verdict.YES_BUT, result.verdict)
        assertEquals(300.0, result.details.single { it.label == "Long-term savings left" }.amount, 0.01)
    }

    @Test fun overspendingBeyondMonthlySavingsReservesExistingSavings() {
        val (p, plan) = setup(savings = 5000.0)
        val budget = FinanceEngine.affordBudget(p, plan, 2000.0)
        assertEquals(4900.0, budget.savingsBalance, 0.01)
        assertEquals(400.0, budget.extraSavings, 0.01)
        val result = FinanceEngine.canAfford(p, plan, 500.0, false, 2000.0)
        assertEquals(Verdict.YES_BUT, result.verdict)
        assertEquals(100.0, result.details.single { it.label == "Emergency savings used" }.amount, 0.01)
    }

    @Test fun underfundedGoalsPreventFalseEmergencyCompletionPromise() {
        val now = LocalDate.of(2026, 10, 1)
        val (p, _) = setup()
        val goal = Goal(id = 1, name = "Long goal", target = 28000.0, saved = 0.0,
            deadlineMillis = now.plusMonths(20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val plan = FinanceEngine.calculate(p, listOf(goal), now)
        val result = FinanceEngine.canAfford(p, plan, 800.0, true, 0.0, listOf(goal), now)
        assertEquals(Verdict.NO, result.verdict)
        assertEquals(null, result.waitMonths)
    }

    @Test fun completedGoalCanFreeMonthlyRoomInForecast() {
        val now = LocalDate.of(2026, 10, 1)
        val (p, _) = setup(savings = 5000.0)
        val goal = Goal(id = 1, name = "Laptop", target = 1500.0, saved = 0.0,
            deadlineMillis = now.plusMonths(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val plan = FinanceEngine.calculate(p, listOf(goal), now)
        val result = FinanceEngine.canAfford(p, plan, 1700.0, true, 0.0, listOf(goal), now)
        assertEquals(Verdict.NOT_YET, result.verdict)
        assertEquals(2, result.waitMonths)
        assertEquals(0.0, goal.saved, 0.01)
        assertEquals(5000.0, p.savings, 0.01)
    }

    @Test fun forecastDoesNotPretendEmergencyDepositsWereRecorded() {
        val (p, plan) = setup()
        val result = FinanceEngine.canAfford(p, plan, 1800.0, true, 0.0)
        assertEquals(Verdict.NOT_YET, result.verdict)
        assertEquals(9, result.waitMonths)
        assertEquals(0.0, p.savings, 0.01)
    }

    @Test fun receiptSourceAmountsAddUpToPurchasePrice() {
        val (p, plan) = setup(savings = 5000.0)
        val result = FinanceEngine.canAfford(p, plan, 1000.0, false, 0.0)
        assertEquals(Verdict.YES, result.verdict)
        assertEquals(1000.0, result.details.filter { it.label.startsWith("From ") }.sumOf { it.amount }, 0.01)
        assertEquals(4652.0, result.details.single { it.label == "Savings left afterward" }.amount, 0.01)
    }

    @Test fun emergencyRebuildUsesRecommendationAfterPurchase() {
        val (p, plan) = setup(savings = 5000.0, income = 1600.0)
        val result = FinanceEngine.canAfford(p, plan, 1200.0, false, 0.0)
        assertEquals(Verdict.YES_BUT, result.verdict)
        assertEquals(30.0, result.details.single { it.label == "Revised emergency saving" }.amount, 0.01)
        assertTrue(result.reason.contains("22 months"))
    }

    @Test fun invalidPurchasePricesAreRejected() {
        val (p, plan) = setup()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -10.0, 0.0, 0.001).forEach { price ->
            assertEquals(Verdict.NO, FinanceEngine.canAfford(p, plan, price, false, 0.0).verdict)
        }
    }
}
