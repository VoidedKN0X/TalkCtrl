package com.vknxworks.talkctrl.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import com.vknxworks.talkctrl.MainActivity
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.bt.GaiaGatt
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.protocol.Gaia
import com.vknxworks.talkctrl.protocol.GaiaDialect
import com.vknxworks.talkctrl.protocol.UpgradeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Ending { COMPLETE, ABORTED, BATTERY_LOW, UNIT_ERROR, CONNECTION_LOST, PROTOCOL, NOT_READY, FILE }

data class TransferState(
    val running: Boolean = false,
    val phase: UpgradeEngine.Phase? = null,
    val percent: Double = 0.0,
    val waitingForUnit: Boolean = false,
    val end: Ending? = null,
    val code: Int = 0,
)

/**
 * Runs a firmware transfer (GAIA upgrade over BLE) as a connectedDevice foreground service, so it keeps
 * going if you leave the app. Shows progress in a notification with Abort.
 */
class FirmwareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var gaia: GaiaGatt? = null
    private var engine: UpgradeEngine? = null
    private var address: String? = null
    private var reconnects = 0
    private var lastNotified = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("gaia").also { it.start() }
        handler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (_state.value.running) return START_NOT_STICKY
                val addr = intent.getStringExtra(EXTRA_ADDRESS)
                val path = intent.getStringExtra(EXTRA_FILE)
                if (addr == null || path == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, notification(0, getString(R.string.upd_phase_preparing)), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                begin(addr, File(path), if (intent.getBooleanExtra(EXTRA_V3, false)) GaiaDialect.V3 else GaiaDialect.V2)
            }
            ACTION_ABORT -> handler.post {
                val e = engine
                if (e == null || gaia == null) finish(Ending.ABORTED) else e.abort()
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(addr: String, file: File, dialect: GaiaDialect) {
        address = addr
        reconnects = 0
        _state.value = TransferState(running = true, phase = UpgradeEngine.Phase.CONNECTING)
        scope.launch {
            val bytes = try {
                file.readBytes()
            } catch (_: Exception) {
                return@launch finish(Ending.FILE)
            }
            // Ask the unit over the control link to get ready (Cardo's OTA_START), then free the link for GAIA.
            val link = container.link
            link.requestOtaReady(8000)
            link.disconnect()
            delay(1500)
            handler.post {
                engine = UpgradeEngine(bytes, io, Gaia.chunkSize(Gaia.blePayloadSize(23)), dialect)
                connectGaia()
            }
        }
    }

    private fun connectGaia() {
        val addr = address ?: return
        gaia?.close()
        val g = GaiaGatt(applicationContext, gattListener)
        gaia = g
        if (!g.connect(addr)) scheduleReconnect()
    }

    private val gattListener = object : GaiaGatt.Listener {
        override fun onReady(mtu: Int) {
            handler.post {
                reconnects = 0
                _state.update { it.copy(waitingForUnit = false) }
                engine?.setChunkSize(Gaia.chunkSize(Gaia.blePayloadSize(mtu)))
                engine?.start()
            }
        }

        override fun onPacket(bytes: ByteArray) {
            handler.post { engine?.onPacket(bytes) }
        }

        override fun onDisconnected() {
            handler.post {
                val e = engine ?: return@post
                if (!_state.value.running) return@post
                val expected = e.onDisconnected()
                _state.update { it.copy(waitingForUnit = true) }
                // The unit reboots after the transfer: give it up to 5 minutes. Otherwise retry a few times
                // and resume where it stopped (the unit remembers its resume point).
                if (expected || reconnects < 5) scheduleReconnect() else finish(Ending.CONNECTION_LOST)
            }
        }
    }

    private fun scheduleReconnect() {
        reconnects++
        if (reconnects > 60) return finish(Ending.CONNECTION_LOST)
        handler.postDelayed({ if (_state.value.running) connectGaia() }, 5000)
    }

    private val io = object : UpgradeEngine.Io {
        override fun send(gaiaPacket: ByteArray) {
            if (gaia?.send(gaiaPacket) != true) {
                // The write didn't go out; the disconnect callback (if any) takes over.
            }
        }

        override fun schedule(delayMs: Long, block: () -> Unit) {
            handler.postDelayed(block, delayMs.coerceIn(0, 60_000))
        }

        override fun onPhase(phase: UpgradeEngine.Phase) {
            _state.update { it.copy(phase = phase) }
            notifyProgress()
        }

        override fun onProgress(percent: Double) {
            _state.update { it.copy(percent = percent) }
            notifyProgress()
        }

        override fun onEnd(end: UpgradeEngine.End) {
            when (end) {
                UpgradeEngine.End.Complete -> finish(Ending.COMPLETE)
                UpgradeEngine.End.Aborted -> finish(Ending.ABORTED)
                is UpgradeEngine.End.Failed -> finish(
                    when (end.reason) {
                        UpgradeEngine.Reason.BATTERY_LOW -> Ending.BATTERY_LOW
                        UpgradeEngine.Reason.NOT_READY -> Ending.NOT_READY
                        UpgradeEngine.Reason.PROTOCOL -> Ending.PROTOCOL
                        else -> Ending.UNIT_ERROR
                    },
                    end.code,
                )
            }
        }
    }

    private fun finish(end: Ending, code: Int = 0) {
        // Give the last packet (disconnect or abort) a moment to go out.
        handler.postDelayed({
            gaia?.close()
            gaia = null
            engine = null
        }, 1000)
        _state.update { it.copy(running = false, end = end, code = code, waitingForUnit = false) }
        scope.launch {
            stopForeground(STOP_FOREGROUND_REMOVE)
            val text = getString(if (end == Ending.COMPLETE) R.string.upd_done_notif else R.string.upd_stopped_notif)
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_DONE_ID, doneNotification(text))
            stopSelf()
        }
    }

    private fun notifyProgress() {
        val p = _state.value.percent.toInt()
        if (p == lastNotified && _state.value.phase != UpgradeEngine.Phase.REBOOTING) return
        lastNotified = p
        val text = getString(
            when (_state.value.phase) {
                UpgradeEngine.Phase.TRANSFERRING -> R.string.upd_phase_transferring
                UpgradeEngine.Phase.VALIDATING -> R.string.upd_phase_validating
                UpgradeEngine.Phase.REBOOTING -> R.string.upd_phase_rebooting
                UpgradeEngine.Phase.COMMITTING -> R.string.upd_phase_committing
                UpgradeEngine.Phase.DONE -> R.string.upd_phase_done
                else -> R.string.upd_phase_preparing
            },
        )
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notification(p, text))
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.ch_update), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.ch_update_desc)
                },
            )
        }
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_UPDATE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notification(percent: Int, text: String): Notification {
        ensureChannel()
        val abort = PendingIntent.getService(
            this, 1, Intent(this, FirmwareService::class.java).setAction(ACTION_ABORT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(getString(R.string.upd_notif_title))
            .setContentText(text)
            .setProgress(100, percent, _state.value.phase == null || _state.value.phase == UpgradeEngine.Phase.CONNECTING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(Notification.Action.Builder(null, getString(R.string.upd_abort), abort).build())
            .build()
    }

    private fun doneNotification(text: String): Notification {
        ensureChannel()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(getString(R.string.upd_notif_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
    }

    override fun onDestroy() {
        gaia?.close()
        thread.quitSafely()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "update"
        private const val NOTIF_ID = 7
        private const val NOTIF_DONE_ID = 8
        const val ACTION_START = "com.vknxworks.talkctrl.UPDATE_START"
        const val ACTION_ABORT = "com.vknxworks.talkctrl.UPDATE_ABORT"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_FILE = "file"
        const val EXTRA_V3 = "v3"

        private val _state = MutableStateFlow(TransferState())
        val state: StateFlow<TransferState> = _state.asStateFlow()

        fun start(context: Context, address: String, file: File, v3: Boolean) {
            _state.value = TransferState()
            context.startForegroundService(
                Intent(context, FirmwareService::class.java).setAction(ACTION_START).putExtra(EXTRA_ADDRESS, address).putExtra(EXTRA_FILE, file.path).putExtra(EXTRA_V3, v3),
            )
        }

        fun abort(context: Context) {
            context.startService(Intent(context, FirmwareService::class.java).setAction(ACTION_ABORT))
        }

        fun clear() {
            if (!_state.value.running) _state.value = TransferState()
        }
    }
}
