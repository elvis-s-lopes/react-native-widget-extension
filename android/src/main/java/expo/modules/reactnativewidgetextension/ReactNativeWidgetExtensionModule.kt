package expo.modules.reactnativewidgetextension
import android.content.Context
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
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

            try {
                logger.i("startActivity()", "startActivity()")

                val payload = Gson().fromJson(jsonPayload, ActivityPayload::class.java)

                // Extract file names from avatarMini and carImage URLs
                val avatarMiniFileName = URL(payload.avatarMini).path
                val carImageFileName = URL(payload.carImage).path

                logger.i("Avatar Mini File Name: $avatarMiniFileName")
                logger.i("Car Image File Name: $carImageFileName")


                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val density = displayMetrics.density

                // Defina uma margem padrão em dp que será convertida em pixels. Ajuste este valor conforme necessário.
                val marginDp = 16f // ou outro valor que melhor se adapte ao layout desejado
                // Converta a margem dp para pixels baseada na densidade da tela
                val marginPx = (marginDp * density).toInt()

                // Ajuste a largura do bitmap de acordo com as margens. Subtraia as margens do lado esquerdo e direito.
                var bitmapWidth = screenWidth - (marginPx * 2)

                // Verifique a versão do Android, e se for Android 9 (API level 28), ajuste a largura conforme necessário
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                    bitmapWidth = screenWidth * 2 // Ajuste a largura do bitmap subtraindo a margem adicional de ambos os lados
                }

                val stopPoints = floatArrayOf(0.25f, 0.5f, 0.75f) // Pontos de parada em 25%, 50%, e 75%
                val progressWithCarBitmap = createProgressBitmapWithDashedLine(context, bitmapWidth, 80, stopPoints)

                val customView = RemoteViews(context.packageName, R.layout.notification_layout).apply {
                    setTextViewText(R.id.tvTitle, "Estacionado há 0 min")
                    setTextViewText(R.id.tvCarDetails, "Elvis Lopes")
                    setImageViewBitmap(R.id.imageProgress, progressWithCarBitmap)
                }


                val customViewBig = RemoteViews(context.packageName, R.layout.notification_layout).apply {
                    setTextViewText(R.id.tvTitle, "Estacionado há 0 min")
                    setTextViewText(R.id.tvCarDetails, "FHN1230: Vermelho Toyota Etios")

                }

                // Create a notification and show it
                val builder = NotificationCompat.Builder(context, "channel_id")
                    .setContentTitle("")
                    .setShowWhen(false)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(customView)
                    .setCustomBigContentView(customViewBig)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(false)



                createNotificationChannel(context)
                with(NotificationManagerCompat.from(context)) {
                    notify(1, builder.build()) // 1 is a unique ID for the notification
                }

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

    fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun createProgressBitmapWithDashedLine(
        context: Context,
        width: Int,
        progress: Int,
        stopPoints: FloatArray,
    ): Bitmap {
        val resources = context.resources
        val carBitmap = BitmapFactory.decodeResource(resources, R.drawable.car_mini)

        // Novas dimensões para o carro
        val newCarWidth = 130 // Substitua com a largura desejada
        val newCarHeight = 80 // Substitua com a altura desejada
        val scaledCarBitmap = scaleBitmap(carBitmap, newCarWidth, newCarHeight)

        val carWidth = scaledCarBitmap.width
        val carHeight = scaledCarBitmap.height
        val barHeight = 25f // Altura da barra de progresso

        val totalWidth = width + carWidth
        val bitmap = Bitmap.createBitmap(totalWidth, carHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Barra de progresso de fundo
        val progressRect = RectF(
            (carWidth / 2).toFloat(),
            (carHeight - barHeight) / 2,
            width.toFloat() + (carWidth / 2),
            (carHeight + barHeight) / 2
        )
        paint.color = Color.parseColor("#d5d3db")
        canvas.drawRoundRect(progressRect, barHeight / 2, barHeight / 2, paint)

        // Progresso preenchido
        val progressLength = progressRect.width() * (progress / 100f)
        val progressRectFilled = RectF(
            progressRect.left,
            progressRect.top,
            progressRect.left + progressLength,
            progressRect.bottom
        )
        paint.color = Color.parseColor("#2761f0")
        canvas.drawRoundRect(progressRectFilled, barHeight / 2, barHeight / 2, paint)

        // Linha pontilhada para o progresso não completo
        val dashedLinePaint = Paint(paint).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = barHeight
            pathEffect = DashPathEffect(floatArrayOf(60f, 70f), 0f)
        }

        // Desenhar a linha pontilhada na seção não completa da barra de progresso
        if (progress < 100) {
            val path = Path()
            path.moveTo(progressRectFilled.right, progressRect.centerY())
            path.lineTo(progressRect.right, progressRect.centerY())
            canvas.drawPath(path, dashedLinePaint)
        }

        // Desenhar os pontos de parada
        paint.style = Paint.Style.FILL
        stopPoints.sorted().forEach { stopPoint ->
            val stopX = progressRect.left + (stopPoint * progressRect.width())
            paint.color = Color.parseColor("#a7bce3") // Cor da borda do ponto de parada
            canvas.drawCircle(stopX, progressRect.centerY(), 20f, paint)
            paint.color = Color.parseColor("#2761f0") // Cor do ponto de parada
            canvas.drawCircle(stopX, progressRect.centerY(), 10f, paint)
        }

        // Desenhar o carro redimensionado
        val carX = progressRectFilled.right - carWidth / 2f
        canvas.drawBitmap(scaledCarBitmap, carX, (carHeight - scaledCarBitmap.height) / 2f, null)

        // Reciclar os bitmaps se não forem mais necessários
        carBitmap.recycle()
        scaledCarBitmap.recycle()

        return bitmap
    }


  private val context: Context
    get() = requireNotNull(appContext.reactContext) { "React Application Context is null" }
  
  private val currentActivity
    get() = requireNotNull(appContext.activityProvider?.currentActivity)

  private val pm
    get() = requireNotNull(currentActivity.packageManager)
}
