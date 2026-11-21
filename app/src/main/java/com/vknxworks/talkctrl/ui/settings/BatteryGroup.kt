package com.vknxworks.talkctrl.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.data.settings.AppSettings
import com.vknxworks.talkctrl.data.settings.SettingsFile
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupChoice
import com.vknxworks.talkctrl.ui.lock.AppLock
import kotlinx.coroutines.launch

/** The low-battery warning level; asks for the notification permission when it is turned on. */
@Composable
fun BatteryGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    var wanted by remember { mutableIntStateOf(0) }
    fun save(level: Int) {
        c.appScope.launch { c.settings.update { it.copy(lowBattery = level) } }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(wanted)
        else Toast.makeText(context, context.getString(R.string.low_battery_no_permission), Toast.LENGTH_LONG).show()
    }
    Group(stringResource(R.string.group_notifications), footer = stringResource(R.string.low_battery_footer)) {
        GroupChoice(
            stringResource(R.string.low_battery),
            SettingsFile.LOW_BATTERY_CHOICES.map { it to if (it == 0) stringResource(R.string.off) else stringResource(R.string.low_battery_at, it) },
            settings.lowBattery,
            icon = Icons.Outlined.BatteryAlert,
        ) { level ->
            val allowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (level == 0 || allowed) save(level)
            else {
                wanted = level
                AppLock.expectReturn()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
