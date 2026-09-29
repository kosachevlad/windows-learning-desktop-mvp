package ua.school.windowsdesktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaintSelectionTest {
    private val bounds = Rect(100f, 100f, 300f, 200f)

    @Test fun cursorDoesNotResizeOutsideSelection() {
        listOf(Offset(0f, 150f), Offset(200f, 0f), Offset(400f, 150f), Offset(200f, 300f))
            .forEach { assertEquals(-1, selectionModeForPoint(it, bounds)) }
    }

    @Test fun centerEdgesAndCornersHaveDistinctHitZones() {
        val points = listOf(
            Offset(200f, 150f) to 0,
            Offset(100f, 150f) to 1, Offset(300f, 150f) to 2,
            Offset(200f, 100f) to 4, Offset(200f, 200f) to 8,
            bounds.topLeft to 5, bounds.topRight to 6,
            bounds.bottomLeft to 9, bounds.bottomRight to 10,
        )
        points.forEach { (point, mode) -> assertEquals(mode, selectionModeForPoint(point, bounds)) }
    }

    @Test fun thinLineStillHasMoveZoneAndNoConflictingEdges() {
        val thin = Rect(100f, 100f, 300f, 108f)
        assertEquals(0, selectionModeForPoint(thin.center, thin))
        assertEquals(4, selectionModeForPoint(Offset(200f, 100f), thin))
        assertEquals(8, selectionModeForPoint(Offset(200f, 108f), thin))
    }

    @Test fun draggingNearEdgeDoesNotJumpToCursor() {
        assertEquals(Rect(110f, 100f, 300f, 200f), transformedBounds(bounds, Offset(10f, 0f), 1))
        assertEquals(bounds, transformedBounds(bounds, Offset.Zero, 1))
        assertEquals(Rect(296f, 100f, 300f, 200f), transformedBounds(bounds, Offset(500f, 0f), 1))
    }

    @Test fun textResizesFromOriginalWithoutCompoundingAndMovesWithItsFrame() {
        val text = PaintAction(PaintTool.TEXT, listOf(Offset(120f, 170f)), 0, 7f, "Text", editBounds = bounds)
        val larger = transformAction(text, bounds, Rect(100f, 100f, 500f, 400f))
        assertEquals(2f, larger.scaleX, 0.001f)
        assertEquals(3f, larger.scaleY, 0.001f)
        assertEquals(Offset(140f, 310f), larger.points.single())
        val moved = transformAction(larger, larger.editBounds!!, larger.editBounds.translate(Offset(20f, -10f)))
        assertEquals(Offset(160f, 300f), moved.points.single())
        assertEquals(larger.scaleX, moved.scaleX, 0.001f)
        assertEquals(larger.scaleY, moved.scaleY, 0.001f)
        assertEquals(bounds, actionBounds(transformAction(text, bounds, bounds)))
    }

    @Test fun textBoundsUseGlyphMetricsInsteadOfCharacterCount() {
        val narrow = PaintAction(PaintTool.TEXT, listOf(Offset(100f, 100f)), 0, 10f, "iiii")
        val wide = narrow.copy(text = "WWWW")
        assertTrue(actionBounds(wide)!!.width > actionBounds(narrow)!!.width)
    }
}
