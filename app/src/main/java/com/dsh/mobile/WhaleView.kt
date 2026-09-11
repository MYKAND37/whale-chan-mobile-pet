package com.dsh.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.View
import java.io.InputStream
import kotlin.math.sin

/**
 * Whale-chan as an overlay sprite.
 *
 * Artwork is loaded from `assets/whale/` rather than `res/drawable`. Files
 * under `res/` pass through AAPT2, which may re-encode a PNG and can disturb
 * its alpha channel; assets are packaged byte-for-byte, so the decoded bitmap
 * is exactly the artwork that was authored.
 *
 * When no sprite can be decoded the view paints a visible placeholder instead
 * of drawing nothing — a silent blank overlay is impossible to diagnose from a
 * screenshot, which is exactly how the earlier "the whale never appears"
 * report went unexplained.
 */
class WhaleView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 22f
    }
    private val frame = RectF()

    private val spriteNames = listOf(
        "whale/whale_front.png",
        "whale/whale_side.png",
        "whale/whale_back.png"
    )

    private val views: List<Bitmap?> = spriteNames.map { decodeAsset(context, it) }

    /** Diagnostic string rendered when nothing could be decoded. */
    private val failure: String? =
        if (views.all { it == null }) "解码失败\n${spriteNames.size} 张图都没读到" else null

    private var tick = 0f

    /** Index into [views]; advanced by repeated pokes so she turns around. */
    var facing = 0
        set(value) {
            field = ((value % views.size) + views.size) % views.size
            invalidate()
        }

    /** 0f = grounded, 1f = peak of the jump arc. */
    var jumpProgress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** True when at least one sprite decoded. */
    val hasArtwork: Boolean get() = views.any { it != null && !it.isRecycled }

    fun tick() {
        tick += 1f
        invalidate()
    }

    /** Poke reaction: hop in place and turn to the next view. */
    fun poke() {
        facing += 1
        jumpProgress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = views.getOrNull(facing)
            ?: views.firstOrNull { it != null }
            ?: run {
                // Nothing decoded: say so on screen rather than staying blank.
                failure?.let { canvas.drawText(it, 12f, 40f, debugPaint) }
                return
            }
        if (bitmap.isRecycled) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Idle bob: a slow vertical drift.
        val bob = sin(tick / 24f) * h * 0.018f
        // Jump: lift plus a squash-and-stretch so the hop reads as weight.
        val jump = sin(jumpProgress * Math.PI).toFloat()
        val lift = jump * h * 0.20f
        val scaleY = 1f - jump * 0.10f
        val scaleX = 1f + jump * 0.06f

        val cx = w / 2f
        val cy = h - bob - lift

        canvas.save()
        canvas.scale(scaleX, scaleY, cx, cy)

        // Fit the sprite inside the view while preserving its aspect ratio.
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f) {
            canvas.restore()
            return
        }
        val scale = minOf(w / bw, h / bh)
        val dw = bw * scale
        val dh = bh * scale
        frame.set(cx - dw / 2f, cy - dh, cx + dw / 2f, cy)

        canvas.drawBitmap(bitmap, null, frame, paint)
        canvas.restore()
    }

    companion object {
        private const val TAG = "WhaleView"

        /** Rendered size of the overlay window, in dp. */
        const val SIZE_DP = 180

        /**
         * Decode one asset, never throwing. Returns null when the asset is
         * missing or unreadable so a single bad file cannot blank the overlay.
         */
        private fun decodeAsset(context: Context, name: String): Bitmap? = try {
            val stream: InputStream = context.assets.open(name)
            stream.use { input ->
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(input, null, options)?.also { bmp ->
                    Log.d(TAG, "decoded $name -> ${bmp.width}x${bmp.height}")
                } ?: run {
                    Log.w(TAG, "decodeStream returned null for $name")
                    null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to decode $name", t)
            null
        }
    }
}
