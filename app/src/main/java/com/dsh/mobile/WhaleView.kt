package com.dsh.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.View
import kotlin.math.sin

/**
 * Whale-chan as an overlay sprite.
 *
 * The artwork is a three-view character sheet (front / side / back) that was
 * cut apart and alpha-keyed at build time.
 *
 * Decoding deliberately goes through [BitmapFactory.Options] with density
 * scaling disabled: these PNGs live in `drawable-nodpi`, and letting the
 * framework rescale them produced bitmaps whose measured size did not match
 * the artwork, which is what made the sprite vanish at draw time.
 */
class WhaleView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frame = RectF()

    private val views: List<Bitmap?> = listOf(
        R.drawable.whale_front,
        R.drawable.whale_side,
        R.drawable.whale_back
    ).map { decode(context, it) }

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

    /** True when at least one sprite decoded; the activity surfaces this. */
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

        val bitmap = views.getOrNull(facing) ?: views.firstOrNull { it != null } ?: return
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
         * Decode without density scaling and without a colour-space surprise.
         * Returns null instead of throwing, so one bad asset cannot take the
         * whole overlay down.
         */
        private fun decode(context: Context, resId: Int): Bitmap? = try {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(context.resources, resId, options)?.also { bmp ->
                Log.d(TAG, "decoded $resId -> ${bmp.width}x${bmp.height}")
            } ?: run {
                Log.w(TAG, "decodeResource returned null for $resId")
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to decode $resId", t)
            null
        }
    }
}
