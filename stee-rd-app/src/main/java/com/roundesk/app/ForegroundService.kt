package com.roundesk.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.roundesk.sdk.config.AppController
import com.roundesk.sdk.socket.SocketConnection
import com.roundesk.sdk.util.Constants
import com.roundesk.sdk.util.LogUtil

class ForegroundService : Service() {
    private val CHANNEL_ID = "STEE-SDK SOCKET Service"
    var socketConnection: SocketConnection? = null

    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ForegroundService::class.java)
            context.stopService(stopIntent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread
        val input = intent?.getStringExtra("inputExtra")
        Constants.SocketSuffix.SOCKET_CONNECT_SEND_CALL_TO_CLIENT =
            SocketConstants.SocketSuffix.SOCKET_CONNECT_SEND_CALL_TO_CLIENT
        Constants.socketConnection = SocketConnection()
        Constants.socketConnection?.connectSocket()
        socketConnection = AppController.getInstance()?.getSocketInstance()
        LogUtil.e("AppBaseActivity", "socketConnection : ${socketConnection.toString()}")
        createNotificationChannel()
        val notificationIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("STEE-SDK SOCKET Service")
            .setContentText(input)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        //stopSelf();
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "STEE-SDK SOCKET Service Channel", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.e("ForegroundService","ForegroundService Stopped")
        stopSelf()
    }
}