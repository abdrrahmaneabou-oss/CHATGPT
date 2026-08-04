package app.aimode.studio.domain

import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicPlannerTest {
    @Test
    fun `every supported image count fills one valid non-overlapping frame`() {
        for (count in 1..5) {
            val images = List(count) { index ->
                MosaicImage("image-$index", listOf(0.67f, 1f, 1.78f)[index % 3])
            }
            val plan = MosaicPlanner.plan(images)

            assertEquals(images.map { it.id }.toSet(), plan.cells.map { it.imageId }.toSet())
            assertEquals(count, plan.cells.size)
            plan.cells.forEach { cell ->
                assertTrue(cell.left >= 0f)
                assertTrue(cell.top >= 0f)
                assertTrue(cell.right <= 1f)
                assertTrue(cell.bottom <= 1f)
                assertTrue(cell.width > 0f)
                assertTrue(cell.height > 0f)
            }
            plan.cells.forEachIndexed { firstIndex, first ->
                plan.cells.drop(firstIndex + 1).forEach { second ->
                    val overlapWidth = min(first.right, second.right) - max(first.left, second.left)
                    val overlapHeight = min(first.bottom, second.bottom) - max(first.top, second.top)
                    assertTrue(overlapWidth <= 0f || overlapHeight <= 0f)
                }
            }
            val usedArea = plan.cells.sumOf { (it.width * it.height).toDouble() }
            assertTrue("count=$count area=$usedArea", usedArea >= 0.95)
        }
    }

    @Test
    fun `two portrait images become a wide side-by-side mosaic`() {
        val plan = MosaicPlanner.plan(
            listOf(MosaicImage("a", 0.66f), MosaicImage("b", 0.72f)),
        )

        assertTrue(plan.canvasAspectRatio > 1f)
        assertTrue(plan.cells[0].right <= plan.cells[1].left)
        assertEquals(plan.cells[0].top, plan.cells[1].top, 0.0001f)
    }

    @Test
    fun `two landscape images become a tall stacked mosaic`() {
        val plan = MosaicPlanner.plan(
            listOf(MosaicImage("a", 1.8f), MosaicImage("b", 1.65f)),
        )

        assertTrue(plan.canvasAspectRatio < 1f)
        assertTrue(plan.cells[0].bottom <= plan.cells[1].top)
        assertEquals(plan.cells[0].left, plan.cells[1].left, 0.0001f)
    }
}
