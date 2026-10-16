package com.fahad.easypeasy.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fahad.easypeasy.MainActivity
import com.fahad.easypeasy.R
import com.fahad.easypeasy.engine.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

/** Non-sticky: process death never silently restarts microphone recording. */
@AndroidEntryPoint
class ConversationService : Service() {
    @Inject lateinit var output: AndroidSpeechOutput
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var manager: ConversationManager? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP" || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("conversation", "Live Korean practice", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ConversationService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "conversation").setSmallIcon(R.drawable.mascot).setContentTitle("Easy Peasy • microphone in use").setContentIntent(open).setOngoing(true).addAction(0, "Stop", stop).build()
        ServiceCompat.startForeground(this, 20, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        // Fail closed: this build has no native Korean STT adapter. Never capture audio as a demo.
        val unavailable = FakeEngine()
        manager = ConversationManager(scope, unavailable, unavailable, output) { ConversationContext(emptyList(), "", "") }
        if (manager?.start() != true) stopSelf()
        return START_NOT_STICKY
    }
    @Deprecated("Android memory callback")
    override fun onLowMemory() { manager?.stop(); stopSelf(); super.onLowMemory() }
    override fun onDestroy() { manager?.stop(); scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
}
