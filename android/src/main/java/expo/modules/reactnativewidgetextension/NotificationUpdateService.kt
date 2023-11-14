package expo.modules.reactnativewidgetextension

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Path
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.*
import kotlin.random.Random

class NotificationUpdateService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private val CHANNEL_ID = "order-status"
    private lateinit var webSocketClient: WebSocketClient
    private var isConnected = false
    private var connectionAttempts = 0
    private val maxConnectionAttempts = 3

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        startForeground(1, createInitialNotification())
        connectToWebSocket()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "live-activities"
            val descriptionText = "Channel description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("order-status", name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun connectToWebSocket() {
        if (!isConnected) {
            val uri = URI.create("ws://192.168.15.112")
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.i("WebSocket", "Conectado ao WebSocket")
                    isConnected = true
                    connectionAttempts = 0 // Resetar tentativas de conexão
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.i("WebSocket", "Conexão fechada: $code, $reason")
                    isConnected = false
                    // Reconecte-se ao WebSocket
                    //val delayMillis = 20000L // Intervalo de espera entre tentativas (5 segundos)
                    //handler.postDelayed({
                    //    connectToWebSocket()
                    //}, delayMillis)
                }

                override fun onMessage(message: String) {
                    Log.i("WebSocket", "Mensagem recebida: $message")
                    // Trate a mensagem WebSocket aqui e atualize a notificação conforme necessário
                    val payload = Gson().fromJson(message, ActivityPayload::class.java)
                    updateNotification(payload)
                }

                override fun onMessage(bytes: ByteBuffer) {
                    // Implemente isso se você estiver enviando dados binários via WebSocket
                }

                override fun onError(ex: Exception) {
                    Log.e("WebSocket", "Erro no WebSocket: ${ex.message}")
                    isConnected = false
                    val delayMillis = 30000L // Intervalo de espera entre tentativas (5 segundos)
                    handler.postDelayed({
                        connectToWebSocket()
                    }, delayMillis)
                }
            }
            webSocketClient.connect()
        }
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun vibrateDevice() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }

    private fun wakeUpDevice() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MyApp:WakeLockTag"
        )
        wakeLock.acquire(3000) // Acende a tela por 3 segundos
        wakeLock.release() // É importante liberar o WakeLock após o uso
    }

    private fun createInitialNotification(): Notification {
        val customView = createCustomNotificationView("Estacionado")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Serviço em Segundo Plano")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
        return builder.build()
    }

    private fun updateNotification(payload: ActivityPayload) {
        Log.i("deviceData", "${payload.devicePlate}")

        val customView = createCustomNotificationView("${payload.timeDriving}")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(customView)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)

        //vibrateDevice()
        wakeUpDevice()
    }

    private fun createCustomNotificationView(text: String): RemoteViews {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density

        val marginDp = 16f
        val marginPx = (marginDp * density).toInt()
        var bitmapWidth = screenWidth - (marginPx * 2)

        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.P) {
            bitmapWidth = screenWidth * 2
        }
        val randomProgress = Random.nextInt(1, 70)

        val stopPoints = floatArrayOf(0.25f, 0.5f, 0.75f)
        val progressWithCarBitmap = createProgressBitmapWithDashedLine(this, bitmapWidth, randomProgress, stopPoints)

        val customView = RemoteViews(packageName, R.layout.notification_layout).apply {
            setTextViewText(R.id.tvTitle, text)
            setTextViewText(R.id.tvCarDetails, "Elvis Lopes")
            setImageViewBitmap(R.id.imageProgress, progressWithCarBitmap)
        }

        return customView
    }

    private fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun createProgressBitmapWithDashedLine(
        context: Context,
        width: Int,
        progress: Int,
        stopPoints: FloatArray
    ): Bitmap {
        val resources = context.resources
        val carBitmap = BitmapFactory.decodeResource(resources, R.drawable.car_mini)

        val newCarWidth = 130
        val newCarHeight = 80
        val scaledCarBitmap = scaleBitmap(carBitmap, newCarWidth, newCarHeight)

        val carWidth = scaledCarBitmap.width
        val carHeight = scaledCarBitmap.height
        val barHeight = 25f

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

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        webSocketClient.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
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
}
