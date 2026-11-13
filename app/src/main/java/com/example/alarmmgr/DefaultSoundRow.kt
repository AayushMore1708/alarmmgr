package com.example.alarmmgr

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alarmmgr.data.AlarmStore

/** Global default tone used by every alarm. System picker + audio-file picker. */
@Composable
fun DefaultSoundRow() {
    val context = LocalContext.current
    val defaultSound by AlarmStore.defaultSound.collectAsState()
    val (soundUri, soundTitle) = defaultSound

    fun applyPicked(uri: Uri?, title: String?) {
        if (uri == null) {
            AlarmStore.setDefaultSound("", "Silent")
        } else {
            AlarmStore.setDefaultSound(uri.toString(), title ?: uri.toString())
        }
    }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            if (uri != null) {
                applyPicked(uri, RingtoneManager.getRingtone(context, uri)?.getTitle(context))
            } else {
                applyPicked(null, null)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "tone_" + System.currentTimeMillis() + ".mp3"
                val dest = java.io.File(java.io.File(context.filesDir, "tones").apply { mkdirs() }, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                AlarmStore.setDefaultSound(dest.absolutePath, name)
            } catch (_: Exception) {
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val existing: Uri? = if (soundUri.isBlank()) null else Uri.parse(soundUri)
                systemPicker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Default alarm sound")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                    }
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("Sound: " + soundTitle)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
            Text("File")
        }
    }
}
