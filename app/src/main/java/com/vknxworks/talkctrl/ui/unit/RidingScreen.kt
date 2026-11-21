package com.vknxworks.talkctrl.ui.unit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.protocol.Volumes
import com.vknxworks.talkctrl.ui.nav.LocalNavigator
import com.vknxworks.talkctrl.ui.theme.CtrlColors
import com.vknxworks.talkctrl.ui.theme.CtrlShapes
import com.vknxworks.talkctrl.ui.theme.CtrlType
import com.vknxworks.talkctrl.ui.theme.Gutter
import java.util.Locale

/**
 * Riding mode: a few big buttons you can hit with gloves on, and the screen kept on. It only sends the
 * commands the other tabs already send (FM on/off, seek, play preset, mesh mute, end intercom calls, music
 * volume), all confirmed in PROTOCOL.md.
 */
@Composable
fun RidingScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val unit = rememberUnit()
    val send = rememberSender()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    // The unit doesn't report mesh mute, so (like the Intercom tab) the last state sent from here is shown.
    var muted by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { nav.back() }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background).statusBarsPadding().navigationBarsPadding().padding(Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.riding_mode), style = CtrlType.Label, color = CtrlColors.Muted)
                Text(
                    when {
                        !unit.connected -> stringResource(R.string.not_connected)
                        unit.battery != null -> stringResource(R.string.battery_value, unit.battery) + if (unit.charging) " · " + stringResource(R.string.charging) else ""
                        else -> stringResource(R.string.status_connected)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (unit.connected) CtrlColors.Text else CtrlColors.Error,
                )
                val fm = if (unit.fmState != 0) unit.fmMhz?.let { mhz -> settings.fmName(mhz) ?: String.format(Locale.ROOT, "%.1f MHz", mhz) } else null
                Text(fm?.let { stringResource(R.string.riding_fm_on, it) } ?: stringResource(R.string.fm_off), style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BigButton(Icons.Outlined.Close, stringResource(R.string.riding_exit), modifier = Modifier.size(88.dp), onClick = { nav.back() })
        }
        Spacer(Modifier.size(16.dp))
        val enabled = unit.connected
        val fmOn = unit.fmState != 0
        val presets = unit.presets.orEmpty().mapIndexedNotNull { i, f -> if (f > 0) i + 1 else null }
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigRow {
                BigButton(Icons.Outlined.Radio, stringResource(if (fmOn) R.string.fm_turn_off else R.string.fm_turn_on), enabled = enabled, accent = fmOn) {
                    send { c.link.send(Caip.control(if (fmOn) Caip.Ev.FM_OFF else Caip.Ev.FM_ON)) }
                }
                BigButton(Icons.Outlined.SkipNext, stringResource(R.string.riding_next_preset), enabled = enabled && presets.isNotEmpty()) {
                    // The next saved preset after the one playing (or the first).
                    val next = presets.firstOrNull { it > unit.fmPreset } ?: presets.first()
                    send { c.link.send(Caip.fmPlayPreset(next)) }
                }
            }
            BigRow {
                BigButton(Icons.Outlined.SkipPrevious, stringResource(R.string.fm_seek_down), enabled = enabled) { send { c.link.send(Caip.control(Caip.Ev.FM_SEEK_DOWN)) } }
                BigButton(Icons.Outlined.SkipNext, stringResource(R.string.fm_seek_up), enabled = enabled) { send { c.link.send(Caip.control(Caip.Ev.FM_SEEK_UP)) } }
            }
            BigRow {
                BigButton(Icons.AutoMirrored.Outlined.VolumeDown, stringResource(R.string.riding_music_down), enabled = enabled && unit.volumes != null) {
                    send { c.link.setVolumes { v -> (v.music - 1).coerceIn(0, Volumes.MAX).let { n -> v.copy(music = n, music2 = n) } } }
                }
                BigButton(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.riding_music_up), enabled = enabled && unit.volumes != null) {
                    send { c.link.setVolumes { v -> (v.music + 1).coerceIn(0, Volumes.MAX).let { n -> v.copy(music = n, music2 = n) } } }
                }
            }
            BigRow {
                BigButton(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, stringResource(if (muted) R.string.riding_unmute else R.string.riding_mute), enabled = enabled, accent = muted) {
                    val next = !muted
                    send { c.link.send(Caip.dmcMute(next)).also { if (it) muted = next } }
                }
                BigButton(Icons.Outlined.CallEnd, stringResource(R.string.ic_end_all), enabled = enabled, danger = true) {
                    send { c.link.send(Caip.control(Caip.Ev.IC_END_ALL)) }
                }
            }
        }
        unit.volumes?.let { v ->
            Text(stringResource(R.string.riding_music_level, v.music, Volumes.MAX), style = CtrlType.Data, color = CtrlColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BigRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun RowScope.BigButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) = BigButton(icon, label, Modifier.weight(1f).fillMaxHeight(), enabled, accent, danger, onClick)

@Composable
private fun BigButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val bg = if (accent && enabled) CtrlColors.Accent else CtrlColors.Surface
    val fg = when {
        !enabled -> CtrlColors.Inactive
        accent -> CtrlColors.OnAccent
        danger -> CtrlColors.Error
        else -> CtrlColors.Text
    }
    Box(
        modifier
            .heightIn(min = 96.dp)
            .background(bg, CtrlShapes.Button)
            .border(2.dp, if (danger && enabled) CtrlColors.Error else CtrlColors.ControlOutline, CtrlShapes.Button)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
            Icon(icon, null, Modifier.size(40.dp), tint = fg)
            Text(label, color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
