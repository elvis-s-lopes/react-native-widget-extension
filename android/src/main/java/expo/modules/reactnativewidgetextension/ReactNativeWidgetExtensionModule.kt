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

// add news class 

class ReactNativeWidgetExtensionModule() : Module() {
    override fun definition() = ModuleDefinition {
        Name("ReactNativeWidgetExtension")


        Function("startActivity") { jsonPayload: String ->

          Log.v("startActivity", "$jsonPayload")


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
