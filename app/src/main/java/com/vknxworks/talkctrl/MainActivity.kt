package com.vknxworks.talkctrl

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.talkctrl.ui.lock.AppLockGate
import com.vknxworks.talkctrl.ui.lock.GuardWindow
import com.vknxworks.talkctrl.ui.lock.lockCovering
import com.vknxworks.talkctrl.ui.nav.AppRoot
import com.vknxworks.talkctrl.ui.theme.TalkCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) take(intent)
        secureBeforeFirstFrame()
        setContent { CtrlWindow(this) { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    /** The firmware update notification opens the update screen. */
    private fun take(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_UPDATE) container.openUpdate.value = true
    }

    companion object {
        const val ACTION_SHOW_UPDATE = "com.vknxworks.talkctrl.SHOW_UPDATE"
    }
}

/**
 * Keeps the window out of screenshots and screen recordings from its very first frame when the app opens locked
 * (or "Hide in screenshots" is already known to be on). [CtrlWindow] keeps the flag up to date after that, with
 * the same rule. Call before setContent.
 */
fun ComponentActivity.secureBeforeFirstFrame() {
    val c = container
    if (c.settings.settings.value.hideScreenshots || lockCovering(c.vault)) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * The Ctrl window: follows the phone's dark theme, applies "Hide in screenshots" / "Hide in recent apps",
 * and shows nothing of the app while the app lock is locked.
 */
@Composable
fun CtrlWindow(activity: ComponentActivity, content: @Composable () -> Unit) {
    val c = activity.container
    val dark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(DARK_BAR) else SystemBarStyle.light(LIGHT_BAR, DARK_BAR)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose { }
    }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val secure = settings.hideScreenshots || lockCovering(c.vault)
    val blankRecents = settings.hideRecents || c.vault.enabled
    // No overlays over the lock and PIN screens, and their content hidden from accessibility misuse.
    GuardWindow(lockCovering(c.vault), sensitive = true)
    LaunchedEffect(secure, blankRecents) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setRecentsScreenshotEnabled(!blankRecents)
    }
    TalkCtrlTheme(dark) {
        AppLockGate { content() }
    }
}

private val DARK_BAR = android.graphics.Color.rgb(8, 8, 10)
private val LIGHT_BAR = android.graphics.Color.rgb(245, 245, 241)
