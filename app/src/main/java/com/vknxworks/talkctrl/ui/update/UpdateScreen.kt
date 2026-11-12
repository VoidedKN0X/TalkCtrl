package com.vknxworks.talkctrl.ui.update

import android.Manifest
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.protocol.FirmwareFile
import com.vknxworks.talkctrl.protocol.FirmwareVersion
import com.vknxworks.talkctrl.protocol.GaiaDialect
import com.vknxworks.talkctrl.protocol.Languages
import com.vknxworks.talkctrl.protocol.Models
import com.vknxworks.talkctrl.protocol.UpgradeEngine
import com.vknxworks.talkctrl.ui.common.hasPermission
import com.vknxworks.talkctrl.ui.components.ConfirmDialog
import com.vknxworks.talkctrl.ui.components.CtrlAlertDialog
import com.vknxworks.talkctrl.ui.components.CtrlButton
import com.vknxworks.talkctrl.ui.components.CtrlCard
import com.vknxworks.talkctrl.ui.components.CtrlProgressBar
import com.vknxworks.talkctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.talkctrl.ui.components.CtrlTextField
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupChoice
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.components.ScreenTopBar
import com.vknxworks.talkctrl.ui.components.SectionLabel
import com.vknxworks.talkctrl.ui.components.WarningCard
import com.vknxworks.talkctrl.ui.theme.CtrlColors
import com.vknxworks.talkctrl.ui.theme.CtrlType
import com.vknxworks.talkctrl.ui.theme.Gutter
import com.vknxworks.talkctrl.ui.unit.rememberUnit
import com.vknxworks.talkctrl.update.Ending
import com.vknxworks.talkctrl.update.FirmwareInfo
import com.vknxworks.talkctrl.update.FirmwareService
import com.vknxworks.talkctrl.update.UpdateServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** A firmware file ready to send: where it is, which version it is, and where it came from. */
private data class Picked(val file: File, val version: FirmwareVersion?, val label: String)

private const val MIN_BATTERY = 50

@Composable
fun UpdateScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val unit = rememberUnit()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val transfer by FirmwareService.state.collectAsStateWithLifecycle()

    val folder = settings.modelFolder ?: Models.guess(unit.serial)?.folder
    var language by remember { mutableStateOf(Languages.byCode(unit.user?.language ?: 1)?.takeIf { it.urlKey.isNotEmpty() } ?: Languages.byCode(1)!!) }
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<FirmwareInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<List<String>?>(null) }
    var downloadPct by remember { mutableStateOf<Int?>(null) }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var askVersion by remember { mutableStateOf<File?>(null) }
    var versionGuess by remember { mutableStateOf("") }
    var confirmStart by remember { mutableStateOf(false) }
    var confirmAbort by remember { mutableStateOf(false) }

    // Keep the screen on while a transfer runs.
    val view = LocalView.current
    DisposableEffect(transfer.running) {
        view.keepScreenOn = transfer.running
        onDispose { view.keepScreenOn = false }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = displayName(context, uri) ?: ""
            val lower = name.lowercase()
            if (!lower.endsWith(".bin") && !lower.endsWith(".fot") && !FirmwareFile.isTarGz(lower)) {
                error = context.getString(R.string.upd_wrong_file)
                return@launch
            }
            val raw = File(context.cacheDir, "firmware-picked")
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } } != null
                } catch (_: Exception) {
                    false
                }
            }
            val dest = if (ok && raw.length() > 0L) prepareFirmware(context, raw, lower) else null
            if (dest == null) {
                error = context.getString(R.string.upd_file_read_failed)
                return@launch
            }
            error = null
            // Always confirm the version (the file itself doesn't say it in a form TalkCtrl can read).
            versionGuess = FirmwareVersion.parse(name)?.toString().orEmpty()
            askVersion = dest
        }
    }

    val current = unit.firmware
    val battery = unit.battery
    val newer = { v: FirmwareVersion? -> v != null && current != null && v > current }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.firmware_update))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            WarningCard(stringResource(R.string.upd_untested_banner), Icons.Outlined.WarningAmber, Modifier.padding(horizontal = Gutter, vertical = 8.dp))

            if (transfer.running || transfer.end != null) {
                TransferCard(transfer.running, transfer.phase, transfer.percent, transfer.waitingForUnit, transfer.end, transfer.code, onAbort = { confirmAbort = true }) {
                    FirmwareService.clear()
                }
            }

            Group(stringResource(R.string.upd_group_unit)) {
                GroupRow(stringResource(R.string.firmware), stringResource(if (unit.connected) R.string.status_connected else R.string.upd_connect_first), icon = Icons.Outlined.Headset, value = current?.toString() ?: "–")
                GroupRow(stringResource(R.string.battery), value = battery?.let { stringResource(R.string.battery_value, it) } ?: "–")
                GroupChoice(
                    stringResource(R.string.upd_model),
                    Models.all.map { it.folder to it.name },
                    folder,
                    fallbackLabel = stringResource(R.string.upd_pick_model),
                ) { f -> scope.launch { c.settings.update { it.copy(modelFolder = f) } }; info = null; notes = null }
            }

            Group(stringResource(R.string.upd_group_server), footer = stringResource(R.string.upd_server_footer, UpdateServer.HOST)) {
                GroupRow(
                    stringResource(if (checking) R.string.upd_checking else R.string.upd_check),
                    folder?.let { stringResource(R.string.upd_check_sub, it) } ?: stringResource(R.string.upd_pick_model),
                    icon = Icons.Outlined.CloudDownload,
                    showChevron = true,
                    onClick = {
                        if (folder == null || checking) return@GroupRow
                        checking = true
                        error = null
                        scope.launch {
                            try {
                                info = UpdateServer.check(folder)
                                notes = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = context.getString(R.string.upd_check_failed)
                            }
                            checking = false
                        }
                    },
                )
                info?.let { i ->
                    GroupRow(
                        stringResource(R.string.upd_latest),
                        when {
                            !i.enabledByOta -> stringResource(R.string.upd_not_ota)
                            current == null -> stringResource(R.string.upd_connect_first)
                            newer(i.version) -> stringResource(R.string.upd_newer)
                            else -> stringResource(R.string.upd_up_to_date)
                        },
                        value = i.versionText.ifBlank { "–" },
                    )
                    GroupRow(stringResource(R.string.upd_whats_new), stringResource(R.string.upd_whats_new_sub), showChevron = true, onClick = {
                        val f = folder ?: return@GroupRow
                        scope.launch {
                            try {
                                val all = UpdateServer.releaseNotes(f)
                                val key = Locale.getDefault().language.let { if (it == "zh") "zh" else it }
                                notes = all[key] ?: all["en"] ?: all.values.firstOrNull() ?: emptyList()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = context.getString(R.string.upd_check_failed)
                            }
                        }
                    })
                    GroupChoice(
                        stringResource(R.string.upd_prompt_language),
                        Languages.all.filter { it.urlKey.isNotEmpty() }.map { it to it.name },
                        language,
                        icon = Icons.Outlined.Language,
                    ) { language = it }
                    if (i.enabledByOta && newer(i.version)) {
                        GroupRow(
                            stringResource(R.string.upd_download),
                            downloadPct?.let { stringResource(R.string.upd_downloading, it) } ?: stringResource(R.string.upd_download_sub),
                            icon = Icons.Outlined.CloudDownload,
                            onClick = {
                                if (downloadPct != null) return@GroupRow
                                val url = UpdateServer.firmwareUrl(i, language.urlKey)
                                if (url == null) {
                                    error = context.getString(R.string.upd_other_host)
                                    return@GroupRow
                                }
                                downloadPct = 0
                                scope.launch {
                                    val dest = File(context.cacheDir, "firmware-download.bin")
                                    try {
                                        UpdateServer.download(url, dest) { done, total -> if (total > 0) downloadPct = (done * 100 / total).toInt() }
                                        val fw = prepareFirmware(context, dest, url.path.lowercase())
                                        if (fw == null) {
                                            error = context.getString(R.string.upd_file_read_failed)
                                        } else {
                                            picked = Picked(fw, i.version, context.getString(R.string.upd_from_server, i.versionText))
                                            error = null
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        error = context.getString(R.string.upd_download_failed)
                                    }
                                    downloadPct = null
                                }
                            },
                        )
                    }
                }
            }

            notes?.let { lines ->
                CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                    SectionLabel(stringResource(R.string.upd_whats_new))
                    Spacer(Modifier.size(8.dp))
                    if (lines.isEmpty()) Text(stringResource(R.string.upd_no_notes), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Group(stringResource(R.string.upd_group_file), footer = stringResource(R.string.upd_file_footer)) {
                GroupRow(stringResource(R.string.upd_load_file), stringResource(R.string.upd_load_file_sub), icon = Icons.Outlined.FolderOpen, showChevron = true, onClick = {
                    fileLauncher.launch(arrayOf("*/*"))
                })
            }

            error?.let { WarningCard(it, Icons.Outlined.WarningAmber, Modifier.padding(horizontal = Gutter, vertical = 6.dp)) }

            picked?.let { p ->
                val problem = when {
                    !unit.connected || unit.address == null -> stringResource(R.string.upd_need_connected)
                    folder == null -> stringResource(R.string.upd_need_model)
                    folder in Models.notUpdatable -> stringResource(R.string.upd_model_unsupported)
                    current == null -> stringResource(R.string.upd_need_version)
                    p.version == null || !newer(p.version) -> stringResource(R.string.upd_not_newer, p.version?.toString() ?: "?", current.toString())
                    battery == null -> stringResource(R.string.upd_battery_unknown)
                    battery < MIN_BATTERY -> stringResource(R.string.upd_battery_low, MIN_BATTERY, battery)
                    transfer.running -> stringResource(R.string.upd_running)
                    else -> null
                }
                CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                    SectionLabel(stringResource(R.string.upd_ready_label))
                    Spacer(Modifier.size(6.dp))
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.upd_file_size, p.file.length() / 1024), style = CtrlType.Data, color = CtrlColors.Muted)
                    Spacer(Modifier.size(10.dp))
                    if (problem != null) Text(problem, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Error)
                    else Text(stringResource(R.string.upd_ready_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    Spacer(Modifier.size(12.dp))
                    CtrlButton(stringResource(R.string.upd_install), onClick = { confirmStart = true }, enabled = problem == null, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    askVersion?.let { f ->
        VersionDialog(versionGuess, onDismiss = { askVersion = null }) { v ->
            askVersion = null
            picked = Picked(f, v, context.getString(R.string.upd_from_file, v.toString()))
        }
    }
    if (confirmStart) {
        val p = picked
        ConfirmDialog(
            stringResource(R.string.upd_confirm_title, p?.version?.toString() ?: "?"),
            stringResource(R.string.upd_confirm_body, p?.version?.toString() ?: "?", current?.toString() ?: "?"),
            stringResource(R.string.upd_confirm_go),
            onDismiss = { confirmStart = false },
        ) {
            confirmStart = false
            val addr = unit.address
            if (p != null && addr != null) {
                if (!context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                FirmwareService.start(context, addr, p.file, v3 = GaiaDialect.forModel(folder) == GaiaDialect.V3)
            }
        }
    }
    if (confirmAbort) {
        ConfirmDialog(stringResource(R.string.upd_abort_title), stringResource(R.string.upd_abort_body), stringResource(R.string.upd_abort), onDismiss = { confirmAbort = false }) {
            confirmAbort = false
            FirmwareService.abort(context)
        }
    }
}

@Composable
private fun TransferCard(
    running: Boolean,
    phase: UpgradeEngine.Phase?,
    percent: Double,
    waiting: Boolean,
    end: Ending?,
    code: Int,
    onAbort: () -> Unit,
    onClear: () -> Unit,
) {
    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        SectionLabel(stringResource(R.string.upd_transfer_label))
        Spacer(Modifier.size(8.dp))
        if (running) {
            Text(
                stringResource(
                    when {
                        waiting && phase == UpgradeEngine.Phase.REBOOTING -> R.string.upd_phase_rebooting
                        waiting -> R.string.upd_phase_reconnecting
                        phase == UpgradeEngine.Phase.TRANSFERRING -> R.string.upd_phase_transferring
                        phase == UpgradeEngine.Phase.VALIDATING -> R.string.upd_phase_validating
                        phase == UpgradeEngine.Phase.REBOOTING -> R.string.upd_phase_rebooting
                        phase == UpgradeEngine.Phase.COMMITTING -> R.string.upd_phase_committing
                        else -> R.string.upd_phase_preparing
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(10.dp))
            CtrlProgressBar((percent / 100.0).toFloat().coerceIn(0f, 1f), Modifier.fillMaxWidth())
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.upd_percent, percent.toInt()), style = CtrlType.Data, color = CtrlColors.Muted)
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.upd_keep_close), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            Spacer(Modifier.size(12.dp))
            CtrlSecondaryButton(onClick = onAbort, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.upd_abort), color = CtrlColors.Error) }
        } else if (end != null) {
            Text(
                stringResource(
                    when (end) {
                        Ending.COMPLETE -> R.string.upd_end_complete
                        Ending.ABORTED -> R.string.upd_end_aborted
                        Ending.BATTERY_LOW -> R.string.upd_end_battery
                        Ending.CONNECTION_LOST -> R.string.upd_end_connection
                        Ending.NOT_READY -> R.string.upd_end_not_ready
                        Ending.FILE -> R.string.upd_file_read_failed
                        Ending.PROTOCOL, Ending.UNIT_ERROR -> R.string.upd_end_error
                    },
                ) + if (code != 0) " " + stringResource(R.string.upd_error_code, code) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (end == Ending.COMPLETE) CtrlColors.Text else CtrlColors.Error,
            )
            Spacer(Modifier.size(10.dp))
            CtrlSecondaryButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.upd_ok)) }
        }
    }
}

@Composable
private fun VersionDialog(initial: String, onDismiss: () -> Unit, onDone: (FirmwareVersion) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val v = FirmwareVersion.parse(text)
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upd_version_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.upd_version_body), style = MaterialTheme.typography.bodyMedium)
                CtrlTextField(
                    value = text,
                    onValueChange = { text = it.take(10) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { v?.let(onDone) }, enabled = v != null) { Text(stringResource(R.string.lock_next)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
    )
}

/** Unpacks Cardo's .tar.gz (first file inside) into the file that's sent; .bin/.fot are used as they are. */
private suspend fun prepareFirmware(context: android.content.Context, src: File, name: String): File? = withContext(Dispatchers.IO) {
    if (!FirmwareFile.isTarGz(name)) return@withContext src
    val inner = FirmwareFile.firstTarEntry(src.readBytes()) ?: return@withContext null
    File(context.cacheDir, "firmware-ver.bin").also { it.writeBytes(inner) }
}

private fun displayName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Exception) {
    null
}
