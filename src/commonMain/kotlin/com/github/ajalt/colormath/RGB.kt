package com.github.ajalt.colormath

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A color in the sRGB color space, which uses the D65 illuminant.
 *
 * All color channels are floating point values normalized to `[0, 1]` for SDR colors. HDR colors may exceed this range.
 *
 * @property r The red channel, a value in the range `[0, 1]`
 * @property g The green channel, a value in the range `[0, 1]`
 * @property b The blue channel, a value in the range `[0, 1]`
 * @property a The alpha channel, a value in the range `[0, 1]`
 */
data class RGB(val r: Float, val g: Float, val b: Float, val a: Float = 1f) : Color {
    /**
     * Construct an RGB instance from a hex string with optional alpha channel.
     *
     * [hex] may optionally start with a `#`. The remaining characters should be one of the following forms:
     *
     * - `ddeeff`: The RGB values specified in pairs of hex digits
     * - `ddeeffaa`: Like the 6 digit form, but with an extra pair of hex digits for specifying the alpha channel
     * - `def`: A shorter version of the 6 digit form. Each digit is repeated, so `def` is equivalent to `ddeeff`
     * - `defa`: A shorter version of the 8 digit for.Each digit is repeated, so `defa` is equivalent to `ddeeffaa`
     */
    constructor(hex: String) : this(
        r = hex.validateHex().parseHex(0),
        g = hex.parseHex(1),
        b = hex.parseHex(2),
        a = if (hex.hexLength.let { it == 4 || it == 8 }) hex.parseHex(3) / 255f else 1f
    )

    @Deprecated("The Byte constructor is deprecated", ReplaceWith("RGB((r + 128), (g + 128), (b + 128))"))
    constructor(r: Byte, g: Byte, b: Byte) : this(r + 128, g + 128, b + 128)

    // A UByte constructor can't be declared since it clashes with the Byte constructor on JVM
    //  constructor(r: UByte, g: UByte, b: UByte) : this(r.toInt(), g.toInt(), b.toInt())

    /**
     * Construct an RGB instance from Int values in the range `[0, 255]`.
     *
     * @property r The red channel, a value typically in the range `[0, 255]`
     * @property g The green channel, a value typically in the range `[0, 255]`
     * @property b The blue channel, a value typically in the range `[0, 255]`
     * @property a The alpha channel, a value in the range `[0f, 1f]`
     */
    constructor(r: Int, g: Int, b: Int, a: Float = 1f) : this(
        r = (r / 255f),
        g = (g / 255f),
        b = (b / 255f),
        a = a
    )

    /**
     * Construct an RGB instance from Double values in the range `[0, 1]`.
     */
    constructor(r: Double, g: Double, b: Double, a: Double = 1.0) : this(
        r = r.toFloat(),
        g = g.toFloat(),
        b = b.toFloat(),
        a = a.toFloat()
    )

    override val alpha: Float get() = a

    /** The red channel scaled to [0, 255]. HDR colors may exceed this range. */
    val redInt: Int get() = (r * 255).roundToInt()

    /** The green channel scaled to [0, 255]. HDR colors may exceed this range. */
    val greenInt: Int get() = (g * 255).roundToInt()

    /** The blue channel scaled to [0, 255]. HDR colors may exceed this range. */
    val blueInt: Int get() = (b * 255).roundToInt()

    /** The alpha channel scaled to [0, 255]. */
    val alphaInt: Int get() = (a * 255).roundToInt()

    @Deprecated("use toRGBInt instead", ReplaceWith("toRGBInt()"))
    fun toPackedInt(): Int = toRGBInt().argb.toInt()

    /**
     * Return this color as a packed ARGB integer.
     *
     * The color will be clamped to the SDR range `[0, 255]`.
     */
    fun toRGBInt() = RGBInt(
        a = alphaInt.coerceIn(0, 255).toUByte(),
        r = redInt.coerceIn(0, 255).toUByte(),
        g = greenInt.coerceIn(0, 255).toUByte(),
        b = blueInt.coerceIn(0, 255).toUByte()
    )

    override fun toHex(withNumberSign: Boolean, renderAlpha: RenderCondition): String {
        return toRGBInt().toHex(withNumberSign, renderAlpha)
    }

    override fun toHSL(): HSL {
        return hueMinMaxDelta { h, min, max, delta ->
            val l = (min + max) / 2
            val s = when {
                max == min -> 0.0
                l <= .5 -> delta / (max + min)
                else -> delta / (2 - max - min)
            }
            HSL(h.roundToInt(), (s * 100.0).roundToInt(), (l * 100.0).roundToInt(), alpha)
        }
    }

    override fun toHSV(): HSV {
        return hueMinMaxDelta { h, _, max, delta ->
            val s = when (max) {
                0.0 -> 0.0
                else -> (delta / max)
            }
            HSV(h.roundToInt(), (s * 100.0).roundToInt(), (max * 100.0).roundToInt(), alpha)
        }
    }

    override fun toXYZ(): XYZ {
        // linearize sRGB
        fun adj(c: Float): Double {
            return when {
                c > 0.04045 -> ((c + 0.055) / 1.055).pow(2.4)
                else -> c / 12.92
            }
        }

        val rL = adj(r)
        val gL = adj(g)
        val bL = adj(b)

        // Matrix from http://www.brucelindbloom.com/index.html?Eqn_RGB_XYZ_Matrix.html
        val x = 0.4124564 * rL + 0.3575761 * gL + 0.1804375 * bL
        val y = 0.2126729 * rL + 0.7151522 * gL + 0.0721750 * bL
        val z = 0.0193339 * rL + 0.1191920 * gL + 0.9503041 * bL
        return XYZ(x * 100.0, y * 100.0, z * 100.0, alpha)
    }

    override fun toLAB(): LAB = toXYZ().toLAB()

    override fun toLUV(): LUV = toXYZ().toLUV()

    override fun toLCH(): LCH = toXYZ().toLUV().toLCH()

    override fun toCMYK(): CMYK {
        val k = 1 - maxOf(r, b, g)
        val c = if (k == 1f) 0f else (1 - r - k) / (1 - k)
        val m = if (k == 1f) 0f else (1 - g - k) / (1 - k)
        val y = if (k == 1f) 0f else (1 - b - k) / (1 - k)
        return CMYK(
            (c * 100f).roundToInt(),
            (m * 100f).roundToInt(),
            (y * 100f).roundToInt(),
            (k * 100f).roundToInt(),
            alpha
        )
    }

    override fun toHWB(): HWB {
        // https://www.w3.org/TR/css-color-4/#rgb-to-hwb
        return hueMinMaxDelta { hue, min, max, _ ->
            HWB(
                h = hue,
                w = 100.0 * min,
                b = 100.0 * (1.0 - max),
                alpha = alpha.toDouble()
            )
        }
    }

    override fun toAnsi16(): Ansi16 {
        val value = (toHSV().v * 100).roundToInt()
        if (value == 30) return Ansi16(30)
        val v = value / 50

        val ansi = 30 + ((b.roundToInt() * 4) or (g.roundToInt() * 2) or r.roundToInt())
        return Ansi16(if (v == 2) ansi + 60 else ansi)
    }

    override fun toAnsi256(): Ansi256 {
        val ri = redInt
        val gi = greenInt
        val bi = blueInt
        // grayscale
        val code = if (ri == gi && gi == bi) {
            when {
                ri < 8 -> 16
                ri > 248 -> 231
                else -> (((ri - 8) / 247.0) * 24.0).roundToInt() + 232
            }
        } else {
            16 + (36 * (r * 5).roundToInt()) +
                    (6 * (g * 5).roundToInt()) +
                    (b * 5).roundToInt()
        }
        return Ansi256(code)
    }

    override fun toRGB() = this

    /**
     * Call [block] with the hue, min of color channels, max of color channels, and the
     * delta between min and max.
     *
     * Min and max are scaled to [0, 1]
     */
    private inline fun <T> hueMinMaxDelta(block: (hue: Double, min: Double, max: Double, delta: Double) -> T): T {
        val r = this.r.toDouble()
        val g = this.g.toDouble()
        val b = this.b.toDouble()
        val min = minOf(r, g, b)
        val max = maxOf(r, g, b)
        val delta = max - min

        var h = when {
            max == min -> 0.0
            r == max -> (g - b) / delta
            g == max -> 2 + (b - r) / delta
            b == max -> 4 + (r - g) / delta
            else -> 0.0
        }

        h = minOf(h * 60, 360.0)
        if (h < 0) h += 360

        return block(h, min, max, delta)
    }

    companion object {
        @Deprecated("Use RGBInt instead", ReplaceWith("RGBInt(argb.toUInt())"))
        fun fromInt(argb: Int): RGB = RGBInt(argb.toUInt()).toRGB()
    }
}


private fun String.validateHex() = apply {
    require(hexLength.let { it == 3 || it == 4 || it == 6 || it == 8 }) {
        "Hex string must be in the format \"#ffffff\" or \"ffffff\""
    }
}

private fun String.parseHex(startIndex: Int): Int {
    return if (hexLength > 4) {
        val i = if (this[0] == '#') startIndex * 2 + 1 else startIndex * 2
        slice(i..i + 1).toInt(16)
    } else {
        val i = if (this[0] == '#') startIndex + 1 else startIndex
        get(i).let { "$it$it" }.toInt(16)
    }
}

private val String.hexLength get() = if (startsWith("#")) length - 1 else length