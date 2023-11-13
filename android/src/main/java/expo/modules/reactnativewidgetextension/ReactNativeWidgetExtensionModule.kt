package expo.modules.reactnativewidgetextension
import android.content.Context
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.util.Log
import com.google.gson.Gson
import java.net.URL
import android.content.Intent
import androidx.core.content.ContextCompat.startForegroundService
import java.io.Serializable

// add news class 

class ReactNativeWidgetExtensionModule() : Module() {



    override fun definition() = ModuleDefinition {
        Name("ReactNativeWidgetExtension")

        Function("startActivity") { jsonPayload: String ->


            try {
                Log.i("startActivity()", "startActivity()")

                Result.success("Serviço de notificação iniciado")

                val payload = Gson().fromJson(jsonPayload, ActivityPayload::class.java)

                // Extract file names from avatarMini and carImage URLs
                val avatarMiniFileName = URL(payload.avatarMini).path
                val carImageFileName = URL(payload.carImage).path

                //Log.i("Avatar Mini File Name", "Error: ${avatarMiniFileName}")
                //Log.i("Car Image File Name", "Error: ${carImageFileName}")

                // Iniciar o serviço de notificação
                val serviceIntent = Intent(context, NotificationUpdateService::class.java)
                //serviceIntent.putExtra("payloadKey", payload as Serializable)
                startForegroundService(context, serviceIntent)





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

data class ActivityPayload(
    val avatarMini: String,
    val carImage: String,
    val statusColor: String,
    val driverName: String,
    val devicePlate: String,
    val deviceModel: String,
    val timeDriving: String,
    val dateTimeDevice: String,
    val deviceAddress: String,
    val deviceProgress: Double
)



  private val context: Context
    get() = requireNotNull(appContext.reactContext) { "React Application Context is null" }
  
  private val currentActivity
    get() = requireNotNull(appContext.activityProvider?.currentActivity)

  private val pm
    get() = requireNotNull(currentActivity.packageManager)
}
