package com.dsh.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
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
 * This is an [ImageView] rather than a custom-drawn view, so the overlay's
 * root is the same kind of framework widget as the speech bubble that has
 * always rendered correctly, and the window needs no special flags.
 *
 * Poking her plays a head-pat animation: five frames extracted from the GIF
 * in issue #4, cycled a few times and then released back to the resting view.
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

    /** Head-pat animation frames, in playback order. */
    private val patPaths: List<List<String>> = (0 until PAT_FRAME_COUNT).map { index ->
        listOf("whale_pat/pat_$index.webp", "whale_pat/pat_$index.png")
    }

    private val sprites: List<Bitmap?> = spritePaths.map { candidates ->
        candidates.firstNotNullOfOrNull { decodeAsset(context, it) }
    }

    private val patFrames: List<Bitmap> = patPaths.mapNotNull { candidates ->
        candidates.firstNotNullOfOrNull { decodeAsset(context, it) }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var facing = 0
    private var tickCount = 0f
    private var patFrame = 0
    private var patLoops = 0
    private var restingBitmap: Bitmap? = null

    /** True when at least one sprite decoded. */
    val hasArtwork: Boolean = sprites.any { it != null }

    /** Decode outcome, shown verbatim in the control panel. */
    val report: String = buildString {
        append(
            sprites.mapIndexed { index, bmp ->
                val label = VIEW_LABELS.getOrElse(index) { "视图$index" }
                if (bmp == null) "$label 解码失败" else "$label ${bmp.width}x${bmp.height}"
            }.joinToString(" / ")
        )
        append(" / 摸头 ")
        append(if (patFrames.isEmpty()) "解码失败" else "${patFrames.size}帧")
    }

    /** Drives the head-pat animation while it is playing. */
    private val patTick = object : Runnable {
        override fun run() {
            if (patFrames.isEmpty()) return

            if (patFrame >= patFrames.size) {
                patFrame = 0
                patLoops++
                if (patLoops >= PAT_LOOPS) {
                    // Settle back onto whichever view she was showing.
                    restingBitmap?.let { setImageBitmap(it) }
                    return
                }
            }
            setImageBitmap(patFrames[patFrame])
            patFrame++
            handler.postDelayed(this, PAT_FRAME_MS)
        }
    }

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

    /** Poke reaction: play the head-pat animation. */
    fun playPat() {
        if (patFrames.isEmpty()) {
            // Without frames, fall back to turning so a poke still reacts.
            poke()
            return
        }
        handler.removeCallbacks(patTick)
        restingBitmap = sprites.getOrNull(facing) ?: sprites.firstOrNull { it != null }
        patFrame = 0
        patLoops = 0
        handler.post(patTick)
    }

    /** Advance to the next view without playing the pat animation. */
    fun poke() {
        handler.removeCallbacks(patTick)
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

    /** Stop pending frame callbacks; called when the overlay is torn down. */
    fun release() {
        handler.removeCallbacks(patTick)
    }

    companion object {
        private const val TAG = "WhaleView"

        /** Rendered size of the overlay window, in dp. */
        const val SIZE_DP = 180

        private const val PLACEHOLDER_COLOR = 0x33FF3B30.toInt()

        /** Frames in `assets/whale_pat`. */
        private const val PAT_FRAME_COUNT = 5

        /** The source GIF runs at 50 ms per frame. */
        private const val PAT_FRAME_MS = 50L

        /** Loops per poke; one pass is only 250 ms and reads as a flicker. */
        private const val PAT_LOOPS = 3

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
