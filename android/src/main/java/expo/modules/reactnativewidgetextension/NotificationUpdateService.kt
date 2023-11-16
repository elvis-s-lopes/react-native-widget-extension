package expo.modules.reactnativewidgetextension

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.*
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import kotlin.random.Random

class NotificationUpdateService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webSocketClient: WebSocketClient
    private var isConnected = false
    private var connectionAttempts = 0
    private var data: String? = null
    private val CHANNEL_ID = "order-status"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "live-activities"
            val descriptionText = "Channel description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        data = intent.getStringExtra("data")
        if (data != null) {
            processData(data!!)
        }
        return START_STICKY
    }

    private fun processData(data: String) {
        val payload = Gson().fromJson(data, ActivityPayload::class.java)
        if (payload.uniqueId !== null) {
            startForeground(1, createInitialNotification(payload))
            connectToWebSocket(data)
        }
    }

    private fun connectToWebSocket(data: String) {
        val delayMillis = 30000L
        val payload = Gson().fromJson(data, ActivityPayload::class.java)

        if (!isConnected && payload.uniqueId !== null) {
            Log.i("ActivityPayload", payload.uniqueId)

            val uri = URI.create("ws://192.168.31.212?uniqueId=" + payload.uniqueId)
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.i("WebSocket", "Connected to WebSocket")
                    isConnected = true
                    connectionAttempts = 0
                    val message = "{\"uniqueId\":\"${payload.uniqueId}\"}"
                    send(message)
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.i("WebSocket", "Connection closed: $code, $reason")
                    isConnected = false

                    handler.postDelayed({ connectToWebSocket(data) }, delayMillis)
                }

                override fun onMessage(message: String) {
                    Log.i("WebSocket", "Message received: $message")
                    val payload = Gson().fromJson(message, ActivityPayload::class.java)
                    updateNotification(payload)
                }

                override fun onError(ex: Exception) {
                    Log.e("WebSocket", "Error in WebSocket: ${ex.message}")
                    isConnected = false
                    handler.postDelayed({ connectToWebSocket(data) }, delayMillis)
                }
            }
            webSocketClient.connect()
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

    private fun createInitialNotification(payload: ActivityPayload): Notification {
        val customView = createCustomNotificationView(payload)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .build()
    }

    private fun updateNotification(payload: ActivityPayload) {
        val customView = createCustomNotificationView(payload)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(customView)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)

        wakeUpDevice()
    }

    private fun createCustomNotificationView(payload: ActivityPayload): RemoteViews {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density

        val marginDp = 16f
        val marginPx = (marginDp * density).toInt()
        var bitmapWidth = screenWidth - (marginPx * 2)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            bitmapWidth = screenWidth * 2
        }

        val randomProgress = Random.nextInt(1, 70)
        val stopPoints = floatArrayOf(0.25f, 0.5f, 0.75f)
        val progressWithCarBitmap = createProgressBitmapWithDashedLine(this, bitmapWidth, randomProgress, stopPoints)

        val customView = RemoteViews(packageName, R.layout.notification_layout).apply {
            setTextViewText(R.id.tvTitle, payload.timeDriving)
            setTextViewText(R.id.tvPlate, payload.devicePlate)
            setTextViewText(R.id.tvModel, payload.deviceModel)
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
        val carBitmap = BitmapFactory.decodeResource(resources, R.drawable.car_mini) // Substitua pelo seu recurso

        val newCarWidth = 130 // Ajuste conforme necessário
        val newCarHeight = 80 // Ajuste conforme necessário
        val scaledCarBitmap = scaleBitmap(carBitmap, newCarWidth, newCarHeight)

        val carWidth = scaledCarBitmap.width
        val carHeight = scaledCarBitmap.height
        val barHeight = 25f // Ajuste conforme necessário

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
        if (::webSocketClient.isInitialized) {
            webSocketClient.close()
        }
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
        val deviceProgress: Double,
        val uniqueId: String,
    )
}
