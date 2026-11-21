package com.vknxworks.talkctrl.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.talkctrl.BuildConfig
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.data.settings.AppSettings
import com.vknxworks.talkctrl.data.settings.SettingsFile
import com.vknxworks.talkctrl.ui.components.CtrlAlertDialog
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.lock.AppLock
import com.vknxworks.talkctrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Profiles and settings to a file and back. Importing shows what would be replaced and changes nothing until you
 * confirm; it never touches the remembered unit or the app lock.
 */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    var exportProfiles by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<Pair<SettingsFile, List<String>>?>(null) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val profiles = exportProfiles
        c.appScope.launch {
            val s = c.settings.settings.value
            val now = OffsetDateTime.now().toString()
            val file = if (profiles) SettingsFile.profilesOf(s, BuildConfig.VERSION_NAME, now) else SettingsFile.settingsOf(s, BuildConfig.VERSION_NAME, now)
            val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(SettingsFile.encode(file).toByteArray()) } != null }.getOrDefault(false)
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(if (ok) R.string.file_saved else R.string.file_save_failed), Toast.LENGTH_SHORT).show() }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.appScope.launch {
            val file = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytes(64 * 1024 + 1)
                    if (bytes.size > 64 * 1024) null else SettingsFile.decode(bytes.decodeToString())
                }
            }.getOrNull()
            val cur = c.settings.settings.value
            val next = file?.applyTo(cur)
            withContext(Dispatchers.Main) {
                when {
                    file == null || next == null -> Toast.makeText(context, context.getString(R.string.file_invalid), Toast.LENGTH_LONG).show()
                    next == cur -> Toast.makeText(context, context.getString(R.string.file_same), Toast.LENGTH_SHORT).show()
                    else -> pending = file to changes(context, cur, next)
                }
            }
        }
    }
    fun export(profiles: Boolean) {
        exportProfiles = profiles
        AppLock.expectReturn()
        exportPicker.launch((if (profiles) "talkctrl-profiles-" else "talkctrl-settings-") + LocalDate.now() + ".json")
    }
    fun import() {
        AppLock.expectReturn()
        importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    Group(stringResource(R.string.group_backup), footer = stringResource(R.string.backup_footer)) {
        GroupRow(stringResource(R.string.export_profiles), stringResource(R.string.export_profiles_sub), icon = Icons.Outlined.Groups, showChevron = true, onClick = { export(true) })
        GroupRow(stringResource(R.string.export_settings), stringResource(R.string.export_settings_sub), icon = Icons.Outlined.FileUpload, showChevron = true, onClick = { export(false) })
        GroupRow(stringResource(R.string.import_file), stringResource(R.string.import_file_sub), icon = Icons.Outlined.FileDownload, showChevron = true, onClick = ::import)
    }

    pending?.let { (file, names) ->
        CtrlAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.import_body, names.joinToString("\n") { "• $it" }), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    c.appScope.launch {
                        // Applied to what is saved at this moment, so nothing outside the file is undone.
                        c.settings.update { file.applyTo(it) }
                        withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.imported), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.import_replace), color = CtrlColors.AccentText) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}

private fun changes(context: Context, a: AppSettings, b: AppSettings): List<String> = buildList {
    for (slot in com.vknxworks.talkctrl.data.settings.ProfileSlot.entries) {
        val before = a.profiles[slot]
        val after = b.profiles[slot]
        if (before != after) add(context.getString(if (before == null) R.string.import_profile_new else R.string.import_profile_replaced, context.getString(slot.label)))
    }
    if (a.useRfcomm != b.useRfcomm) add(context.getString(R.string.use_rfcomm))
    if (a.modelFolder != b.modelFolder) add(context.getString(R.string.import_model))
    if (a.lockTimeout != b.lockTimeout) add(context.getString(R.string.lock_again))
    if (a.hideScreenshots != b.hideScreenshots) add(context.getString(R.string.hide_screenshots))
    if (a.hideRecents != b.hideRecents) add(context.getString(R.string.hide_recents))
    if (a.fmNames != b.fmNames) add(context.getString(R.string.import_fm_names))
}
