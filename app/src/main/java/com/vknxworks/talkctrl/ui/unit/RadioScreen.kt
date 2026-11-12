package com.vknxworks.talkctrl.ui.unit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.ui.components.CtrlAlertDialog
import com.vknxworks.talkctrl.ui.components.CtrlButton
import com.vknxworks.talkctrl.ui.components.CtrlHeroCard
import com.vknxworks.talkctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.talkctrl.ui.components.CtrlTextField
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupChoice
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.components.GroupSwitch
import com.vknxworks.talkctrl.ui.components.StatusTag
import com.vknxworks.talkctrl.ui.components.WarningCard
import com.vknxworks.talkctrl.ui.theme.CtrlColors
import com.vknxworks.talkctrl.ui.theme.CtrlType
import com.vknxworks.talkctrl.ui.theme.Gutter
import java.util.Locale

@Composable
fun RadioScreen() {
    val context = LocalContext.current
    val c = context.container
    val unit = rememberUnit()
    val send = rememberSender()
    var tuning by remember { mutableStateOf(false) }
    var editPreset by remember { mutableStateOf<Int?>(null) }
    val japan = unit.user?.fmRegion == 1
    val range = if (japan) 76.0..95.0 else 87.5..108.0

    TabPage(stringResource(R.string.tab_radio), stringResource(R.string.label_fm)) {
        if (!unit.connected) return@TabPage NotConnectedView(unit)
        if (unit.caps?.fm == false) {
            WarningCard(stringResource(R.string.fm_not_on_unit), Icons.Outlined.WarningAmber, Modifier.padding(horizontal = Gutter, vertical = 6.dp))
        }
        val on = unit.fmState != 0
        CtrlHeroCard(stringResource(R.string.label_fm), Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(
                    stringResource(
                        when (unit.fmState) {
                            0 -> R.string.fm_off
                            1 -> R.string.fm_on
                            2, 3 -> R.string.fm_scanning
                            else -> R.string.fm_tuning
                        },
                    ),
                    active = on,
                )
                Spacer(Modifier.weight(1f))
                Text(unit.fmMhz?.let { fmt(it) } ?: "–", style = CtrlType.Readout, color = CtrlColors.Text)
            }
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlButton(
                    stringResource(if (on) R.string.fm_turn_off else R.string.fm_turn_on),
                    onClick = { send { c.link.send(Caip.control(if (on) Caip.Ev.FM_OFF else Caip.Ev.FM_ON)) } },
                    modifier = Modifier.weight(1f),
                )
                CtrlSecondaryButton(onClick = { tuning = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fm_tune_to)) }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlSecondaryButton(onClick = { send { c.link.send(Caip.control(Caip.Ev.FM_SEEK_DOWN)) } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fm_seek_down)) }
                CtrlSecondaryButton(onClick = { send { c.link.send(Caip.control(Caip.Ev.FM_SEEK_UP)) } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fm_seek_up)) }
            }
        }

        Group(stringResource(R.string.group_scan), footer = stringResource(R.string.scan_footer)) {
            GroupRow(stringResource(R.string.fm_scan), stringResource(R.string.fm_scan_sub), icon = Icons.Outlined.Radio, onClick = { send { c.link.send(Caip.control(Caip.Ev.FM_SCAN)) } })
            GroupRow(stringResource(R.string.fm_scan_back), icon = Icons.Outlined.Radio, onClick = { send { c.link.send(Caip.control(Caip.Ev.FM_SCAN_BACK)) } })
            GroupRow(stringResource(R.string.fm_stop_scan), onClick = { send { c.link.send(Caip.control(Caip.Ev.FM_STOP_SCAN)) } })
            GroupRow(stringResource(R.string.fm_auto_tune), stringResource(R.string.fm_auto_tune_sub), icon = Icons.Outlined.Tune, onClick = { send { c.link.send(Caip.control(Caip.Ev.FM_AUTO_TUNE)) } })
        }

        val presets = unit.presets
        Group(stringResource(R.string.group_presets), footer = stringResource(R.string.presets_footer)) {
            (1..6).forEach { n ->
                val f = presets?.getOrNull(n - 1)?.takeIf { it > 0 }
                val editButton: (@Composable () -> Unit)? = if (presets == null) null else {
                    { IconButton(onClick = { editPreset = n }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_preset), tint = CtrlColors.Muted) } }
                }
                GroupRow(
                    stringResource(R.string.preset_n, n),
                    if (unit.fmPreset == n && on) stringResource(R.string.playing) else null,
                    value = f?.let { fmt(it) } ?: stringResource(R.string.preset_empty),
                    onClick = { send { c.link.send(Caip.fmPlayPreset(n)) } },
                    trailing = editButton,
                )
            }
        }

        unit.user?.let { u ->
            Group(stringResource(R.string.group_fm_settings)) {
                GroupChoice(
                    stringResource(R.string.fm_region),
                    listOf(0 to stringResource(R.string.region_world), 1 to stringResource(R.string.region_japan)),
                    u.fmRegion,
                    icon = Icons.Outlined.Public,
                ) { r -> send { c.link.setUser { it.withFmRegion(r) } } }
                GroupSwitch(stringResource(R.string.rds), stringResource(R.string.rds_sub), u.rds, Icons.Outlined.TextFields) { on2 ->
                    send { c.link.setUser { it.withRds(on2) } }
                }
            }
        }
    }

    if (tuning) {
        FrequencyDialog(stringResource(R.string.fm_tune_to), unit.fmMhz, range, allowEmpty = false, onDismiss = { tuning = false }) { f ->
            tuning = false
            if (f != null) send { c.link.send(Caip.fmSetFrequency(f)) }
        }
    }
    editPreset?.let { n ->
        val cur = unit.presets ?: return@let
        FrequencyDialog(stringResource(R.string.preset_n, n), cur.getOrNull(n - 1)?.takeIf { it > 0 }, range, allowEmpty = true, onDismiss = { editPreset = null }) { f ->
            editPreset = null
            val next = cur.toMutableList().also { it[n - 1] = f ?: 0.0 }
            send { c.link.setPresets(next) }
        }
    }
}

private fun fmt(mhz: Double) = String.format(Locale.ROOT, "%.2f", mhz).trimEnd('0').trimEnd('.').let { if ('.' in it) it else "$it.0" } + " MHz"

@Composable
private fun FrequencyDialog(title: String, initial: Double?, range: ClosedFloatingPointRange<Double>, allowEmpty: Boolean, onDismiss: () -> Unit, onDone: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { String.format(Locale.ROOT, "%.2f", it).trimEnd('0').trimEnd('.') } ?: "") }
    val value = text.replace(',', '.').toDoubleOrNull()
    // 50 kHz steps only, inside the region's band.
    val valid = (allowEmpty && text.isBlank()) || (value != null && value in range && Math.round(value * 100) % 5 == 0L)
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            androidx.compose.foundation.layout.Column {
                CtrlTextField(
                    value = text,
                    onValueChange = { text = it.take(7) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.fm_range, fmt(range.start), fmt(range.endInclusive)) + if (allowEmpty) "\n" + stringResource(R.string.preset_clear_hint) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(if (text.isBlank()) null else value) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
    )
}
