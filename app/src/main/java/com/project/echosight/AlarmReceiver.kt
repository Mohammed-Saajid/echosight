package com.project.echosight
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm triggered!") // Log to check if receiver is called

        Toast.makeText(context, "Alarm ringing!", Toast.LENGTH_LONG).show()

        val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = RingtoneManager.getRingtone(context, notificationUri)
        ringtone.play()
    }
}
