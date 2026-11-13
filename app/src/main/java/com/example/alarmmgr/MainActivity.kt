package com.example.alarmmgr

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.alarmmgr.alarm.AlarmReceiver
import com.example.alarmmgr.alarm.AlarmScheduler
import com.example.alarmmgr.alarm.RingActivity
import com.example.alarmmgr.data.Alarm
import com.example.alarmmgr.data.AlarmStore
import com.example.alarmmgr.data.clockParts
import com.example.alarmmgr.data.nextUpcoming
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.example.alarmmgr.data.repeatLabel
import com.example.alarmmgr.ui.theme.AlarmmgrTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmStore.init(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            AlarmmgrTheme(darkTheme = true) {
                AlarmScreen(
                    canScheduleExact = canScheduleExact(),
                    onRequestExact = ::openExactAlarmSettings,
                    canFullScreen = canUseFullScreen(),
                    onRequestFullScreen = ::openFullScreenSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setContent {
            AlarmmgrTheme(darkTheme = true) {
                AlarmScreen(
                    canScheduleExact = canScheduleExact(),
                    onRequestExact = ::openExactAlarmSettings,
                    canFullScreen = canUseFullScreen(),
                    onRequestFullScreen = ::openFullScreenSettings,
                )
            }
        }
    }

    private fun canUseFullScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
    }

    private fun openFullScreenSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:" + packageName)
        })
    }

    private fun canScheduleExact(): Boolean {
        val manager = getSystemService(AlarmManager::class.java) ?: return true
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    private fun openExactAlarmSettings() {
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:" + packageName)
        })
    }
}

@Composable
private fun AlarmScreen(canScheduleExact: Boolean, onRequestExact: () -> Unit, canFullScreen: Boolean, onRequestFullScreen: () -> Unit) {
    val alarms by AlarmStore.alarms.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000)
                    now = System.currentTimeMillis()
                }
    }
    val next = remember(alarms, now) { nextUpcoming(alarms, now) }
    var editing by remember { mutableStateOf<Alarm?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }) { Text("+", fontSize = 28.sp) }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Alarms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    alarms.count { it.enabled }.toString() + " of " + alarms.size + " on",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (!canScheduleExact) {
                Button(onClick = onRequestExact, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow exact alarms for on-time ringing")
                }
                Spacer(Modifier.height(12.dp))
            }
            if (!canFullScreen) {
                Button(onClick = onRequestFullScreen, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow full-screen alarms (ring over other apps)")
                }
                Spacer(Modifier.height(12.dp))
            }
            LiveClockCard(now = now)
            Spacer(Modifier.height(8.dp))
            DefaultSoundRow()
            Spacer(Modifier.height(8.dp))
            val context = LocalContext.current
            OutlinedButton(
                onClick = {
                    val sample = next?.first
                    val (sh, sm, sp) = sample?.clockParts() ?: Triple("--", "--", "")
                    context.startActivity(
                        Intent(context, RingActivity::class.java)
                            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, sample?.id ?: -1L)
                            .putExtra("preview", true)
                            .putExtra("preview_time", sh + ":" + sm + (if (sp.isBlank()) "" else " " + sp))
                            .putExtra("preview_label", sample?.label ?: "Sample")
                            .putExtra("preview_sound", sample?.let { AlarmStore.effectiveSoundUri(it) } ?: AlarmStore.defaultSoundUri())
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Show sample: how alarm will ring")
            }
            Spacer(Modifier.height(12.dp))
            if (alarms.isEmpty()) {
                Text("No alarms yet - tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { AlarmStore.toggle(alarm.id) },
                            onEdit = { editing = alarm; showSheet = true },
                        )
                    }
                }
            }
        }
    }
    if (showSheet) {
        AddAlarmSheet(
            existing = editing,
            onClose = { showSheet = false; editing = null },
            onSave = { h, m, label, days ->
                val target = editing
                if (target == null) AlarmStore.add(h, m, label, days)
                else AlarmStore.update(target.id, h, m, label, days)
            },
            onDelete = { editing?.let { AlarmStore.remove(it.id) } },
        )
    }
}

@Composable
private fun LiveClockCard(now: Long) {
    val zone = remember { ZoneId.of("Asia/Kolkata") }
    val time = remember(now) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            .format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
    }
    val date = remember(now) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("LIVE - IST", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
            Text(time, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Text(date + " - Asia/Kolkata", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(alarm: Alarm, onToggle: () -> Unit, onEdit: () -> Unit) {
    val (h, m, p) = alarm.clockParts()
    Card(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (alarm.enabled) 1f else 0.5f)
            .combinedClickable(onClick = {}, onLongClick = onEdit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(h + ":" + m, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(p, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(alarm.label + " - " + alarm.repeatLabel(), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = alarm.enabled, onCheckedChange = { onToggle() })
        }
    }
}
