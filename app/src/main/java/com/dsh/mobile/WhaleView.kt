package com.dsh.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.Gravity
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.InputStream
import kotlin.math.sin

/**
 * Whale-chan as an overlay sprite, built from a plain [ImageView].
 *
 * An ImageView is a framework widget: it scales and draws its drawable the
 * same way the TextView and LinearLayout widgets in this service do. The
 * previous custom onDraw path depended on canvas maths lining up with the
 * bitmap's measured size, and when that assumption broke the overlay painted
 * nothing — with no way to tell why from a screenshot.
 *
 * Animation is a translation on the view itself rather than canvas scaling,
 * which is the mechanism the framework is built around.
 */
class WhaleView(context: Context) : FrameLayout(context) {

    private val image = ImageView(context)

    /** Candidate asset paths per view, most-preferred first. */
    private val spriteNames = listOf(
        listOf("whale/whale_front.webp", "whale/whale_front.png"),
        listOf("whale/whale_side.webp", "whale/whale_side.png"),
        listOf("whale/whale_back.webp", "whale/whale_back.png")
    )

    /** Decoded sprites; entries are null when that asset could not be read. */
    private val sprites: List<Bitmap?> = spriteNames.map { candidates ->
        candidates.firstNotNullOfOrNull { decodeAsset(context, it) }
    }

    private var facing = 0
    private var tickCount = 0f

    /** True when at least the front sprite decoded. */
    val hasArtwork: Boolean = sprites.any { it != null }

    init {
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(
            image,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )

        val first = sprites.getOrNull(0) ?: sprites.firstOrNull { it != null }
        if (first != null) {
            image.setImageBitmap(first)
        } else {
            Log.e(TAG, "no whale sprite decoded; the overlay will be empty")
        }
    }

    /** Idle bob: a slow vertical drift applied as a view translation. */
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
        if (next != null) image.setImageBitmap(next)
    }

    /** A single hop, expressed as a translation the framework runs for us. */
    fun jump() {
        val h = height.toFloat().coerceAtLeast(1f)
        val hop = TranslateAnimation(0f, 0f, 0f, -h * 0.20f).apply {
            duration = 260
            repeatMode = Animation.REVERSE
            repeatCount = 1
            interpolator = LinearInterpolator()
        }
        startAnimation(hop)
    }

    companion object {
        private const val TAG = "WhaleView"

        /** Rendered size of the overlay window, in dp. */
        const val SIZE_DP = 180

        /**
         * Decode one asset, never throwing. Returns null when the asset is
         * missing or unreadable.
         */
        private fun decodeAsset(context: Context, name: String): Bitmap? = try {
            val stream: InputStream = context.assets.open(name)
            stream.use { input ->
                BitmapFactory.decodeStream(input)?.also { bmp ->
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
