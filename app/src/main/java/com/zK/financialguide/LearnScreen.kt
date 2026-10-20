/** Static, locally stored financial education. */
package com.zK.financialguide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** One lesson in the Learn section. Static content, stored in the app. */
data class Lesson(val title: String, val summary: String, val body: String)

private val lessons = listOf(
    Lesson(
        "How FinWise Builds Your Plan",
        "Detect, analyze, prioritize, allocate, and explain.",
        "FinWise does not use one fixed budgeting rule for everyone. " +
                "It first calculates your monthly cash flow by subtracting essential expenses and minimum debt payments from your income. " +
                "It then calculates your cash-flow ratio to understand how much of your income remains available.\n\n" +
                "The app analyzes whether your cash flow is limited, moderate, strong, or negative. " +
                "It then considers your emergency fund, financial goals, and long-term savings needs before creating a monthly plan.\n\n" +
                "The general priority is to cover essential expenses and minimum debt payments first, then build an emergency fund, work toward financial goals, and allocate remaining money toward long-term savings and a reasonable lifestyle allowance.\n\n" +
                "The app adapts the plan to your actual financial situation instead of blindly applying rules such as 50/30/20. " +
                "Every recommendation is calculated from your information and explained so you can understand why it was made."
    ),

    Lesson(
        "Emergency Funds",
        "Build a financial safety net for unexpected expenses.",
        "An emergency fund is money set aside for unexpected expenses or a temporary loss of income. " +
                "It can help you handle situations such as a medical expense, urgent repair, or period without income without relying on new debt.\n\n" +
                "FinWise lets you choose your preferred level of protection: 3 months (Basic), 4 months (Moderate), or 6 months (Strong) of essential expenses.\n\n" +
                "The app calculates your emergency-fund target automatically using your essential monthly expenses. " +
                "It then compares that target with your current emergency savings to determine how much is still needed.\n\n" +
                "You do not need to calculate the gap or decide how much to save each month. FinWise considers your available cash flow and the remaining gap to create an affordable contribution and estimate how long it may take to reach your target.\n\n" +
                "If your emergency fund is already complete, the app stops allocating money toward it and can redirect available money toward other financial priorities."
    ),

    Lesson(
        "Understanding Your Cash Flow",
        "Know how much money is actually available each month.",
        "Cash flow is the amount of money left after your essential expenses and minimum debt payments are covered.\n\n" +
                "FinWise calculates it as:\n\n" +
                "Cash Flow = Income − Essential Expenses − Minimum Debt Payment\n\n" +
                "The app also calculates your cash-flow ratio by comparing your available cash flow with your income. " +
                "This helps the app understand how much financial flexibility you have.\n\n" +
                "A positive cash flow means money is available for financial priorities. " +
                "A cash flow close to zero means there is little room for additional saving. " +
                "A negative cash flow means your current expenses are greater than your income and the priority should be improving your financial position before trying to save aggressively.\n\n" +
                "Understanding cash flow is important because you cannot sustainably save money that you do not actually have available."
    ),

    Lesson(
        "Budgeting",
        "Give every part of your income a purpose.",
        "A budget is a plan for how you will use your income. It helps you understand what must be paid, what you want to save, and how much you can reasonably spend.\n\n" +
                "Rules such as 50/30/20 can be useful starting points, but they do not work equally well for everyone. " +
                "Someone with high essential expenses has a very different financial situation from someone with low expenses and a large surplus.\n\n" +
                "FinWise therefore uses your actual income, expenses, debt, savings, and goals instead of forcing one fixed percentage on every user.\n\n" +
                "A budget should also be compared with your real spending. " +
                "That is why FinWise lets you record expenses and compare your actual spending with your planned allowance."
    ),

    Lesson(
        "Saving",
        "Turn available cash flow into financial progress.",
        "Saving means keeping part of your available money instead of spending it. " +
                "The purpose of saving depends on what the money is for.\n\n" +
                "Emergency savings protect you from unexpected financial problems. " +
                "Goal savings are reserved for something specific, such as a laptop or a trip. " +
                "Long-term savings are intended for future needs and larger financial objectives.\n\n" +
                "FinWise does not assume that everyone should save the same percentage of income. " +
                "The recommended amount depends on your actual cash flow, existing savings, emergency-fund progress, and financial goals.\n\n" +
                "The goal is to create a saving plan that is realistic enough to maintain while still helping you make meaningful financial progress."
    ),

    Lesson(
        "Debt",
        "Understand how borrowing affects your financial freedom.",
        "Debt is money that you have borrowed and must repay, usually with interest. " +
                "Monthly debt payments reduce the amount of money available for saving and other financial priorities.\n\n" +
                "The minimum payment is the amount you need to pay to keep the account in good standing. " +
                "Missing required payments can lead to additional costs and other financial consequences.\n\n" +
                "FinWise includes your minimum debt payment when calculating your available cash flow because this payment is an important financial obligation.\n\n" +
                "If you have additional available cash flow, paying down high-interest debt can be an important financial priority. " +
                "The right approach depends on the type of debt, its interest rate, and your overall financial situation."
    ),

    Lesson(
        "Interest & Compound Interest",
        "Understand how interest can work against you or for you.",
        "Interest is the cost of borrowing money or the return earned on money that you save.\n\n" +
                "For example, if a $1,000 debt has a 20% annual interest rate and the balance remained unchanged, it could generate about $200 of interest over one year before considering fees or other factors.\n\n" +
                "Compound interest means that previously earned interest can itself begin earning interest. " +
                "Over long periods, this can make a significant difference to savings and investments.\n\n" +
                "The same principle can work against you when borrowing. " +
                "High-interest debt can grow quickly when balances remain unpaid.\n\n" +
                "Understanding interest helps you make better decisions about borrowing, saving, and the long-term cost of financial choices."
    ),

    Lesson(
        "Needs vs Wants",
        "Separate essential spending from spending you can control.",
        "Needs are expenses that are necessary for living, working, or studying, such as housing, basic food, utilities, transportation, and required debt payments.\n\n" +
                "Wants are expenses that improve your lifestyle but can usually be reduced, delayed, or avoided, such as entertainment, takeaway food, subscriptions, shopping, or optional upgrades.\n\n" +
                "The difference is not always completely clear. For example, food is a need, but expensive takeaway meals may contain a large discretionary component.\n\n" +
                "FinWise allows you to identify whether an expense is essential. This helps the app understand your actual financial commitments and compare discretionary spending with your lifestyle allowance.\n\n" +
                "When your cash flow needs improvement, reviewing wants is often a practical place to look for spending that can be reduced."
    ),

    Lesson(
        "Financial Goals",
        "Turn something you want into a measurable plan.",
        "A financial goal gives your savings a specific purpose. Examples include buying a laptop, paying for education, taking a trip, or saving for a major purchase.\n\n" +
                "FinWise uses three main pieces of information: your target amount, how much you have already saved, and your deadline.\n\n" +
                "The app calculates the amount remaining and the monthly contribution required to reach the goal by the deadline.\n\n" +
                "For example, if you have $1,000 remaining and 8 months left, the basic required contribution is $125 per month.\n\n" +
                "If you have multiple goals, the app considers their combined requirements. If your current cash flow cannot support all of them, FinWise will show that the goals are not currently affordable rather than pretending that they are.\n\n" +
                "You can then consider lowering a target, extending a deadline, reducing spending, or increasing available income."
    ),

    Lesson(
        "Long-Term Savings",
        "Prepare your money for future needs.",
        "Long-term savings are money set aside for financial needs that are further in the future, such as further education, a home, retirement, or other major goals.\n\n" +
                "Long-term financial planning is different from emergency savings. An emergency fund is designed to be available when something unexpected happens, while long-term savings are intended to support future objectives.\n\n" +
                "FinWise prioritizes establishing financial stability and addressing current commitments before directing significant remaining money toward long-term savings.\n\n" +
                "Once higher-priority needs, emergency savings, and current goals are adequately funded, remaining available money can be directed toward long-term savings.\n\n" +
                "Regular contributions can make a significant difference over many years, especially when savings or investments generate returns that are reinvested."
    ),

    Lesson(
        "Responsible Spending & Can I Afford This?",
        "Make purchases without losing sight of your financial priorities.",
        "Being able to pay for something today does not always mean that the purchase is financially comfortable. A large purchase can reduce your savings, delay an important goal, or slow down your emergency-fund progress.\n\n" +
                "Before making a purchase, consider whether it is necessary, whether you can comfortably afford it, and what other financial priority might be delayed.\n\n" +
                "FinWise's Can I Afford This? feature considers your current savings, emergency-fund progress, monthly cash flow, debt, financial goals, lifestyle allowance, and the price of the purchase.\n\n" +
                "The app can return four types of guidance: YES, YES BUT, NOT YET, or NO.\n\n" +
                "For example, a purchase may be affordable but delay your emergency-fund target by several months. In that case, the app should explain the impact instead of simply saying that you can or cannot afford it.\n\n" +
                "The purpose is not to stop you from spending money. It is to help you understand the financial consequences before you decide."
    )
)

@Composable
fun LearnScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Learn", "Money essentials")
        }
        items(lessons, key = { it.title }) { lesson -> LessonCard(lesson) }
    }
}

@Composable
private fun LessonCard(lesson: Lesson) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse lesson" else "Expand lesson") {
                    expanded = !expanded
                }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lesson.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(lesson.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                HorizontalDivider()
                Text(lesson.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
