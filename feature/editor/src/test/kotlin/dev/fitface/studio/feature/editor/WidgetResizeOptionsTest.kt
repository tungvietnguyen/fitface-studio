package dev.fitface.studio.feature.editor

import dev.fitface.studio.core.model.WidgetGuide
import dev.fitface.studio.core.model.WidgetResizeKind
import dev.fitface.studio.core.model.WidgetRotationKind
import dev.fitface.studio.core.model.rotationBounds
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetResizeOptionsTest {
    @Test
    fun rotatedWidgetRungsStayInsideThePanel() {
        val widget = WidgetGuide(
            ordinal = 0,
            globalIndex = 0,
            type = 1,
            sequenceId = 0,
            x = 0,
            y = 0,
            width = 200,
            height = 200,
            originalWidth = 200,
            originalHeight = 200,
            recordSize = 40,
            isFinal = false,
            canEditPosition = true,
            resizeKind = WidgetResizeKind.FIELDS,
            colorArgb = null,
            rotationKind = WidgetRotationKind.TEXT,
            rotationTenths = 450,
            supportMessage = "",
        )

        val options = widgetResizeOptions(widget, panelWidth = 256, panelHeight = 402)

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { size ->
            val bounds = rotationBounds(
                size.width,
                size.height,
                size.width / 2,
                size.height / 2,
                45.0,
            )
            bounds.width <= 256 && bounds.height <= 402
        })
        assertTrue(options.none { it.percentOfOriginal >= 95 })
        assertTrue(nextWidgetSizeOnFace(widget, grow = false, panelWidth = 256, panelHeight = 402) != null)
        assertNull(nextWidgetSizeOnFace(widget, grow = true, panelWidth = 256, panelHeight = 402))
    }
}
