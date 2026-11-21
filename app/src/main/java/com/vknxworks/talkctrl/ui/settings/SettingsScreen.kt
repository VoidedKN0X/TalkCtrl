package com.vknxworks.talkctrl.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.data.lock.LockVault
import com.vknxworks.talkctrl.data.settings.AppSettings
import com.vknxworks.talkctrl.data.settings.LockTimeout
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupChoice
import com.vknxworks.talkctrl.ui.components.GroupRow
import com.vknxworks.talkctrl.ui.components.GroupSwitch
import com.vknxworks.talkctrl.ui.components.ScreenTopBar
import com.vknxworks.talkctrl.ui.lock.AppLock
import com.vknxworks.talkctrl.ui.lock.PinRequest
import com.vknxworks.talkctrl.ui.lock.findActivity
import kotlinx.coroutines.launch

/** Settings, opened from the You page (tap the profile picture). TalkCtrl follows the phone's dark theme. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { c.settings.update(transform) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 4.dp, bottom = 24.dp)) {
            Group(stringResource(R.string.group_connection), footer = stringResource(R.string.connection_footer)) {
                GroupSwitch(stringResource(R.string.use_rfcomm), stringResource(R.string.use_rfcomm_sub), settings.useRfcomm, Icons.Outlined.Bluetooth) { on ->
                    set { it.copy(useRfcomm = on) }
                }
            }
            PrivacyAndSecurityGroup(settings)
            BackupGroup()
        }
    }
}
/** Privacy and security: the shared Ctrl series app lock, then hiding TalkCtrl in screenshots. */
@Composable
private fun PrivacyAndSecurityGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.app_lock_footer)) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            checked = lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                checked = c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                settings.lockTimeout,
                icon = Icons.Outlined.Timer,
            ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            checked = settings.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            checked = settings.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
    }
}
