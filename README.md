# FinWise

FinWise is an Android personal-finance app for 159.336 Assignment 3. It helps students and young adults turn income, essential expenses, savings, debt payments, goals, and spending into a practical monthly plan.

## Main Features

- First-run profile setup for monthly income, essential expenses, existing savings, debt, and emergency-fund preference.
- Dashboard showing cash flow, emergency-fund progress, planned allocations, lifestyle budget, and spending checks.
- Expense tracking with essential/non-essential categories.
- Goal planning with deadline-based affordability checks.
- "Can I afford this?" purchase advice for one-time and recurring spending.
- Learn section with local financial education content.
- Optional monthly reminder using WorkManager and Android notifications.

## Android Platform Features Used

- Jetpack Compose and Material 3 for the UI.
- Navigation Compose for bottom navigation and the monthly plan route.
- Room for local SQLite persistence.
- Lifecycle ViewModel and lifecycle-aware collection for app state.
- WorkManager for monthly reminder scheduling.
- Android 13+ notification permission handling.

## Project Structure

- `MainActivity.kt` starts Compose, documents originality, libraries, class-count notes, and platform use.
- `AppRoot.kt` owns navigation and bottom-bar routing.
- `FinanceEngine.kt` contains the deterministic rule-based planning and affordability logic.
- `FinanceViewModel.kt` connects Room data, automatic monthly goal progress, and UI state. One committed `FinanceState` holds the profile, goals, calendar, and calculated plan. Screens collect this snapshot rather than mixing independently refreshed balances and plans.
- `FinanceDb.kt` and `Entities.kt` define the local database, DAO, and Room entities.
- Screen files (`DashboardScreen.kt`, `PlanScreen.kt`, `ExpensesScreen.kt`, `GoalsScreen.kt`, `AffordScreen.kt`, `LearnScreen.kt`, `SetupScreen.kt`) contain the Compose UI.
- `UiComponents.kt` provides shared screen headers, summary panels, unframed sections, complete monetary text, metrics, status badges, and progress lines. The `ui/theme` files own the palette, type scale, and shapes.
- `ReminderWorker.kt` and `ReminderCard.kt` handle the optional monthly reminder.
- Unit tests in `app/src/test` cover the financial engine and afford logic.
- Instrumented tests in `app/src/androidTest` cover persistence, calendar refresh, and purchase-screen interactions with isolated data.

## Originality And Libraries

All app code was written for this assignment. No code was reused from another course, book, tutorial, or online repository.

External libraries are declared as Gradle dependencies:

- Jetpack Compose / Material 3
- Room
- Navigation Compose
- Lifecycle ViewModel and lifecycle-runtime-compose
- WorkManager
- JUnit

## Class-Count Note

The assignment limit is 10 Kotlin classes for an individual submission, excluding simple data classes, theme classes, database entities, and library classes. The project stays under that limit. The counted non-data project types are:

- `MainActivity`
- `FinanceViewModel`
- `FinanceEngine` object
- `ReminderWorker`
- `FinanceDao` interface
- `FinanceDatabase`
- `Status`, `Tier`, and `Verdict` enums

Simple data classes such as `Profile`, `Expense`, `Goal`, `Plan`, and UI helper state models are intentionally kept method-free and are not counted under the assignment's stated exclusions.

## Resource Management

- The Room database is closed in `FinanceViewModel.onCleared()`.
- The expense query uses `SharingStarted.WhileSubscribed(5000)` so collection stops shortly after the UI is not visible. The financial snapshot and Room invalidation observer live with the ViewModel.
- The monthly goal-progress collector runs only while the ViewModel exists, and the database is released when the ViewModel is cleared.
- Manual savings deposits commit to Room before the ViewModel publishes the new balance and its recalculated plan together. Home's amount, progress, status, and allocation update without reopening or refreshing. Emergency savings remain manual; this does not create automatic emergency deposits.
- WorkManager reminder work only runs when the user enables it.
- The app uses local storage only and does not depend on network access.
- The date-change receiver is registered while the activity is visible and removed when it stops.

## Monthly Goal Progress

- Emergency savings use manual deposits. Automatic progress only updates goals.
- Each completed calendar month contributes the amount allocated by the plan. For example, a $1,500 goal due five months from October has $300 applied when November starts, leaving $1,200 over four months.
- Returning to the app or changing the date, time, or time zone refreshes the calendar and catches up missed months without requiring an app restart.
- Goal balances and the last processed month are saved in one Room transaction, preventing repeated contributions on refresh or restart. Moving the clock backward preserves the checkpoint and balances.
- Deadlines stay fixed. Countdown months decrease at calendar-month boundaries; goals due this month and overdue goals have explicit statuses.
- These automatic contributions assume the planned money was set aside. Catch-up uses the stored profile and goals; the app does not connect to a bank or reconstruct historical income changes.
- Database version 2 adds the checkpoint table while preserving existing data and importing the old preferences checkpoint on first use.

## Purchase Advice And Screen Design

- `AffordScreen` collects live profile, plan, goals, spending, and calendar data. `AffordContent` receives these as plain parameters so previews and UI tests do not need the user's database.
- The budget summary, one-time/monthly segmented control, purchase form, and result have separate composable functions. Result cards use an icon and explicit recommendation text as well as colour; explanations stay on normal surfaces for light/dark readability.
- Purchase input and check intent survive configuration changes. Checked advice is computed again from live financial data. Editing the price, name, or purchase type clears the previous result. Checking hides the keyboard and brings the result into view.
- The screen uses `FinanceEngine.affordBudget` for available-money figures. Overspending uses the planned long-term allocation first, then reserves any uncovered amount against existing savings.
- One-time spending can use remaining lifestyle money and savings above the emergency target. Monthly spending uses remaining lifestyle money and the available long-term allocation, never an existing savings balance to justify an ongoing cost.
- Monthly waiting advice projects at most 12 months, recalculating goals and emergency contributions each month. The estimate assumes the current profile and spending level continue and that planned deposits are made. It is withheld if spending cuts into protected contributions or active goal data is missing.
- Forecasts are read-only: simulating an emergency deposit does not record it. The app does not buy anything or add an expense when the user checks a price.
- `AffordResult` supplies numeric `AffordDetail` rows and an optional waiting period. The UI never extracts money or timing by parsing an explanation string. Monetary labels use Compose text auto-sizing to fit narrow columns without hiding digits.
- Validation rejects non-finite prices and prices below $0.01. The form supports keyboard Next/Done actions, a clear-price button, and direct navigation to the monthly plan.

## Shared Visual Theme

- The light-mode brand accent is `#505D87`, sampled from the Afford amount. It is fixed rather than derived from Android wallpaper, so available balances, buttons, selected navigation, and progress keep the same colour on every device. Dark mode uses a lighter companion blue for contrast.
- All screens use the same Material typography with zero letter spacing, 16 dp page margins, compact headings, and 8 dp card/text-field corners. Switches and segmented selectors retain familiar Material shapes. Summary tools use neutral `surfaceContainerLow`; normal page sections are unframed. Completed goals and emergency funds keep neutral backgrounds rather than becoming separate green themes.
- Blue emphasizes available cash flow, spending room, savings progress, and monthly allocations. Green, amber, and red retain financial meaning through readable status text; over-budget amounts remain red rather than appearing safely available.
- `ScreenHeader`, `FinancePanel`, `FinanceSection`, `MoneyText`, `FinanceMetric`, `LabelRow`, `StatusPill`, and `ProgressBar` keep styling in one place. `MoneyText` fits full monetary values within its layout, while screen headers and ordinary labels can wrap. These functions have no database or allocation side effects.
- Home, Expenses, Goals, Afford, the monthly plan, Learn, and profile/setup share these conventions. Expense and goal dialogs scroll when space is limited; lesson disclosures have accessibility roles and expanded/collapsed state. The monthly plan and profile include explicit back navigation.
- Expenses and Goals pin their add actions beside the header so they remain reachable while records scroll. Selection controls use the same blue highlight as navigation and available-money figures.
- `ThemeScreensTest` checks the sampled colour, remaining-expense arithmetic, all tabs, plan/profile navigation, lesson disclosures, dialogs, dark mode, and a 320 dp layout at 140% text scale. Screenshots use an isolated in-memory database, not the user's stored finances.
- This redesign does not change the finance engine, database schema, manual emergency deposits, or automatic monthly goal contributions.

## Build And Verification

From the project root:

```bash
./gradlew test assembleDebug
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

Run these commands before exporting the final Android Studio zip. The project should have no build errors; dependency-version availability warnings from lint are informational and do not indicate broken app behavior.

The connected tests require an emulator or Android device. They cover monthly idempotency, missed months, backward clock changes, transaction rollback, database migration, month-specific expenses, immediate manual-savings updates, and Home's actual savings dialog using isolated test databases. Afford UI tests exercise validation, live advice, one-time/monthly modes, plan navigation, dark mode, and a narrow layout with enlarged text; they render fixture values without editing financial records.

`gradle.properties` sets `android.injected.androidTest.leaveApksInstalledAfterRun=true` so connected-test cleanup leaves the app installed. Prefer a dedicated test emulator for development checks. The AndroidX project uses [the same setting](https://android.googlesource.com/platform/frameworks/support/+/eb74b1a4b3527414639994574a201f125aff90f5) to preserve test installations and outputs.
