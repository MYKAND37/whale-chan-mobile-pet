package com.dsh.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/**
 * Hosts Whale-chan as a system overlay and keeps her alive.
 *
 * The service owns three windows: the whale itself, a speech bubble that
 * fades in on tap, and a small action menu for quick commands.
 */
class PetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var petView: WhaleView
    private lateinit var bubbleView: TextView
    private lateinit var menuView: LinearLayout

    private lateinit var petParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var bubbleVisible = false
    private var menuVisible = false
    private var lastLineIndex = -1

    /** Frame loop: drives the whale's animation. */
    private val frameTick = object : Runnable {
        override fun run() {
            if (!running) return
            petView.tick()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    /** Fades the speech bubble back out. */
    private val hideBubble = Runnable {
        if (bubbleVisible) {
            bubbleView.animate().alpha(0f).setDuration(260).withEndAction {
                runCatching { windowManager.removeView(bubbleView) }
                bubbleVisible = false
            }.start()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())

        createPetWindow()
        createBubbleWindow()
        createMenuWindow()

        running = true
        handler.post(frameTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        for (v in listOf(petView, bubbleView, menuView)) {
            runCatching { windowManager.removeView(v) }
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- windows

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun createPetWindow() {
        petView = WhaleView(this)
        petParams = WindowManager.LayoutParams(
            WhaleView.SIZE, WhaleView.SIZE,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 400
        }

        petView.setOnTouchListener(DragTapListener())
        windowManager.addView(petView, petParams)
    }

    private fun createBubbleWindow() {
        bubbleView = TextView(this).apply {
            setBackgroundResource(R.drawable.bubble_bg)
            setTextColor(0xFF1B2440.toInt())
            textSize = 13f
            setPadding(28, 18, 28, 18)
            alpha = 0f
        }
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun createMenuWindow() {
        menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.menu_bg)
            setPadding(20, 14, 20, 14)
            alpha = 0f
            addView(menuItem(R.string.menu_say_hi) { showBubble(pickLine()); hideMenu() })
            addView(menuItem(R.string.menu_jump) { petView.jump(); hideMenu() })
            addView(menuItem(R.string.menu_turn) { petView.poke(); hideMenu() })
            addView(menuItem(R.string.menu_stop) { stopSelf() })
        }
        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun menuItem(labelRes: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = getString(labelRes)
            textSize = 14f
            setTextColor(0xFF1B2440.toInt())
            setPadding(10, 22, 10, 22)
            isClickable = true
            setOnClickListener { onClick() }
        }

    // ------------------------------------------------------------- behaviour

    /** Distinguishes a tap from a drag, and moves the pet with the finger. */
    private inner class DragTapListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragged = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = petParams.x
                    startY = petParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (hypot(dx, dy) > TAP_SLOP) dragged = true
                    petParams.x = startX + dx.toInt()
                    petParams.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(petView, petParams) }
                    if (dragged) hideMenu()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) onPetTapped()
                    return true
                }
            }
            return false
        }
    }

    private fun onPetTapped() {
        if (menuVisible) {
            hideMenu()
            return
        }
        petView.poke()
        petView.jump()
        showBubble(pickLine())
        showMenu()
    }

    /** Random chatter, so repeated taps do not read as a stuck recording. */
    private fun pickLine(): String {
        val lines = resources.getStringArray(R.array.whale_lines)
        if (lines.isEmpty()) return getString(R.string.line_hello)
        // Avoid repeating the same line twice in a row: it reads as a glitch.
        val index = (Math.random() * lines.size).toInt().coerceIn(0, lines.size - 1)
        return if (lines.size > 1 && index == lastLineIndex) {
            lines[(index + 1) % lines.size]
        } else {
            lastLineIndex = index
            lines[index]
        }
    }

    private fun showBubble(text: String) {
        bubbleView.text = text
        positionBubble()
        if (!bubbleVisible) {
            bubbleVisible = true
            runCatching { windowManager.addView(bubbleView, bubbleParams) }
        }
        bubbleView.animate().alpha(1f).setDuration(180).start()
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, BUBBLE_MS)
    }

    private fun positionBubble() {
        bubbleParams.x = (petParams.x - 40).coerceAtLeast(8)
        bubbleParams.y = (petParams.y - 130).coerceAtLeast(8)
        if (bubbleVisible) {
            runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
        }
    }

    private fun showMenu() {
        menuParams.x = (petParams.x - 20).coerceAtLeast(8)
        menuParams.y = petParams.y + WhaleView.SIZE - 12
        if (!menuVisible) {
            menuVisible = true
            runCatching { windowManager.addView(menuView, menuParams) }
        }
        menuView.animate().alpha(1f).setDuration(150).start()
    }

    private fun hideMenu() {
        if (!menuVisible) return
        menuView.animate().alpha(0f).setDuration(140).withEndAction {
            runCatching { windowManager.removeView(menuView) }
            menuVisible = false
        }.start()
    }

    private fun WhaleView.jump() {
        jumpProgress = 0f
        val start = System.currentTimeMillis()
        val runner = object : Runnable {
            override fun run() {
                val t = (System.currentTimeMillis() - start) / (JUMP_MS * 1f)
                if (t >= 1f) {
                    jumpProgress = 0f
                    return
                }
                jumpProgress = t
                handler.postDelayed(this, FRAME_MS)
            }
        }
        handler.post(runner)
    }

    // ---------------------------------------------------------- notification

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_desc) }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.menu_stop),
                    stopIntent
                ).build()
            )
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.dsh.mobile.STOP"

        private const val CHANNEL_ID = "whale_chan_pet"
        private const val NOTIFICATION_ID = 42
        private const val FRAME_MS = 40L
        private const val TAP_SLOP = 18f
        private const val BUBBLE_MS = 2600L
        private const val JUMP_MS = 520f
    }
}
