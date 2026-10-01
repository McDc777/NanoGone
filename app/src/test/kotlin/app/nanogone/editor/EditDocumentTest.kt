package app.nanogone.editor

import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.mask.Mask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditDocumentTest {

    @Test
    fun brushThenEraseLeavesOnlyTheUnerasedPart() {
        val doc = EditDocument(100, 100)
        doc.addShape(BrushStroke(floatArrayOf(10f, 60f), floatArrayOf(50f, 50f), 5f, erase = false))
        doc.addShape(BrushStroke(floatArrayOf(35f), floatArrayOf(50f), 8f, erase = true))
        val m = doc.selectionMask(IntRect(0, 0, 100, 100))
        assertTrue(m[12, 50])
        assertTrue(m[58, 50])
        assertFalse(m[35, 50])
    }

    @Test
    fun loopSelectsItsInside() {
        val doc = EditDocument(50, 50)
        doc.addShape(Loop(floatArrayOf(10f, 30f, 30f, 10f), floatArrayOf(10f, 10f, 30f, 30f)))
        assertEquals(IntRect(10, 10, 31, 31), doc.selectionBounds())
        val m = doc.selectionMask(IntRect(0, 0, 50, 50))
        assertEquals(400, m.count())
    }

    @Test
    fun undoAndRedoWalkTheHistory() {
        val doc = EditDocument(20, 20)
        assertFalse(doc.canUndo)
        doc.addShape(Spot(5f, 5f, 2f))
        doc.addPatch(Patch(IntRect(0, 0, 4, 4), IntArray(16), Mask(4, 4)))
        assertTrue(doc.state.selection.isEmpty())
        assertEquals(1, doc.state.patches.size)
        doc.undo()
        assertEquals(1, doc.state.selection.size)
        assertEquals(0, doc.state.patches.size)
        doc.redo()
        assertEquals(1, doc.state.patches.size)
        doc.undo(); doc.undo()
        assertNull(doc.selectionBounds())
        doc.addShape(Spot(1f, 1f, 1f))
        assertFalse(doc.canRedo)
    }

    @Test
    fun boundsAreClippedToThePhoto() {
        val doc = EditDocument(30, 30)
        doc.addShape(Spot(0f, 0f, 10f))
        assertEquals(IntRect(0, 0, 11, 11), doc.selectionBounds())
    }
}
