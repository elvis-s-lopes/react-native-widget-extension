package expo.modules.reactnativewidgetextension
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.startForegroundService
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

// add news class 

class ReactNativeWidgetExtensionModule() : Module() {



    override fun definition() = ModuleDefinition {
        Name("ReactNativeWidgetExtension")

        Function("startActivity") { jsonPayload: String ->


            try {
                Log.i("startActivity()", "startActivity()")

                Result.success("Serviço de notificação iniciado")

                // Iniciar o serviço de notificação
                val serviceIntent = Intent(context, NotificationUpdateService::class.java)
                serviceIntent.action = NotificationUpdateService.ACTION_START_SERVICE
                serviceIntent.putExtra("data", jsonPayload)
                //startForegroundService(context, serviceIntent)
                context.startService(serviceIntent)


                //val startServiceIntent = Intent(context, NotificationUpdateService::class.java)
                //startServiceIntent.action = NotificationUpdateService.ACTION_START_SERVICE
                //context.startService(startServiceIntent)


                Result.success("Some result or token")
            } catch (e: Exception) {
                Log.i("startActivity", "Error: ${e.message}")
                Result.failure(e)
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
