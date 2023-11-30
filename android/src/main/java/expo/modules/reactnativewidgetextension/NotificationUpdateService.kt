package expo.modules.reactnativewidgetextension

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.*
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import kotlin.math.min

class NotificationUpdateService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webSocketClient: WebSocketClient
    private var isConnected = false
    private var connectionAttempts = 0
    private var data: String? = null
    private val CHANNEL_ID = "order-status"
    private var backoffTime = 10000L // Inicializar tempo de backoff
    private var shouldReconnect = true

    companion object {
        const val ACTION_STOP_SERVICE = "expo.modules.reactnativewidgetextension.ACTION_STOP_SERVICE"
        const val ACTION_START_SERVICE = "expo.modules.reactnativewidgetextension.ACTION_START_SERVICE"


        @Volatile private var isRunning = false

        fun isServiceRunning() = isRunning
    }


     override fun onCreate() {
        super.onCreate()
         isRunning = true
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

    private fun closeWebSocketConnection() {
        if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
            webSocketClient.close()
        }
        isConnected = false
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            ACTION_START_SERVICE -> {
                // Add your code here to start service functionality
                data = intent.getStringExtra("data")
                if (data != null) {
                    closeWebSocketConnection()
                    handler.removeCallbacksAndMessages(null)
                    processData(data!!)
                }
                // Keep the service running
                return START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                shouldReconnect = false
                stopSelf()
                closeWebSocketConnection()
                handler.removeCallbacksAndMessages(null)
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun processData(data: String) {
        val payload = Gson().fromJson(data, ActivityPayload::class.java)

        payload.uniqueId?.let {
            startForeground(1, createInitialNotification(payload))
            connectToWebSocket(data)
        }
    }


    private fun connectToWebSocket(data: String) {
        val payload = Gson().fromJson(data, ActivityPayload::class.java)

        if (!isConnected && payload.uniqueId != null) {
            val uri = URI.create("wss://web-socket.gpswox.com.br?uniqueId=${payload.uniqueId}")
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
                    if (shouldReconnect) {

                        backoffTime *= 2 // Dobrar o tempo de backoff
                        val reconnectTime = minOf(backoffTime, 60000L) // Limitar a 60 segundos
                        handler.postDelayed({ connectToWebSocket(data) }, reconnectTime)
                    }

                }

                override fun onMessage(message: String) {
                    Log.i("WebSocket", "Message received: $message")
                    val messagePayload = Gson().fromJson(message, ActivityPayload::class.java)
                    val imageCar = payload.carImage;
                    val imageDriver = payload.avatarMini;

                    updateNotification(messagePayload , imageCar, imageDriver)
                }

                override fun onError(ex: Exception) {
                    Log.e("WebSocket", "Error in WebSocket: ${ex.message}")
                    isConnected = false
                    if (shouldReconnect) {

                        backoffTime *= 2 // Dobrar o tempo de backoff
                        val reconnectTime = minOf(backoffTime, 60000L) // Limitar a 60 segundos
                        handler.postDelayed({ connectToWebSocket(data) }, reconnectTime)
                    }
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
    private fun createStopServicePendingIntent(): PendingIntent {
        val stopIntent = Intent(this, NotificationUpdateService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }

        return PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_MUTABLE)
    }


    private fun createInitialNotification(payload: ActivityPayload): Notification {
        val customView = createCustomNotificationView(payload)
        val customViewExp = createCustomNotificationViewExpand(payload)


        val stopServicePendingIntent = createStopServicePendingIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_notifications_24)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(customViewExp)
            .setCustomContentView(customView)
            .addAction(
                0, // Zero indica que nenhum ícone será usado
                "Encerrar", // Texto do botão
                stopServicePendingIntent // PendingIntent para a ação de parar
            )
            .build()
    }

    private fun updateNotification(payload: ActivityPayload, imageCar: String, imageDriver: String) {
        val customView = createCustomNotificationView(payload, imageCar, imageDriver)
        val customViewExp = createCustomNotificationViewExpand(payload, imageCar, imageDriver)


        val stopServicePendingIntent = createStopServicePendingIntent()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_notifications_24)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(customViewExp)
            .setCustomContentView(customView)
            .addAction(
                0, // Zero indica que nenhum ícone será usado
                "Encerrar", // Texto do botão
                stopServicePendingIntent // PendingIntent para a ação de parar
            )
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)

        wakeUpDevice()
    }
    fun doubleToInt(value: Double): Int {
        return (value * 100).toInt()
    }


    private fun createCustomNotificationView(payload: ActivityPayload, imageCar: String = "", imageDriver: String = ""): RemoteViews {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density

        val marginDp = 16f
        val marginPx = (marginDp * density).toInt()
        var bitmapWidth = screenWidth - (marginPx * 2)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P || Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            bitmapWidth = screenWidth * 2
        }

        val progressValue = doubleToInt(payload.deviceProgress)

        val stopPoints = floatArrayOf(0.25f, 0.5f, 0.75f)
        //val progressWithCarBitmap = createProgressBitmapWithDashedLine(this, bitmapWidth, progressValue, stopPoints)
        val progressWithCarBitmap = createProgressBitmapWithDashedLine(this, bitmapWidth, progressValue)


        val customView = RemoteViews(packageName, R.layout.notification_layout).apply {

            setTextViewText(R.id.tvTitle, payload.timeDriving)
            setTextViewText(R.id.tvPlate, payload.devicePlate)
            setTextViewText(R.id.tvModel, payload.dateTimeDevice)
            setTextViewText(R.id.tvCarDetails, payload.deviceAddress)
            setImageViewBitmap(R.id.imageProgress, progressWithCarBitmap)


            if (imageCar.isNotEmpty()) {
                // Use the provided imageCar
                val carBitmap = getBitmapFromUri(this@NotificationUpdateService, Uri.parse(imageCar), 50, 50, 500f, payload.statusColor, 5)
                setImageViewBitmap(R.id.ivAvatar2, carBitmap)
            } else {
                payload.carImage?.takeIf { it.isNotEmpty() }?.let { uriString ->
                    runCatching {
                        Uri.parse(uriString)
                    }.getOrNull()?.let { uri ->
                        val avatarBitmap = getBitmapFromUri(this@NotificationUpdateService, uri, 50, 50, 500f, payload.statusColor, 5)
                        setImageViewBitmap(R.id.ivAvatar2, avatarBitmap)
                    }
                }
            }

            if (imageDriver.isNotEmpty()) {
                // Use the provided imageDriver
                val driverBitmap = getBitmapFromUri(this@NotificationUpdateService, Uri.parse(imageDriver), 50, 50, 500f)
                setImageViewBitmap(R.id.ivAvatar, driverBitmap)
            } else {
                payload.avatarMini?.takeIf { it.isNotEmpty() }?.let { uriString ->
                    runCatching {
                        Uri.parse(uriString)
                    }.getOrNull()?.let { uri ->
                        val avatarBitmap = getBitmapFromUri(this@NotificationUpdateService, uri, 50, 50, 500f)
                        setImageViewBitmap(R.id.ivAvatar, avatarBitmap)
                    }
                }
            }
        }

        return customView
    }

    private fun createCustomNotificationViewExpand(payload: ActivityPayload, imageCar: String = "", imageDriver: String = ""): RemoteViews {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density

        val marginDp = 16f
        val marginPx = (marginDp * density).toInt()
        var bitmapWidth = screenWidth - (marginPx * 2)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P || Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            bitmapWidth = screenWidth * 2
        }

        val progressValue = doubleToInt(payload.deviceProgress)
        val percentageText = (payload.deviceProgress * 100).toInt().toString() + "%"
        val  remainingDistanceText =  "(+"+ payload.remainingDistance + ")"



        val stopPoints = floatArrayOf(0.25f, 0.5f, 0.75f)
        //val progressWithCarBitmap = createProgressBitmapWithDashedLine(this, bitmapWidth, progressValue, stopPoints)
        val progressWithCarBitmap = createProgressBitmapWithDashedLine(this, bitmapWidth, progressValue)


        val customView = RemoteViews(packageName, R.layout.notification_layout_big).apply {
            setTextViewText(R.id.tvTitle, payload.timeDriving)
            setTextViewText(R.id.tvPlate, payload.devicePlate)
            setTextViewText(R.id.tvModel, payload.dateTimeDevice)
            setTextViewText(R.id.tvCarDetails, payload.deviceAddress)
            setTextViewText(R.id.tvEndTextPercente, percentageText)
            setTextViewText(R.id.tvEndTextDistance, remainingDistanceText)
            setImageViewBitmap(R.id.imageProgress, progressWithCarBitmap)
            setTextColor(R.id.tvEndTextPercente, Color.parseColor(getColorHexForPercentage(progressValue)));


            if (imageCar.isNotEmpty()) {
                // Use the provided imageCar
                val carBitmap = getBitmapFromUri(this@NotificationUpdateService, Uri.parse(imageCar), 50, 50, 500f, payload.statusColor, 5)
                setImageViewBitmap(R.id.ivAvatar2, carBitmap)
            } else {
                payload.carImage?.takeIf { it.isNotEmpty() }?.let { uriString ->
                    runCatching {
                        Uri.parse(uriString)
                    }.getOrNull()?.let { uri ->
                        val avatarBitmap = getBitmapFromUri(this@NotificationUpdateService, uri, 50, 50, 500f, payload.statusColor, 5)
                        setImageViewBitmap(R.id.ivAvatar2, avatarBitmap)
                    }
                }
            }

            if (imageDriver.isNotEmpty()) {
                // Use the provided imageDriver
                val driverBitmap = getBitmapFromUri(this@NotificationUpdateService, Uri.parse(imageDriver), 50, 50, 500f)
                setImageViewBitmap(R.id.ivAvatar, driverBitmap)
            } else {
                payload.avatarMini?.takeIf { it.isNotEmpty() }?.let { uriString ->
                    runCatching {
                        Uri.parse(uriString)
                    }.getOrNull()?.let { uri ->
                        val avatarBitmap = getBitmapFromUri(this@NotificationUpdateService, uri, 50, 50, 500f)
                        setImageViewBitmap(R.id.ivAvatar, avatarBitmap)
                    }
                }
            }
        }

        return customView
    }


    fun getBitmapFromUri(context: Context, uri: Uri, width: Int, height: Int, cornerRadius: Float, borderColorHex: String? = null, borderWidth: Int = 0): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.let { originalBitmap ->
                    // Redimensiona o Bitmap para as dimensões especificadas
                    val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

                    // Ajusta as dimensões para incluir a borda
                    val adjustedWidth = width - 2 * borderWidth
                    val adjustedHeight = height - 2 * borderWidth

                    // Cria um novo Bitmap com cantos arredondados e possivelmente com borda
                    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)

                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = Color.BLACK
                    }

                    val rect = Rect(0, 0, adjustedWidth, adjustedHeight)
                    val rectF = RectF(rect)

                    // Desenha o retângulo arredondado
                    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(resizedBitmap, rect, rect, paint)

                    // Desenha a borda, se uma cor de borda for fornecida
                    borderColorHex?.let {
                        val borderPaint = Paint().apply {
                            isAntiAlias = true
                            color = Color.parseColor(borderColorHex)
                            style = Paint.Style.STROKE
                            strokeWidth = borderWidth.toFloat()
                        }
                        val borderRect = RectF(borderWidth / 2f, borderWidth / 2f, adjustedWidth + borderWidth / 2f, adjustedHeight + borderWidth / 2f)
                        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
                    }

                    output
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }


    fun getColorHexForPercentage(percentage: Int): String {
        return when {
            percentage > 30 -> "#00B894" // Verde
            percentage > 20 -> "#ffc107" // Amarelo
            else -> "#D63031" // Vermelho
        }
    }

    private fun createProgressBitmapWithDashedLine(
        context: Context,
        width: Int,
        progress: Int
    ): Bitmap {
        val barHeight = 20f // Altura da barra
        val cornerRadius = 20f // Raio de canto fixo e visível
        val bitmap = Bitmap.createBitmap(width, barHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Barra de progresso de fundo
        val progressRect = RectF(
            0f,
            0f,
            width.toFloat(),
            barHeight
        )
        paint.color = Color.parseColor("#d5d3db")
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, paint)

        // Progresso preenchido
        val progressLength = width * (progress / 100f)
        val progressRectFilled = RectF(
            progressRect.left,
            progressRect.top,
            progressRect.left + progressLength,
            progressRect.bottom
        )

        // Definindo a cor baseada no progresso
        paint.color = when {
            progress > 30 -> Color.parseColor("#00B894")
            progress > 20 -> Color.parseColor("#ffc107")
            else -> Color.parseColor("#D63031")
        }

        canvas.drawRoundRect(progressRectFilled, cornerRadius, cornerRadius, paint)

        return bitmap
    }



    /*private fun createProgressBitmapWithDashedLine(
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
    }*/


    fun Bitmap.toCircularBitmap(): Bitmap {
        val width = this.width
        val height = this.height
        val diameter = min(width, height)

        val outputBitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(outputBitmap)
        val paint = Paint()
        val rect = Rect(0, 0, diameter, diameter)
        val rectF = RectF(rect)

        paint.isAntiAlias = true
        canvas.drawOval(rectF, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, rect, rect, paint)

        return outputBitmap
    }


    override fun onDestroy() {
        shouldReconnect = false
        closeWebSocketConnection()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        isRunning = false

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
        var remainingDistance: String,
        val uniqueId: String,
    )

    data class MessagePayload(
        val avatarMini: String? = null,
        val carImage: String? = null,
    )


}