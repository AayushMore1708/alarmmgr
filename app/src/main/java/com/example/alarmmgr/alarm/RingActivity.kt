package com.example.alarmmgr.alarm

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmmgr.data.AlarmStore
import com.example.alarmmgr.data.clockParts
import com.example.alarmmgr.ui.theme.AlarmmgrTheme

/** Full-screen ringing screen, shown over the lock screen. */
class RingActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val isPreview = intent.getBooleanExtra("preview", false)
        AlarmStore.init(this)
        val alarm = AlarmStore.get(alarmId)
        val previewSound = intent.getStringExtra("preview_sound")
        val playbackUri: String? = if (isPreview) {
            previewSound?.ifBlank { null }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
        } else {
            alarm?.let { AlarmStore.effectiveSoundUri(it) }?.takeIf { it.startsWith("/") }
        }
        if (playbackUri != null) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    if (playbackUri.startsWith("content://")) {
                        setDataSource(this@RingActivity, Uri.parse(playbackUri))
                    } else {
                        setDataSource(playbackUri)
                    }
                    isLooping = true
                    if (isPreview) setVolume(0.5f, 0.5f)
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                player = null
            }
        }
        setContent {
            AlarmmgrTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    RingScreen(
                        time = intent.getStringExtra("preview_time") ?: alarm?.let {
                            val (h, m, p) = it.clockParts()
                            "$h:$m $p"
                        } ?: "--:--",
                        label = intent.getStringExtra("preview_label") ?: alarm?.label ?: "Alarm",
                    onSnooze = {
                        if (!isPreview && alarm != null) AlarmScheduler.snooze(this, alarm.id)
                        stopRinging(alarmId)
                    },
                    onDismiss = {
                        stopRinging(alarmId)
                    },
                    )
                }
            }
        }
    }

    private fun stopRinging(alarmId: Long) {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        AlarmReceiver.silence(this, alarmId)
        finish()
    }

    override fun onDestroy() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun RingScreen(time: String, label: String, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var showLock by remember { mutableStateOf(false) }
    var pwd by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ALARM", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text(time, fontSize = 84.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(24.dp))
        Text("\u0906\u092f\u0941\u0937\u0932\u093e \u0909\u0920\u0935\u093e, \u0911\u092b\u093f\u0938\u0932\u093e \u091c\u093e\u092f\u091a\u0902 \u0906\u0939\u0947!", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text("\u0906\u092f\u0941\u0937 \u0915\u094b \u0909\u0920\u093e\u0913, \u0911\u092b\u093f\u0938 \u091c\u093e\u0928\u093e \u0939\u0948!", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
        Text("Wake up, Aayush! It's time to go to the office!", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(48.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                Text("Snooze 9 min")
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { showLock = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Stop")
            }
        }
        if (showLock) {
            AlertDialog(
                onDismissRequest = { showLock = false; wrong = false },
                title = { Text("Enter the password to stop alarm") },
                text = {
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it.filter { c -> c.isDigit() }.take(8); wrong = false },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = wrong,
                        supportingText = { if (wrong) Text("Wrong password") },
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (pwd == "1234") { showLock = false; onDismiss() }
                        else wrong = true
                    }) { Text("Stop alarm") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showLock = false; wrong = false; pwd = "" }) { Text("Cancel") }
                },
            )
        }
    }
}
