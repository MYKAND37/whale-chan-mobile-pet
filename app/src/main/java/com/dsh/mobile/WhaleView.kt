package com.dsh.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

/**
 * Whale-chan as an overlay sprite.
 *
 * The artwork is a three-view character sheet (front / side / back) that was
 * cut apart and alpha-keyed at build time. Animation is applied to the sprite
 * as a whole: she bobs while breathing, squashes and lifts on a jump, and
 * spins through the three views when poked repeatedly.
 */
class WhaleView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frame = RectF()

    private val views: List<Bitmap> = listOf(
        R.drawable.whale_front,
        R.drawable.whale_side,
        R.drawable.whale_back
    ).map { BitmapFactory.decodeResource(context.resources, it) }

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

        val bitmap = views.getOrNull(facing) ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || bitmap.isRecycled) return

        // Idle bob: a slow vertical drift, largest at the middle of the cycle.
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
        val scale = minOf(w / bw, h / bh)
        val dw = bw * scale
        val dh = bh * scale
        frame.set(cx - dw / 2f, cy - dh, cx + dw / 2f, cy)

        canvas.drawBitmap(bitmap, null, frame, paint)
        canvas.restore()
    }

    companion object {
        /** Rendered size of the overlay window, in pixels. */
        const val SIZE = 300
    }
}
