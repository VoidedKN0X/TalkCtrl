package com.vknxworks.talkctrl.ui.unit

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.bt.LinkState
import com.vknxworks.talkctrl.bt.UnitState
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.components.MessageView
import com.vknxworks.talkctrl.ui.components.TabTitle
import com.vknxworks.talkctrl.ui.components.bottomClearance
import com.vknxworks.talkctrl.ui.nav.LocalNavigator
import com.vknxworks.talkctrl.ui.nav.UnitRoute
import com.vknxworks.talkctrl.ui.theme.CtrlColors
import com.vknxworks.talkctrl.ui.theme.CtrlType

@Composable
fun rememberUnit(): UnitState {
    val s by LocalContext.current.container.link.state.collectAsStateWithLifecycle()
    return s
}

/** A main tab: wordmark, profile picture, big title, then scrolling content above the bottom bar. */
@Composable
fun TabPage(title: String, label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).bottomClearance()) {
            TabTitle(title, label = label)
            content()
        }
    }
}

/** Shown on the Audio, Radio and Intercom tabs while no unit is connected. */
@Composable
fun NotConnectedView(unit: UnitState) {
    val nav = LocalNavigator.current
    MessageView(
        Icons.Outlined.BluetoothDisabled,
        stringResource(if (unit.link == LinkState.CONNECTING) R.string.connecting else R.string.not_connected),
        stringResource(R.string.not_connected_body),
        Modifier.heightIn(min = 360.dp),
        actionLabel = stringResource(R.string.open_unit_tab),
        onAction = { nav.navigate(UnitRoute) },
    )
}

/** A feature TalkCtrl knows about but can't do safely yet. */
@Composable
fun NeedsConfirmRow(title: String, icon: ImageVector? = Icons.Outlined.HelpOutline) {
    GroupRow(title, stringResource(R.string.needs_confirming), icon = icon, iconTint = CtrlColors.Inactive, titleColor = CtrlColors.Inactive)
}

/** A 0..max level slider in a group; sends only when you let go. */
@Composable
fun LevelRow(title: String, value: Int, max: Int, enabled: Boolean = true, valueLabel: (Int) -> String = { "$it" }, onSet: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) CtrlColors.Text else CtrlColors.Inactive,
                modifier = Modifier.weight(1f),
            )
            Text(valueLabel(v.toInt()), style = CtrlType.Data, color = CtrlColors.Muted)
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onSet(v.toInt()) },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = CtrlColors.Accent, activeTrackColor = CtrlColors.Accent, inactiveTrackColor = CtrlColors.ProgressTrack),
        )
    }
}

/** Sends something to the unit, with a short note if it couldn't. */
@Composable
fun rememberSender(): ((() -> Boolean) -> Unit) {
    val context = LocalContext.current
    return remember(context) {
        { action ->
            val ok = try {
                action()
            } catch (_: IllegalArgumentException) {
                false
            }
            if (!ok) Toast.makeText(context, context.getString(R.string.send_failed), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun SmallNote(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Icon(Icons.Outlined.HelpOutline, null, Modifier.size(16.dp), tint = CtrlColors.Muted)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
    }
}
