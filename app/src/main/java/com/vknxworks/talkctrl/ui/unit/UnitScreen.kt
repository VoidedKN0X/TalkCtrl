package com.vknxworks.talkctrl.ui.unit

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.bt.FoundUnit
import com.vknxworks.talkctrl.bt.LinkState
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.data.settings.ProfileSlot
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.protocol.ConfigRecord
import com.vknxworks.talkctrl.protocol.UserConfig
import com.vknxworks.talkctrl.protocol.Volumes
import com.vknxworks.talkctrl.ui.common.hasPermission
import com.vknxworks.talkctrl.ui.common.openAppSettings
import com.vknxworks.talkctrl.ui.components.ConfirmDialog
import com.vknxworks.talkctrl.ui.components.CtrlBottomSheet
import com.vknxworks.talkctrl.ui.components.CtrlButton
import com.vknxworks.talkctrl.ui.components.CtrlHeroCard
import com.vknxworks.talkctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.components.GroupSwitch
import com.vknxworks.talkctrl.ui.components.NameDialog
import com.vknxworks.talkctrl.ui.components.StatusTag
import com.vknxworks.talkctrl.ui.components.WarningCard
import com.vknxworks.talkctrl.ui.nav.LocalNavigator
import com.vknxworks.talkctrl.ui.nav.UpdateRoute
import com.vknxworks.talkctrl.ui.theme.CtrlColors
import com.vknxworks.talkctrl.ui.theme.CtrlType
import com.vknxworks.talkctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private val BT_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

@Composable
fun UnitScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val unit = rememberUnit()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val send = rememberSender()
    var granted by remember { mutableStateOf(BT_PERMISSIONS.all { context.hasPermission(it) }) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = BT_PERMISSIONS.all { context.hasPermission(it) }
        askedOnce = true
        if (granted) showScan = true
    }

    // Reconnect to the remembered unit when the app opens.
    LaunchedEffect(granted) {
        if (granted && unit.link == LinkState.IDLE && settings.unitAddress != null) c.reconnect()
    }
    // Keep the remembered name in step with the unit's own name.
    LaunchedEffect(unit.name) {
        val n = unit.name
        if (n != null && unit.address == settings.unitAddress && n != settings.unitName) c.settings.update { it.copy(unitName = n) }
    }

    var renaming by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showSerial by remember { mutableStateOf(false) }
    var profileSlot by remember { mutableStateOf<ProfileSlot?>(null) }

    val title = unit.name ?: settings.unitName ?: stringResource(R.string.no_unit)
    TabPage(title, stringResource(R.string.label_unit)) {
        val statusText = stringResource(
            when (unit.link) {
                LinkState.CONNECTED -> R.string.status_connected
                LinkState.CONNECTING -> R.string.connecting
                LinkState.FAILED -> R.string.status_failed
                LinkState.IDLE -> R.string.not_connected
            },
        )
        CtrlHeroCard(
            label = stringResource(R.string.label_link),
            modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            meta = if (unit.rfcomm) stringResource(R.string.via_rfcomm) else stringResource(R.string.via_ble),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(statusText, active = unit.connected)
                Spacer(Modifier.weight(1f))
                if (unit.battery != null) {
                    Text(stringResource(R.string.battery_value, unit.battery), style = CtrlType.Readout, color = CtrlColors.Text)
                }
            }
            if (unit.charging) Text(stringResource(R.string.charging), style = CtrlType.Data, color = CtrlColors.Muted)
            Spacer(Modifier.size(14.dp))
            if (!granted) {
                Text(stringResource(R.string.bt_permission_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                Spacer(Modifier.size(12.dp))
                CtrlButton(
                    stringResource(if (askedOnce) R.string.open_permissions else R.string.allow_bluetooth),
                    onClick = { if (askedOnce) openAppSettings(context) else permLauncher.launch(BT_PERMISSIONS) },
                    icon = Icons.Outlined.Bluetooth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        unit.link == LinkState.CONNECTED || unit.link == LinkState.CONNECTING ->
                            CtrlSecondaryButton(onClick = { c.link.disconnect() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.disconnect)) }
                        settings.unitAddress != null ->
                            CtrlButton(stringResource(R.string.connect), onClick = { c.reconnect() }, modifier = Modifier.weight(1f))
                    }
                    CtrlSecondaryButton(onClick = { showScan = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.find_units)) }
                }
                if (unit.connected) {
                    Spacer(Modifier.size(10.dp))
                    CtrlButton(
                        stringResource(R.string.riding_mode),
                        onClick = { nav.navigate(com.vknxworks.talkctrl.ui.nav.RidingRoute) },
                        icon = Icons.Outlined.TwoWheeler,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (unit.unsupportedVersion) {
            WarningCard(stringResource(R.string.old_protocol_warning, unit.caipVersion ?: 0), Icons.Outlined.WarningAmber, Modifier.padding(horizontal = Gutter, vertical = 6.dp))
        }

        if (unit.connected) {
            Group(stringResource(R.string.group_this_unit), footer = stringResource(R.string.serial_footer)) {
                GroupRow(stringResource(R.string.unit_name), unit.name ?: "–", icon = Icons.Outlined.Edit, showChevron = true, onClick = { renaming = true })
                GroupRow(stringResource(R.string.firmware), icon = Icons.Outlined.Memory, value = unit.firmware?.toString() ?: "–")
                if (unit.internalVersion != null) GroupRow(stringResource(R.string.firmware_internal), value = unit.internalVersion)
                GroupRow(
                    stringResource(R.string.serial),
                    if (showSerial) null else stringResource(R.string.tap_to_show),
                    icon = Icons.Outlined.Badge,
                    value = if (showSerial) unit.serial ?: "–" else null,
                    onClick = { showSerial = !showSerial },
                )
                GroupRow(
                    stringResource(R.string.battery),
                    icon = Icons.Outlined.BatteryChargingFull,
                    value = unit.battery?.let { stringResource(R.string.battery_value, it) } ?: "–",
                )
            }

            Group(stringResource(R.string.group_profiles), footer = stringResource(R.string.profiles_footer)) {
                ProfileSlot.entries.forEach { slot ->
                    val saved = settings.profiles[slot] != null
                    GroupRow(
                        stringResource(slot.label),
                        stringResource(if (saved) R.string.profile_tap_apply else R.string.profile_empty),
                        icon = when (slot) {
                            ProfileSlot.SOLO -> Icons.Outlined.Person
                            ProfileSlot.DUO -> Icons.Outlined.People
                            ProfileSlot.GROUP -> Icons.Outlined.Groups
                        },
                        showChevron = true,
                        onClick = { profileSlot = slot },
                    )
                }
            }
        }

        Group(stringResource(R.string.group_maintenance)) {
            GroupRow(
                stringResource(R.string.firmware_update),
                stringResource(R.string.firmware_update_sub),
                icon = Icons.Outlined.SystemUpdate,
                showChevron = true,
                onClick = { nav.navigate(UpdateRoute) },
            )
            if (unit.connected) {
                GroupRow(
                    stringResource(R.string.factory_reset),
                    stringResource(R.string.factory_reset_sub),
                    icon = Icons.Outlined.RestartAlt,
                    iconTint = CtrlColors.Error,
                    onClick = { confirmReset = true },
                )
            }
            if (settings.unitAddress != null) {
                GroupRow(stringResource(R.string.forget_unit), stringResource(R.string.forget_unit_sub), icon = Icons.Outlined.LinkOff, onClick = { c.forgetUnit() })
            }
        }
        SmallNote(stringResource(R.string.out_of_scope))
    }

    if (showScan && granted) ScanSheet(onDismiss = { showScan = false }) { u ->
        showScan = false
        c.connect(u.address, u.name)
    }
    if (renaming) {
        NameDialog(stringResource(R.string.rename_unit), unit.name.orEmpty(), onDismiss = { renaming = false }) { n ->
            renaming = false
            if (n.toByteArray().size > Caip.NAME_MAX_BYTES) {
                Toast.makeText(context, context.getString(R.string.name_too_long), Toast.LENGTH_SHORT).show()
            } else send { c.link.rename(n) }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            stringResource(R.string.factory_reset_title),
            stringResource(R.string.factory_reset_body),
            stringResource(R.string.factory_reset_confirm),
            onDismiss = { confirmReset = false },
        ) {
            confirmReset = false
            send { c.link.send(Caip.factoryReset()) }
        }
    }
    profileSlot?.let { slot ->
        ProfileSheet(
            slot,
            saved = settings.profiles[slot],
            onDismiss = { profileSlot = null },
            onApply = { data ->
                profileSlot = null
                val parsed = parseProfile(data)
                if (parsed == null) Toast.makeText(context, context.getString(R.string.send_failed), Toast.LENGTH_SHORT).show()
                else send { c.link.applyRecords(parsed.first, parsed.second) }
            },
            onSave = {
                profileSlot = null
                val u = unit.user
                val v = unit.volumes
                if (u == null || v == null) {
                    Toast.makeText(context, context.getString(R.string.profile_nothing_to_save), Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch { c.settings.update { s -> s.copy(profiles = s.profiles + (slot to profileString(u, v))) } }
                    Toast.makeText(context, context.getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun String.unhex(): ByteArray? = if (length % 2 != 0) null else try {
    ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
} catch (_: NumberFormatException) {
    null
}

private fun profileString(u: UserConfig, v: Volumes) = u.record().hex() + ":" + v.record().hex()

private fun parseProfile(s: String): Pair<UserConfig, Volumes>? {
    val parts = s.split(':')
    if (parts.size != 2) return null
    val a = parts[0].unhex() ?: return null
    val b = parts[1].unhex() ?: return null
    if (a.size != 4 || a[0].toInt() != 0x00 || b.size != 7 || b[0].toInt() != 0x02) return null
    return UserConfig.from(ConfigRecord(0x00, a)) to Volumes.from(ConfigRecord(0x02, b))
}

@Composable
private fun ProfileSheet(slot: ProfileSlot, saved: String?, onDismiss: () -> Unit, onApply: (String) -> Unit, onSave: () -> Unit) {
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(slot.label), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.profile_sheet_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
            if (saved != null) CtrlButton(stringResource(R.string.profile_apply), onClick = { onApply(saved) }, modifier = Modifier.fillMaxWidth())
            CtrlSecondaryButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (saved != null) R.string.profile_replace else R.string.profile_save))
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ScanSheet(onDismiss: () -> Unit, onPick: (FoundUnit) -> Unit) {
    val c = LocalContext.current.container
    val found by c.scanner.found.collectAsStateWithLifecycle()
    val scanning by c.scanner.scanning.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        c.scanner.start()
        onDispose { c.scanner.stop() }
    }
    val list = if (showAll) found else found.filter { it.cardo }
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.find_units), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            stringResource(if (!c.scanner.bluetoothOn) R.string.bt_off else if (scanning) R.string.scanning else R.string.scan_stopped),
            style = CtrlType.Data,
            color = CtrlColors.Muted,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        GroupSwitch(stringResource(R.string.show_all_devices), stringResource(R.string.show_all_devices_sub), showAll) { showAll = it }
        LazyColumn(Modifier.navigationBarsPadding().heightIn(max = 420.dp)) {
            if (list.isEmpty()) item {
                Text(stringResource(R.string.no_units_found), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted, modifier = Modifier.padding(24.dp))
            }
            items(list, key = { it.address }) { u ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(u) }.heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (u.cardo) Icons.Outlined.Headset else Icons.Outlined.Bluetooth, null, Modifier.size(22.dp), tint = if (u.cardo) CtrlColors.AccentText else CtrlColors.Muted)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name ?: stringResource(R.string.unnamed_device), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                u.address,
                                if (u.paired) stringResource(R.string.paired) else null,
                                u.rssi?.let { stringResource(R.string.rssi, it) },
                            ).joinToString(" · "),
                            style = CtrlType.Data,
                            color = CtrlColors.Muted,
                        )
                    }
                }
            }
        }
    }
}
