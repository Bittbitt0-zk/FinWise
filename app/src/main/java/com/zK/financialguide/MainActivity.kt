/*
 * FinWise - 159.336 Assignment 3
 * Author: <Sai Som Kiat>
 *
 * A personal finance app for students and young adults. A deterministic, rule-based engine
 * (no AI, no network) turns income, essential expenses, savings, debt and goals into a monthly
 * plan, and explains every recommendation.
 *
 * All code in this project was written for this assignment. No code was reused from other
 * courses or projects.
 *
 * Kotlin class-limit note: the counted non-data project types are MainActivity,
 * FinanceViewModel, FinanceEngine (object), ReminderWorker, FinanceDao, FinanceDatabase,
 * and the Status/Tier/Verdict enums. Screens are @Composable functions, not classes.
 * Room entities and simple data classes have no custom methods and are excluded by the
 * assignment brief.
 *
 * External libraries (all declared as Gradle dependencies in build.gradle.kts):
 *  - Jetpack Compose and Material 3 - user interface
 *  - Room - local SQLite database for profile, expenses and goals
 *  - Navigation Compose - moving between screens
 *  - Lifecycle ViewModel / lifecycle-runtime-compose - lifecycle-aware state
 *  - WorkManager - schedules the monthly reminder
 *  - JUnit - unit tests for the financial engine
 *
 * Android platform features used: Room persistence, WorkManager background work, notifications
 * with the Android 13+ runtime permission, lifecycle-aware collection, edge-to-edge UI.
 * Resources are released when not in use: the database is closed in ViewModel.onCleared(),
 * Monthly expense collection uses WhileSubscribed sharing; the finance snapshot lives with
 * the ViewModel. WorkManager runs only when scheduled. Shared styling is in UiComponents.kt
 * and ui/theme; presentation does not change financial allocations or stored balances.
 */
package com.zK.financialguide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.zK.financialguide.ui.theme.FinWiseTheme

class MainActivity : ComponentActivity() {
    private val vm: FinanceViewModel by viewModels()
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.refreshCalendar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinWiseTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, dateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshCalendar()
    }

    override fun onStop() {
        unregisterReceiver(dateReceiver)
        super.onStop()
    }
}
