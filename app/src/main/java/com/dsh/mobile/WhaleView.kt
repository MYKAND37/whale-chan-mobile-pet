package com.dsh.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Whale-chan, drawn entirely with Canvas — no image assets required.
 *
 * Animation is driven by [tick], a monotonically increasing frame counter
 * supplied by the owning service. Everything else derives from it, so the
 * pet breathes, blinks and wags its tail without any timeline bookkeeping.
 */
class WhaleView(context: Context) : View(context) {

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BODY }
    private val belly = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BELLY }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUSH }
    private val spout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SPOUT
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val bodyPath = Path()
    private val tailPath = Path()
    private val finPath = Path()
    private val eyeRect = RectF()

    private var tick = 0f
    private var blinkFrames = 0

    /** Vertical squash used by the "jump" reaction, 0f = normal. */
    var jumpProgress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Advance the animation by one frame. */
    fun tick() {
        tick += 1f
        if (blinkFrames > 0) {
            blinkFrames--
        } else if ((tick.toInt() % BLINK_PERIOD) == 0) {
            blinkFrames = BLINK_LENGTH
        }
        invalidate()
    }

    /** Trigger a single blink right now (used as a poke reaction). */
    fun blink() {
        blinkFrames = BLINK_LENGTH
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Breathing: a slow sine on the vertical axis.
        val breathe = sin(tick / 22f) * 0.022f
        // The jump reaction lifts and squashes the whole pet.
        val jump = sin(jumpProgress * Math.PI).toFloat()
        val squash = 1f - jump * 0.18f
        val lift = jump * h * 0.22f

        canvas.save()
        canvas.translate(0f, -lift)
        canvas.scale(1f + breathe, squash + breathe, w / 2f, h)

        val cx = w / 2f
        val cy = h * 0.56f
        val bodyW = w * 0.78f
        val bodyH = h * 0.50f

        drawTail(canvas, cx, cy, bodyW, bodyH)
        drawFins(canvas, cx, cy, bodyW, bodyH)
        drawBody(canvas, cx, cy, bodyW, bodyH)
        drawFace(canvas, cx, cy, bodyW, bodyH, w, h)
        drawSpout(canvas, cx, cy, bodyH)

        canvas.restore()
    }

    /** Two-lobed fluke that wags with the body rhythm. */
    private fun drawTail(canvas: Canvas, cx: Float, cy: Float, bodyW: Float, bodyH: Float) {
        val wag = sin(tick / 14f) * 0.14f
        val baseX = cx + bodyW * 0.40f
        val baseY = cy - bodyH * 0.06f

        canvas.save()
        canvas.translate(baseX, baseY)
        canvas.rotate(wag * 26f)

        tailPath.reset()
        tailPath.moveTo(0f, 0f)
        tailPath.quadTo(bodyW * 0.16f, -bodyH * 0.30f, bodyW * 0.30f, -bodyH * 0.24f)
        tailPath.quadTo(bodyW * 0.20f, -bodyH * 0.05f, bodyW * 0.22f, 0f)
        tailPath.quadTo(bodyW * 0.20f, bodyH * 0.05f, bodyW * 0.30f, bodyH * 0.24f)
        tailPath.quadTo(bodyW * 0.16f, bodyH * 0.30f, 0f, 0f)
        tailPath.close()

        canvas.drawPath(tailPath, body)
        canvas.restore()
    }

    /** Pectoral fin on the visible side, flapping gently. */
    private fun drawFins(canvas: Canvas, cx: Float, cy: Float, bodyW: Float, bodyH: Float) {
        val flap = sin(tick / 18f) * 0.18f

        canvas.save()
        canvas.translate(cx - bodyW * 0.06f, cy + bodyH * 0.12f)
        canvas.rotate(-18f + flap * 24f)
        finPath.reset()
        finPath.moveTo(0f, 0f)
        finPath.quadTo(-bodyW * 0.10f, bodyH * 0.18f, -bodyW * 0.28f, bodyH * 0.30f)
        finPath.quadTo(-bodyW * 0.10f, bodyH * 0.30f, 0f, bodyH * 0.08f)
        finPath.close()
        canvas.drawPath(finPath, body)
        canvas.restore()
    }

    /** Rounded body plus the lighter belly patch. */
    private fun drawBody(canvas: Canvas, cx: Float, cy: Float, bodyW: Float, bodyH: Float) {
        val left = cx - bodyW / 2f
        val top = cy - bodyH / 2f
        val right = cx + bodyW / 2f
        val bottom = cy + bodyH / 2f

        bodyPath.reset()
        bodyPath.addOval(RectF(left, top, right, bottom), Path.Direction.CW)
        canvas.drawPath(bodyPath, body)

        // Belly: a slightly smaller oval pushed toward the bottom.
        val bellyRect = RectF(
            left + bodyW * 0.12f,
            cy - bodyH * 0.06f,
            right - bodyW * 0.16f,
            bottom - bodyH * 0.06f
        )
        canvas.drawOval(bellyRect, belly)
    }

    /** Eyes (with blinking / happy arcs) and blush. */
    private fun drawFace(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        bodyW: Float,
        bodyH: Float,
        w: Float,
        h: Float
    ) {
        val eyeY = cy - bodyH * 0.14f
        val eyeDx = bodyW * 0.17f
        val eyeR = w * 0.030f
        val blinking = blinkFrames > 0

        // Smiling eyes when jumping, round pupils otherwise.
        if (jumpProgress > 0.05f) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                style = Paint.Style.STROKE
                strokeWidth = w * 0.018f
                strokeCap = Paint.Cap.ROUND
            }
            for (sign in intArrayOf(-1, 1)) {
                val ex = cx + sign * eyeDx
                val arc = RectF(ex - eyeR, eyeY - eyeR, ex + eyeR, eyeY + eyeR * 0.6f)
                canvas.drawArc(arc, 200f, 140f, false, stroke)
            }
        } else if (blinking) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                style = Paint.Style.STROKE
                strokeWidth = w * 0.016f
                strokeCap = Paint.Cap.ROUND
            }
            for (sign in intArrayOf(-1, 1)) {
                val ex = cx + sign * eyeDx
                canvas.drawLine(ex - eyeR, eyeY, ex + eyeR, eyeY, stroke)
            }
        } else {
            for (sign in intArrayOf(-1, 1)) {
                val ex = cx + sign * eyeDx
                canvas.drawCircle(ex, eyeY, eyeR, ink)
                // Catchlight makes the pet feel alive.
                val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                canvas.drawCircle(ex - eyeR * 0.32f, eyeY - eyeR * 0.34f, eyeR * 0.30f, glint)
            }
        }

        // Blush
        val blushR = w * 0.040f
        canvas.drawCircle(cx - bodyW * 0.30f, eyeY + bodyH * 0.16f, blushR, blush)
        canvas.drawCircle(cx + bodyW * 0.30f, eyeY + bodyH * 0.16f, blushR, blush)

        // Mouth: a tiny smile, widening while airborne.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            style = Paint.Style.STROKE
            strokeWidth = w * 0.014f
            strokeCap = Paint.Cap.ROUND
        }
        val mouthY = eyeY + bodyH * 0.16f
        val mouthW = w * 0.048f + jumpProgress * w * 0.030f
        val arc = RectF(cx - mouthW, mouthY - mouthW * 0.5f, cx + mouthW, mouthY + mouthW * 0.9f)
        canvas.drawArc(arc, 20f, 140f, false, stroke)
    }

    /** A little water spout drifting up from the blowhole. */
    private fun drawSpout(canvas: Canvas, cx: Float, cy: Float, bodyH: Float) {
        val top = cy - bodyH * 0.50f
        val phase = (tick % 90f) / 90f
        if (phase > 0.6f) return // pause between spouts

        val grow = phase / 0.6f
        val alpha = (1f - grow) * 190f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SPOUT
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = width * 0.012f
            this.alpha = alpha.toInt().coerceIn(0, 255)
        }

        val h = bodyH * 0.34f * grow
        val dx = width * 0.045f

        val path = Path()
        path.moveTo(cx, top)
        path.quadTo(cx - dx * 0.6f, top - h * 0.55f, cx - dx, top - h)
        canvas.drawPath(path, stroke)

        val path2 = Path()
        path2.moveTo(cx, top)
        path2.quadTo(cx + dx * 0.6f, top - h * 0.55f, cx + dx, top - h)
        canvas.drawPath(path2, stroke)
    }

    companion object {
        private val BODY = Color.parseColor("#4D6BFE")
        private val BELLY = Color.parseColor("#DCE4FF")
        private val INK = Color.parseColor("#1B2440")
        private val BLUSH = Color.parseColor("#FF9BB0")
        private val SPOUT = Color.parseColor("#8FB4FF")

        private const val BLINK_PERIOD = 190
        private const val BLINK_LENGTH = 9
    }
}
