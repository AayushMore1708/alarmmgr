package com.example.alarmmgr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.alarmmgr.data.Alarm
import com.example.alarmmgr.data.WEEKDAY_CHIPS
import java.util.Calendar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private fun defaultTime(): Pair<Int, Int> {
    val cal = Calendar.getInstance()
    val minutes = cal.get(Calendar.MINUTE) + 1
    val carry = if (minutes >= 60) 1 else 0
    return ((cal.get(Calendar.HOUR_OF_DAY) + carry) % 24) to (minutes % 60)
}

@Composable
fun AddAlarmSheet(
    existing: Alarm? = null,
    onClose: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, days: Set<Int>) -> Unit,
    onDelete: () -> Unit = {},
) {
    val (defHour, defMinute) = remember { defaultTime() }
    var hour by remember { mutableIntStateOf(existing?.hour ?: defHour) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: defMinute) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var hourField by remember { mutableStateOf("") }
    var minuteField by remember { mutableStateOf("") }

    val previewHour = if (hour % 12 == 0) 12 else hour % 12
    val period = if (hour < 12) "AM" else "PM"

    fun commitHour(text: String) {
        hourField = text.filter { it.isDigit() }.take(2)
        hourField.toIntOrNull()?.let {
            val h12 = if (it == 0) 12 else it.coerceIn(1, 12)
            hour = (h12 % 12) + (if (hour < 12) 0 else 12)
        }
    }

    fun commitMinute(text: String) {
        minuteField = text.filter { it.isDigit() }.take(2)
        minuteField.toIntOrNull()?.let { minute = it.coerceIn(0, 59) }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Image(
                painter = painterResource(id = R.drawable.alarm_art),
                contentDescription = "Alarm",
                modifier = Modifier.size(120.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onClose) { Text("Cancel") }
                Text(if (existing == null) "New alarm" else "Edit alarm", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    onSave(hour, minute, label.ifBlank { "Alarm" }, days)
                    onClose()
                }) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance()
                    hour = cal.get(Calendar.HOUR_OF_DAY)
                    minute = cal.get(Calendar.MINUTE)
                    hourField = ""
                    minuteField = ""
                }) { Text("Now") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hourField.ifEmpty { previewHour.toString().padStart(2, '0') },
                    onValueChange = { commitHour(it) },
                    modifier = Modifier.width(120.dp),
                    textStyle = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(":", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = minuteField.ifEmpty { minute.toString().padStart(2, '0') },
                    onValueChange = { commitMinute(it) },
                    modifier = Modifier.width(120.dp),
                    textStyle = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { hour = (hour + 12) % 24 }) { Text(period) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WEEKDAY_CHIPS.forEach { (day, letter) ->
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(letter) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (days.isEmpty()) "Rings once" else "Repeats on " + days.size + " day(s)",
                style = MaterialTheme.typography.bodySmall,
            )
            if (existing != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onDelete(); onClose() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete alarm")
                }
            }
            }
        }
    }
}
