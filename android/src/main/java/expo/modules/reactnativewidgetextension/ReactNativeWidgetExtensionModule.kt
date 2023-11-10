package expo.modules.reactnativewidgetextension
import android.util.Log
import android.content.Context
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.ComponentName;

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// add news class 

class ReactNativeWidgetExtensionModule() : Module() {

    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Channel name"
            val descriptionText = "Channel description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("channel_id", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun definition() = ModuleDefinition {
        Name("ReactNativeWidgetExtension")


        Function("startActivity") { jsonPayload: String ->

            Log.v("startActivity", "$jsonPayload")

            // Create a notification and show it
            val builder = NotificationCompat.Builder(context, "channel_id")
                .setSmallIcon(R.drawable.redbox_top_border_background) // Replace with your notification icon
                .setContentTitle("Notification Title")
                .setContentText("Notification Content")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            createNotificationChannel(context)
            with(NotificationManagerCompat.from(context)) {
                notify(1, builder.build()) // 1 is a unique ID for the notification
            }




        }

        Function("areActivitiesEnabled") {
            true
        }


    }

  private val context: Context
    get() = requireNotNull(appContext.reactContext) { "React Application Context is null" }
  
  private val currentActivity
    get() = requireNotNull(appContext.activityProvider?.currentActivity)

  private val pm
    get() = requireNotNull(currentActivity.packageManager)
}
