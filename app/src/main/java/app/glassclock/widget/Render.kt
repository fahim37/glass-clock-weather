package app.glassclock.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws every picture the widget uses: glass background, clock face, hands and weather icons. */
object Render {

    fun hsl(h: Float, s: Float, l: Float): Int =
        ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, l))

    fun alpha(color: Int, a: Float): Int =
        ColorUtils.setAlphaComponent(color, (a.coerceIn(0f, 1f) * 255f).roundToInt())

    private fun bitmap(w: Int, h: Int): Bitmap = Bitmap.createBitmap(max(w, 2), max(h, 2), Bitmap.Config.ARGB_8888)

    // ------------------------------------------------------------------ background

    /** [dp] is how many bitmap pixels make one design unit. */
    fun background(w: Int, h: Int, dp: Float, s: Settings): Bitmap {
        val bmp = bitmap(w, h)
        val c = Canvas(bmp)
        val fw = bmp.width.toFloat()
        val fh = bmp.height.toFloat()
        val inset = dp
        val r = 28f * dp
        val path = Path().apply { addRoundRect(RectF(inset, inset, fw - inset, fh - inset), r, r, Path.Direction.CW) }
        val base = if (s.dark) Color.rgb(16, 18, 22) else Color.WHITE
        val opacity = 1f - s.transparency / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        when (s.bg) {
            "solid" -> {
                p.color = alpha(base, opacity)
                c.drawPath(path, p)
            }
            "glass" -> {
                p.color = alpha(base, opacity)
                c.drawPath(path, p)
                c.save()
                c.clipPath(path)
                p.shader = RadialGradient(
                    fw * 0.15f, 0f, fw * 0.6f,
                    intArrayOf(alpha(Color.WHITE, 0.12f), alpha(Color.WHITE, 0f)), null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, fw, fh, p)
                c.restore()
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp
                p.color = alpha(Color.WHITE, 0.28f)
                c.drawPath(path, p)
            }
            "liquid" -> {
                // Thin tint, so the wallpaper stays visible through the glass.
                p.color = alpha(base, opacity * 0.5f)
                c.drawPath(path, p)

                c.save()
                c.clipPath(path)
                // Soft light pooling in two corners.
                p.shader = RadialGradient(
                    fw * 0.12f, 0f, fw * 0.6f,
                    intArrayOf(alpha(Color.WHITE, 0.26f), alpha(Color.WHITE, 0f)), null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, fw, fh, p)
                p.shader = RadialGradient(
                    fw * 0.95f, fh * 1.05f, fw * 0.45f,
                    intArrayOf(alpha(Color.WHITE, 0.14f), alpha(Color.WHITE, 0f)), null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, fw, fh, p)
                // Thick blurred edge: bright on the top-left, shaded on the bottom-right.
                p.style = Paint.Style.STROKE
                p.strokeWidth = 14f * dp
                p.maskFilter = BlurMaskFilter(8f * dp, BlurMaskFilter.Blur.NORMAL)
                p.shader = LinearGradient(
                    0f, 0f, fw, fh,
                    intArrayOf(alpha(Color.WHITE, 0.38f), alpha(Color.WHITE, 0.08f), alpha(Color.BLACK, 0.22f)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                c.drawPath(path, p)
                c.restore()

                // Crisp bright rim.
                p.maskFilter = null
                p.strokeWidth = 1.5f * dp
                p.shader = LinearGradient(
                    0f, 0f, 0f, fh,
                    intArrayOf(alpha(Color.WHITE, 0.85f), alpha(Color.WHITE, 0.18f), alpha(Color.WHITE, 0.42f)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                c.drawPath(path, p)
            }
        }
        return bmp
    }

    // ------------------------------------------------------------------ clock

    fun dial(size: Int, s: Settings): Bitmap {
        val bmp = bitmap(size, size)
        val c = Canvas(bmp)
        val u = bmp.width / 200f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = hsl(s.hue.toFloat(), 0.8f, 0.86f)
        when (s.shape) {
            "circle" -> c.drawCircle(100f * u, 100f * u, 94f * u, p)
            "squircle" -> c.drawRoundRect(RectF(8f * u, 8f * u, 192f * u, 192f * u), 58f * u, 58f * u, p)
            else -> {
                val path = Path()
                val n = 240
                for (i in 0 until n) {
                    val a = i.toDouble() / n * Math.PI * 2.0
                    val r = (89.0 + 5.5 * cos(12.0 * a)) * u
                    val x = (100.0 * u + r * sin(a)).toFloat()
                    val y = (100.0 * u - r * cos(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, p)
            }
        }
        return bmp
    }

    /** Hands are drawn pointing at 12 on a canvas the same size as the dial. */
    fun hourHand(size: Int, s: Settings): Bitmap = hand(size, 56f, 13f, hsl(s.hue.toFloat(), 0.55f, 0.20f))

    fun minuteHand(size: Int, s: Settings): Bitmap = hand(size, 32f, 8f, hsl(s.hue.toFloat(), 0.55f, 0.40f))

    private fun hand(size: Int, tipY: Float, width: Float, color: Int): Bitmap {
        val bmp = bitmap(size, size)
        val u = bmp.width / 200f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width * u
        Canvas(bmp).drawLine(100f * u, 100f * u, 100f * u, tipY * u, p)
        return bmp
    }

    fun secondHand(size: Int, s: Settings): Bitmap {
        val bmp = bitmap(size, size)
        val u = bmp.width / 200f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = hsl(s.hue + 150f, 0.75f, 0.42f)
        Canvas(bmp).drawCircle(100f * u, 24f * u, 5.5f * u, p)
        return bmp
    }

    // ------------------------------------------------------------------ weather icons

    private const val SUN = 0xFFFFC233.toInt()
    private const val MOON = 0xFFFFD772.toInt()
    private const val RAIN = 0xFF4FA8FF.toInt()

    fun weatherIcon(kind: String, isDay: Boolean, size: Int, cloud: Int): Bitmap {
        val bmp = bitmap(size, size)
        val c = Canvas(bmp)
        val u = bmp.width / 64f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (kind) {
            "clear" -> if (isDay) drawSun(c, p, u, 32f, 32f, 12f, 20f, 26f, 4f) else drawMoon(c, p, u)
            "partly" -> {
                if (isDay) drawSun(c, p, u, 44f, 22f, 9f, 14f, 18f, 3.5f) else drawMoon(c, p, u)
                drawCloud(c, p, u, 0f, 4f, cloud)
            }
            "rain" -> {
                drawCloud(c, p, u, 0f, -8f, cloud)
                p.color = RAIN
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 3.5f * u
                for (x in floatArrayOf(22f, 33f, 44f)) c.drawLine(x * u, 47f * u, (x - 2f) * u, 54f * u, p)
            }
            "storm" -> {
                drawCloud(c, p, u, 0f, -8f, cloud)
                p.color = SUN
                p.style = Paint.Style.FILL
                val bolt = Path()
                val pts = floatArrayOf(34f, 44f, 27f, 54f, 32f, 54f, 29f, 62f, 40f, 50f, 34f, 50f, 38f, 44f)
                bolt.moveTo(pts[0] * u, pts[1] * u)
                var i = 2
                while (i < pts.size) {
                    bolt.lineTo(pts[i] * u, pts[i + 1] * u)
                    i += 2
                }
                bolt.close()
                c.drawPath(bolt, p)
            }
            else -> drawCloud(c, p, u, 0f, 0f, cloud)
        }
        return bmp
    }

    private fun drawSun(c: Canvas, p: Paint, u: Float, cx: Float, cy: Float, r: Float, ray0: Float, ray1: Float, stroke: Float) {
        p.color = SUN
        p.style = Paint.Style.FILL
        c.drawCircle(cx * u, cy * u, r * u, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = stroke * u
        for (i in 0 until 8) {
            val a = i * Math.PI / 4.0
            val dx = sin(a).toFloat()
            val dy = -cos(a).toFloat()
            c.drawLine((cx + dx * ray0) * u, (cy + dy * ray0) * u, (cx + dx * ray1) * u, (cy + dy * ray1) * u, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawMoon(c: Canvas, p: Paint, u: Float) {
        p.color = MOON
        p.style = Paint.Style.FILL
        c.drawCircle(30f * u, 32f * u, 18f * u, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c.drawCircle(40f * u, 25f * u, 15f * u, p)
        p.xfermode = null
    }

    private fun drawCloud(c: Canvas, p: Paint, u: Float, dx: Float, dy: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawCircle((22f + dx) * u, (40f + dy) * u, 10f * u, p)
        c.drawCircle((33f + dx) * u, (31f + dy) * u, 13f * u, p)
        c.drawCircle((46f + dx) * u, (41f + dy) * u, 9f * u, p)
        c.drawRect((22f + dx) * u, (36f + dy) * u, (46f + dx) * u, (50f + dy) * u, p)
    }

    fun drop(size: Int): Bitmap {
        val bmp = bitmap(size, size)
        val u = bmp.width / 24f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = RAIN
        val path = Path()
        path.moveTo(12f * u, 3f * u)
        path.cubicTo(16f * u, 8f * u, 18.5f * u, 11.2f * u, 18.5f * u, 14.5f * u)
        path.arcTo(RectF(5.5f * u, 8f * u, 18.5f * u, 21f * u), 0f, 180f, false)
        path.cubicTo(5.5f * u, 11.2f * u, 8f * u, 8f * u, 12f * u, 3f * u)
        path.close()
        Canvas(bmp).drawPath(path, p)
        return bmp
    }
}
