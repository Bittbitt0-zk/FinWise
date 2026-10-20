/** Switch to schedule the monthly reminder and handle the notification permission. */
package com.zK.financialguide

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

@Composable
fun ReminderCard() {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    // The switch reflects whether the monthly job is really scheduled
    val workFlow = remember { workManager.getWorkInfosForUniqueWorkFlow(REMINDER_WORK_NAME) }
    val infos by workFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabled = infos.any { !it.state.isFinished }

    var blocked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            blocked = false
            scheduleReminder(context)
        } else {
            blocked = true
        }
    }

    FinanceSection("Monthly check-in") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remind me once a month", fontWeight = FontWeight.SemiBold)
                Text(
                    "A notification to update your savings and review your plan.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { turnOn ->
                    if (!turnOn) {
                        cancelReminder(context)
                    } else if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        blocked = false
                        scheduleReminder(context)
                    }
                }
            )
        }
        if (blocked) {
            Text(
                "Notifications are blocked for this app. Allow them in your phone's app settings to use reminders.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (enabled) {
            TextButton(onClick = {
                workManager.enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
            }) { Text("Send a test reminder now") }
        }
    }
}
