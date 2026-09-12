package com.dsh.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import java.io.InputStream
import kotlin.math.sin

/**
 * Whale-chan as an overlay sprite.
 *
 * This is an [ImageView] rather than a custom-drawn view or a FrameLayout
 * wrapper, so the overlay's root is the same kind of framework widget as the
 * speech bubble that has always rendered correctly. It also means the window
 * needs no special flags: the pet window and the bubble window are now
 * configured identically apart from size and position.
 *
 * [report] carries the decode outcome so the control panel can show it. A
 * blank overlay with no explanation is impossible to diagnose from a
 * screenshot, which is exactly how earlier attempts stalled.
 */
class WhaleView(context: Context) : ImageView(context) {

    /**
     * Candidate asset paths per view, most-preferred first.
     *
     * Lossless WebP is tried first because the resource pipeline leaves it
     * alone; the PNG copy is a fallback in case a device cannot decode WebP.
     */
    private val spritePaths = listOf(
        listOf("whale/whale_front.webp", "whale/whale_front.png"),
        listOf("whale/whale_side.webp", "whale/whale_side.png"),
        listOf("whale/whale_back.webp", "whale/whale_back.png")
    )

    private val sprites: List<Bitmap?> = spritePaths.map { candidates ->
        candidates.firstNotNullOfOrNull { decodeAsset(context, it) }
    }

    private var facing = 0
    private var tickCount = 0f

    /** True when at least one sprite decoded. */
    val hasArtwork: Boolean = sprites.any { it != null }

    /** Decode outcome, shown verbatim in the control panel. */
    val report: String = sprites.mapIndexed { index, bmp ->
        val label = VIEW_LABELS.getOrElse(index) { "视图$index" }
        if (bmp == null) "$label 解码失败" else "$label ${bmp.width}x${bmp.height}"
    }.joinToString(" / ")

    init {
        scaleType = ScaleType.FIT_CENTER

        val first = sprites.getOrNull(0) ?: sprites.firstOrNull { it != null }
        if (first != null) {
            setImageBitmap(first)
        } else {
            // Fail loudly: a tinted box proves the window itself is on screen,
            // which separates "no artwork" from "no window".
            setBackgroundColor(PLACEHOLDER_COLOR)
            Log.e(TAG, "no whale sprite decoded; showing placeholder")
        }
    }

    /** Idle bob: a slow vertical drift, applied as a view translation. */
    fun tick() {
        tickCount += 1f
        val h = height.toFloat()
        if (h <= 0f) return
        translationY = sin(tickCount / 24f) * h * 0.018f
    }

    /** Poke reaction: advance to the next view. */
    fun poke() {
        facing = (facing + 1) % sprites.size
        val next = sprites.getOrNull(facing) ?: sprites.firstOrNull { it != null }
        if (next != null) setImageBitmap(next)
    }

    /** A single hop, run by the framework as a view animation. */
    fun jump() {
        val h = height.toFloat().coerceAtLeast(1f)
        startAnimation(
            TranslateAnimation(0f, 0f, 0f, -h * 0.20f).apply {
                duration = 260
                repeatMode = Animation.REVERSE
                repeatCount = 1
                interpolator = LinearInterpolator()
            }
        )
    }

    companion object {
        private const val TAG = "WhaleView"

        /** Rendered size of the overlay window, in dp. */
        const val SIZE_DP = 180

        private const val PLACEHOLDER_COLOR = 0x33FF3B30.toInt()

        private val VIEW_LABELS = listOf("正面", "侧面", "背面")

        /**
         * Decode one asset, never throwing. Returns null when the asset is
         * missing or unreadable.
         */
        private fun decodeAsset(context: Context, name: String): Bitmap? = try {
            context.assets.open(name).use { input: InputStream ->
                BitmapFactory.decodeStream(input)?.also { bmp ->
                    Log.d(TAG, "decoded $name -> ${bmp.width}x${bmp.height}")
                } ?: run {
                    Log.w(TAG, "decodeStream returned null for $name")
                    null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "decode threw for $name", t)
            null
        }
    }
}
