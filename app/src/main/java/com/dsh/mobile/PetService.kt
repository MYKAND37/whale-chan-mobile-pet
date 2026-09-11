package com.dsh.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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
 * Overlay attachment is the fragile part of this service: `addView` throws
 * when the user has not granted the draw-over-other-apps permission, and an
 * uncaught throw inside a service tears down the whole process. Every step
 * that touches the window manager is therefore guarded, and a failure ends
 * the service cleanly instead of crashing the app.
 */
class PetService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: WhaleView? = null
    private var bubbleView: TextView? = null
    private var menuView: LinearLayout? = null

    private var petParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var overlayAttached = false
    private var bubbleVisible = false
    private var menuVisible = false
    private var lastLineIndex = -1

    /** Frame loop: drives the whale's animation. */
    private val frameTick = object : Runnable {
        override fun run() {
            if (!running) return
            petView?.tick()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    /** Fades the speech bubble back out. */
    private val hideBubble = Runnable {
        val view = bubbleView ?: return@Runnable
        if (bubbleVisible) {
            view.animate().alpha(0f).setDuration(260).withEndAction {
                removeViewSafely(view)
                bubbleVisible = false
            }.start()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Must happen before anything can throw, or Android kills the service.
        if (!enterForeground()) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            stopSelf()
            return
        }

        // Without the overlay permission every addView call throws; bail out.
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }

        if (!createPetWindow()) {
            stopSelf()
            return
        }

        createBubbleWindow()
        createMenuWindow()

        running = true
        PetState.running = true
        handler.post(frameTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A restart with the overlay already gone means the permission was
        // revoked while we were alive; drop out rather than looping.
        if (!overlayAttached && !canDrawOverlays()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        PetState.running = false
        handler.removeCallbacksAndMessages(null)
        bubbleVisible = false
        menuVisible = false
        removeViewSafely(bubbleView)
        removeViewSafely(menuView)
        removeViewSafely(petView)
        overlayAttached = false
        petView = null
        bubbleView = null
        menuView = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ lifecycle

    /** Promote to a foreground service; returns false when the OS refuses. */
    private fun enterForeground(): Boolean = try {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType())
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (t: Throwable) {
        false
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

    // ---------------------------------------------------------------- windows

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** Attaches the whale. Returns false when the OS rejects the window. */
    private fun createPetWindow(): Boolean {
        val wm = windowManager ?: return false
        val view = WhaleView(this)

        // The window is sized in density-independent pixels; using raw pixels
        // made the overlay a tiny postage stamp on high-density screens.
        val density = resources.displayMetrics.density
        val sizePx = (WhaleView.SIZE_DP * density).toInt().coerceAtLeast(1)

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (60 * density).toInt()
            y = (300 * density).toInt()
        }

        view.setOnTouchListener(DragTapListener())
        return try {
            wm.addView(view, params)
            petView = view
            petParams = params
            overlayAttached = true
            true
        } catch (t: Throwable) {
            // BadTokenException when the permission was revoked mid-flight.
            false
        }
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
            addView(menuItem(R.string.menu_jump) { petView?.jump(); hideMenu() })
            addView(menuItem(R.string.menu_turn) { petView?.poke(); hideMenu() })
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

    private fun removeViewSafely(view: View?) {
        val wm = windowManager ?: return
        if (view == null) return
        runCatching { wm.removeView(view) }
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
            val params = petParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (hypot(dx, dy) > TAP_SLOP) dragged = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    if (dragged) {
                        val view = petView
                        val wm = windowManager
                        if (view != null && wm != null) {
                            runCatching { wm.updateViewLayout(view, params) }
                        }
                        hideMenu()
                    }
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
        petView?.poke()
        petView?.jump()
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
        val view = bubbleView ?: return
        val wm = windowManager ?: return
        val params = bubbleParams ?: return

        view.text = text
        positionBubble()
        if (!bubbleVisible) {
            bubbleVisible = try {
                wm.addView(view, params)
                true
            } catch (t: Throwable) {
                false
            }
        }
        view.animate().alpha(1f).setDuration(180).start()
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, BUBBLE_MS)
    }

    private fun positionBubble() {
        val pet = petParams ?: return
        val params = bubbleParams ?: return
        params.x = (pet.x - 40).coerceAtLeast(8)
        params.y = (pet.y - 130).coerceAtLeast(8)
        if (bubbleVisible) {
            val view = bubbleView
            val wm = windowManager
            if (view != null && wm != null) {
                runCatching { wm.updateViewLayout(view, params) }
            }
        }
    }

    private fun showMenu() {
        val view = menuView ?: return
        val wm = windowManager ?: return
        val params = menuParams ?: return
        val pet = petParams ?: return

        params.x = (pet.x - 20).coerceAtLeast(8)
        val density = resources.displayMetrics.density
        params.y = pet.y + (WhaleView.SIZE_DP * density).toInt() - 12
        if (!menuVisible) {
            menuVisible = try {
                wm.addView(view, params)
                true
            } catch (t: Throwable) {
                false
            }
        }
        view.animate().alpha(1f).setDuration(150).start()
    }

    private fun hideMenu() {
        if (!menuVisible) return
        val view = menuView ?: return
        view.animate().alpha(0f).setDuration(140).withEndAction {
            removeViewSafely(view)
            menuVisible = false
        }.start()
    }

    private fun WhaleView.jump() {
        jumpProgress = 0f
        val start = System.currentTimeMillis()
        val runner = object : Runnable {
            override fun run() {
                val t = (System.currentTimeMillis() - start) / JUMP_MS
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
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_desc) }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
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
