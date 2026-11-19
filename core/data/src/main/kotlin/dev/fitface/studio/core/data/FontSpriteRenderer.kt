package dev.fitface.studio.core.data

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

enum class BundledSpriteFont(val label: String, val assetPath: String) {
    ORBITRON("Orbitron", "fonts/Orbitron.ttf"),
    BEBAS_NEUE("Bebas Neue", "fonts/BebasNeue.ttf"),
    JETBRAINS_MONO("JetBrains Mono", "fonts/JetBrainsMono.ttf"),
    OSWALD("Oswald", "fonts/Oswald.ttf"),
    RAJDHANI("Rajdhani", "fonts/Rajdhani.ttf"),
}

data class FontSpriteSheet(
    val bitmap: Bitmap,
    val columns: Int,
    val rows: Int,
    val cellWidth: Int,
    val cellHeight: Int,
)

/** Builds static glyph artwork; it does not change the watch's firmware-rendered live text. */
object FontSpriteRenderer {
    val symbols: List<String> = buildList {
        addAll(('0'..'9').map { it.toString() })
        addAll(listOf(":", "/", "-", "%"))
        addAll(('A'..'Z').map { it.toString() })
        addAll(('a'..'z').map { it.toString() })
    }

    fun bundledTypeface(assets: AssetManager, font: BundledSpriteFont): Typeface =
        Typeface.createFromAsset(assets, font.assetPath)

    fun fileTypeface(path: String): Typeface = Typeface.createFromFile(path)

    fun render(
        typeface: Typeface,
        sizePx: Int,
        fillColor: Int,
        outlineThicknessPx: Float,
        outlineColor: Int? = null,
        characterSpacingPx: Int = 0,
    ): FontSpriteSheet {
        require(sizePx in MIN_FONT_SIZE_PX..MAX_FONT_SIZE_PX) {
            "Font size must be between $MIN_FONT_SIZE_PX and $MAX_FONT_SIZE_PX pixels."
        }
        require(outlineThicknessPx.isFinite() && outlineThicknessPx in 0f..MAX_OUTLINE_PX) {
            "Outline thickness must be between 0 and $MAX_OUTLINE_PX pixels."
        }
        require(characterSpacingPx in 0..MAX_CHARACTER_SPACING_PX) {
            "Character spacing must be between 0 and $MAX_CHARACTER_SPACING_PX pixels."
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            textSize = sizePx.toFloat()
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        val metrics = fill.fontMetrics
        val padding = ceil(outlineThicknessPx / 2f).toInt() + CELL_PADDING_PX
        val cellWidth = ceil(symbols.maxOf { fill.measureText(it) }).toInt() +
            padding * 2 + characterSpacingPx
        val cellHeight = ceil(metrics.bottom - metrics.top).toInt() + padding * 2
        val columns = minOf(GRID_COLUMNS, symbols.size)
        val rows = (symbols.size + columns - 1) / columns
        val bitmap = Bitmap.createBitmap(columns * cellWidth, rows * cellHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = Paint(fill).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = outlineThicknessPx
            color = outlineColor ?: if (relativeLuminance(fillColor) > 0.5) {
                0xFF000000.toInt()
            } else {
                0xFFFFFFFF.toInt()
            }
        }
        symbols.forEachIndexed { index, symbol ->
            val column = index % columns
            val row = index / columns
            val x = column * cellWidth + cellWidth / 2f
            val baseline = row * cellHeight + padding - metrics.top
            if (outlineThicknessPx > 0f) canvas.drawText(symbol, x, baseline, outline)
            canvas.drawText(symbol, x, baseline, fill)
        }
        return FontSpriteSheet(bitmap, columns, rows, cellWidth, cellHeight)
    }

    private fun relativeLuminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color ushr 16 and 0xFF) +
            0.7152 * linear(color ushr 8 and 0xFF) +
            0.0722 * linear(color and 0xFF)
    }

    const val MIN_FONT_SIZE_PX = 12
    const val MAX_FONT_SIZE_PX = 150
    const val MAX_OUTLINE_PX = 8f
    const val MAX_CHARACTER_SPACING_PX = 24
    const val GRID_COLUMNS = 8
    private const val CELL_PADDING_PX = 3
}
