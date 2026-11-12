package com.vknxworks.talkctrl.ui.unit

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.ui.components.ConfirmDialog
import com.vknxworks.talkctrl.ui.components.CtrlAlertDialog
import com.vknxworks.talkctrl.ui.components.CtrlTextField
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.components.GroupSwitch
import com.vknxworks.talkctrl.ui.theme.CtrlColors

@Composable
fun IntercomScreen() {
    val c = LocalContext.current.container
    val unit = rememberUnit()
    val send = rememberSender()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmResetRiders by remember { mutableStateOf(false) }
    var editDial by remember { mutableStateOf(false) }
    // The unit doesn't report these back in a form TalkCtrl reads yet, so they show what you last sent.
    var muted by rememberSaveable { mutableStateOf(false) }
    var bridged by rememberSaveable { mutableStateOf(false) }

    TabPage(stringResource(R.string.tab_intercom), stringResource(R.string.label_intercom)) {
        if (!unit.connected) return@TabPage NotConnectedView(unit)
        val caps = unit.caps

        if (caps?.dmc != false) {
            Group(stringResource(R.string.group_mesh), footer = stringResource(R.string.mesh_footer)) {
                GroupRow(stringResource(R.string.mesh_create), stringResource(R.string.mesh_create_sub), icon = Icons.Outlined.GroupAdd, onClick = { send { c.link.send(Caip.control(Caip.Ev.GROUP_CREATE)) } })
                GroupRow(stringResource(R.string.mesh_join), stringResource(R.string.mesh_join_sub), icon = Icons.Outlined.Groups, onClick = { send { c.link.send(Caip.control(Caip.Ev.GROUP_JOIN)) } })
                GroupRow(stringResource(R.string.mesh_add), stringResource(R.string.mesh_add_sub), icon = Icons.Outlined.PersonAdd, onClick = { send { c.link.send(Caip.control(Caip.Ev.GROUP_ADD_RIDERS)) } })
                GroupRow(stringResource(R.string.mesh_cancel), icon = Icons.Outlined.Cancel, onClick = { send { c.link.send(Caip.control(Caip.Ev.GROUP_CANCEL)) } })
                GroupSwitch(stringResource(R.string.mesh_mute), stringResource(R.string.mesh_mute_sub), muted, Icons.Outlined.MicOff) { on ->
                    send { c.link.send(Caip.dmcMute(on)).also { if (it) muted = on } }
                }
                if (caps?.dmcIcBridge != false) {
                    GroupSwitch(stringResource(R.string.mesh_bridge), stringResource(R.string.mesh_bridge_sub), bridged, Icons.Outlined.Hub) { on ->
                        send { c.link.send(Caip.dmcBridge(on)).also { if (it) bridged = on } }
                    }
                }
                NeedsConfirmRow(stringResource(R.string.mesh_private_chat), Icons.Outlined.Lock)
                GroupRow(stringResource(R.string.mesh_delete), stringResource(R.string.mesh_delete_sub), icon = Icons.Outlined.DeleteOutline, iconTint = CtrlColors.Error, onClick = { confirmDelete = true })
            }
        }

        if (caps?.bluetoothIntercom != false) {
            Group(stringResource(R.string.group_bt_intercom), footer = stringResource(R.string.bt_intercom_footer)) {
                listOf("A", "B", "C").forEachIndexed { i, ch ->
                    GroupRow(stringResource(R.string.ic_pair, ch), icon = Icons.Outlined.Link, onClick = { send { c.link.send(Caip.icPair(i)) } })
                }
                GroupRow(stringResource(R.string.ic_passive_pair), stringResource(R.string.ic_passive_pair_sub), icon = Icons.Outlined.AddCircleOutline, onClick = { send { c.link.send(Caip.control(Caip.Ev.IC_PASSIVE_PAIR)) } })
                GroupRow(stringResource(R.string.ic_stop_pair), icon = Icons.Outlined.StopCircle, onClick = { send { c.link.send(Caip.control(Caip.Ev.IC_STOP_PAIR)) } })
                listOf("A", "B", "C").forEachIndexed { i, ch ->
                    GroupRow(stringResource(R.string.ic_call, ch), icon = Icons.Outlined.Call, onClick = { send { c.link.send(Caip.icCall(i)) } })
                }
                GroupRow(stringResource(R.string.ic_conference), stringResource(R.string.ic_conference_sub), icon = Icons.Outlined.Groups, onClick = { send { c.link.send(Caip.control(Caip.Ev.CONF_START)) } })
                GroupRow(stringResource(R.string.ic_conference_stop), icon = Icons.Outlined.CallEnd, onClick = { send { c.link.send(Caip.control(Caip.Ev.CONF_STOP)) } })
                GroupRow(stringResource(R.string.ic_end_all), icon = Icons.Outlined.CallEnd, onClick = { send { c.link.send(Caip.control(Caip.Ev.IC_END_ALL)) } })
                GroupRow(stringResource(R.string.ic_reset_riders), stringResource(R.string.ic_reset_riders_sub), icon = Icons.Outlined.RestartAlt, iconTint = CtrlColors.Error, onClick = { confirmResetRiders = true })
            }
        }

        Group(stringResource(R.string.group_speed_dial), footer = stringResource(R.string.speed_dial_footer)) {
            GroupRow(stringResource(R.string.speed_dial_number), unit.speedDial?.ifBlank { null } ?: stringResource(R.string.not_set), icon = Icons.Outlined.Dialpad, showChevron = true, onClick = { editDial = true })
            GroupRow(stringResource(R.string.speed_dial_call), icon = Icons.Outlined.Call, onClick = { send { c.link.send(Caip.control(Caip.Ev.SPEED_DIAL_CALL)) } })
        }
    }

    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.mesh_delete_title), stringResource(R.string.mesh_delete_body), stringResource(R.string.mesh_delete), onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            send { c.link.send(Caip.control(Caip.Ev.GROUP_DELETE)) }
        }
    }
    if (confirmResetRiders) {
        ConfirmDialog(stringResource(R.string.ic_reset_riders_title), stringResource(R.string.ic_reset_riders_body), stringResource(R.string.ic_reset_riders), onDismiss = { confirmResetRiders = false }) {
            confirmResetRiders = false
            send { c.link.send(Caip.control(Caip.Ev.IC_RESET_RIDERS)) }
        }
    }
    if (editDial) {
        var text by remember { mutableStateOf(unit.speedDial.orEmpty()) }
        val valid = text.isNotEmpty() && text.length <= 30 && text.all { it.isDigit() || it in "+*#" }
        CtrlAlertDialog(
            onDismissRequest = { editDial = false },
            title = { Text(stringResource(R.string.speed_dial_number), style = MaterialTheme.typography.titleLarge) },
            text = {
                CtrlTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch in "+*#" }.take(30) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { editDial = false; send { c.link.setSpeedDial(text) } }, enabled = valid) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editDial = false }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}
