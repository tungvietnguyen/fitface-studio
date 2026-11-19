package dev.fitface.studio.core.data

import android.graphics.Color
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FontSpriteRendererTest {
    @Test
    fun glyphsUseEqualTransparentCellsAndRespectTheRequestedSize() {
        val small = FontSpriteRenderer.render(Typeface.create("sans-serif", Typeface.NORMAL), 14, 0xFFFFFFFF.toInt(), 0f)
        val large = FontSpriteRenderer.render(Typeface.create("sans-serif", Typeface.NORMAL), 40, 0xFFFFFFFF.toInt(), 3f)

        assertEquals(66, FontSpriteRenderer.symbols.size)
        assertEquals(8, small.columns)
        assertEquals(9, small.rows)
        assertEquals(small.columns * small.cellWidth, small.bitmap.width)
        assertEquals(small.rows * small.cellHeight, small.bitmap.height)
        assertTrue(small.cellWidth < large.cellWidth)
        assertTrue(small.cellHeight < large.cellHeight)
        assertEquals(0, small.bitmap.getPixel(0, 0) ushr 24)
        assertTrue(
            (0 until large.bitmap.height).any { y ->
                (0 until large.bitmap.width).any { x -> large.bitmap.getPixel(x, y) == Color.BLACK }
            },
        )
        repeat(FontSpriteRenderer.symbols.size) { index ->
            val left = index % small.columns * small.cellWidth
            val top = index / small.columns * small.cellHeight
            var hasInk = false
            for (y in top until top + small.cellHeight) {
                for (x in left until left + small.cellWidth) {
                    if (small.bitmap.getPixel(x, y) ushr 24 != 0) hasInk = true
                }
            }
            assertTrue("glyph ${FontSpriteRenderer.symbols[index]} should render", hasInk)
        }
        small.bitmap.recycle()
        large.bitmap.recycle()
    }

    @Test
    fun outlineColorAndCharacterSpacingAreApplied() {
        val compact = FontSpriteRenderer.render(
            Typeface.DEFAULT,
            12,
            Color.WHITE,
            2f,
            outlineColor = Color.RED,
        )
        val spaced = FontSpriteRenderer.render(
            Typeface.DEFAULT,
            12,
            Color.WHITE,
            2f,
            outlineColor = Color.RED,
            characterSpacingPx = 8,
        )

        assertTrue(spaced.cellWidth > compact.cellWidth)
        assertTrue(
            (0 until compact.bitmap.height).any { y ->
                (0 until compact.bitmap.width).any { x -> compact.bitmap.getPixel(x, y) == Color.RED }
            },
        )
        compact.bitmap.recycle()
        spaced.bitmap.recycle()
    }
}
