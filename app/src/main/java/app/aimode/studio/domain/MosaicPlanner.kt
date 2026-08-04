package app.aimode.studio.domain

import kotlin.math.abs
import kotlin.math.ln

data class MosaicImage(
    val id: String,
    val aspectRatio: Float,
)

data class MosaicCell(
    val imageId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class MosaicPlan(
    val canvasAspectRatio: Float,
    val cells: List<MosaicCell>,
)

object MosaicPlanner {
    private const val GAP = 0.006f
    private val canvasAspects = listOf(0.8f, 1f, 1.25f)

    fun plan(images: List<MosaicImage>): MosaicPlan {
        require(images.size in 1..5) { "Mosaic supports one to five images" }
        if (images.size == 1) {
            val aspect = images.first().aspectRatio.safeAspect().coerceIn(0.45f, 2.2f)
            return MosaicPlan(aspect, listOf(cell(images.first().id, 0f, 0f, 1f, 1f)))
        }

        return canvasAspects
            .flatMap { canvasAspect -> candidates(images, canvasAspect) }
            .minBy(::cropScore)
    }

    private fun candidates(images: List<MosaicImage>, canvasAspect: Float): List<Candidate> {
        val ids = images.map { it.id }
        val layouts = when (images.size) {
            2 -> layoutsForTwo(ids)
            3 -> layoutsForThree(ids)
            4 -> layoutsForFour(ids)
            5 -> layoutsForFive(ids)
            else -> error("Unsupported image count")
        }
        return layouts.map { Candidate(MosaicPlan(canvasAspect, it), images) }
    }

    private fun layoutsForTwo(ids: List<String>): List<List<MosaicCell>> = listOf(
        columns(ids, listOf(0.5f, 0.5f)),
        rows(ids, listOf(0.5f, 0.5f)),
        columns(ids, listOf(0.58f, 0.42f)),
        rows(ids, listOf(0.58f, 0.42f)),
    )

    private fun layoutsForThree(ids: List<String>): List<List<MosaicCell>> = listOf(
        columns(ids, listOf(1f / 3f, 1f / 3f, 1f / 3f)),
        rows(ids, listOf(1f / 3f, 1f / 3f, 1f / 3f)),
        heroLeft(ids),
        heroTop(ids),
    )

    private fun layoutsForFour(ids: List<String>): List<List<MosaicCell>> = listOf(
        grid(ids, columns = 2, rows = 2),
        columns(ids, List(4) { 0.25f }),
        rows(ids, List(4) { 0.25f }),
    )

    private fun layoutsForFive(ids: List<String>): List<List<MosaicCell>> = listOf(
        heroTopWithGrid(ids),
        heroLeftWithGrid(ids),
        twoThenThree(ids),
        threeThenTwo(ids),
    )

    private fun columns(ids: List<String>, fractions: List<Float>): List<MosaicCell> {
        var cursor = 0f
        return ids.mapIndexed { index, id ->
            val next = if (index == ids.lastIndex) 1f else cursor + fractions[index]
            val result = cell(
                id,
                left = cursor + if (index == 0) 0f else GAP / 2f,
                top = 0f,
                right = next - if (index == ids.lastIndex) 0f else GAP / 2f,
                bottom = 1f,
            )
            cursor = next
            result
        }
    }

    private fun rows(ids: List<String>, fractions: List<Float>): List<MosaicCell> {
        var cursor = 0f
        return ids.mapIndexed { index, id ->
            val next = if (index == ids.lastIndex) 1f else cursor + fractions[index]
            val result = cell(
                id,
                left = 0f,
                top = cursor + if (index == 0) 0f else GAP / 2f,
                right = 1f,
                bottom = next - if (index == ids.lastIndex) 0f else GAP / 2f,
            )
            cursor = next
            result
        }
    }

    private fun grid(ids: List<String>, columns: Int, rows: Int): List<MosaicCell> = ids.mapIndexed { index, id ->
        val column = index % columns
        val row = index / columns
        val left = column.toFloat() / columns + if (column == 0) 0f else GAP / 2f
        val right = (column + 1f) / columns - if (column == columns - 1) 0f else GAP / 2f
        val top = row.toFloat() / rows + if (row == 0) 0f else GAP / 2f
        val bottom = (row + 1f) / rows - if (row == rows - 1) 0f else GAP / 2f
        cell(id, left, top, right, bottom)
    }

    private fun heroLeft(ids: List<String>): List<MosaicCell> = listOf(
        cell(ids[0], 0f, 0f, 0.58f - GAP / 2f, 1f),
        cell(ids[1], 0.58f + GAP / 2f, 0f, 1f, 0.5f - GAP / 2f),
        cell(ids[2], 0.58f + GAP / 2f, 0.5f + GAP / 2f, 1f, 1f),
    )

    private fun heroTop(ids: List<String>): List<MosaicCell> = listOf(
        cell(ids[0], 0f, 0f, 1f, 0.58f - GAP / 2f),
        cell(ids[1], 0f, 0.58f + GAP / 2f, 0.5f - GAP / 2f, 1f),
        cell(ids[2], 0.5f + GAP / 2f, 0.58f + GAP / 2f, 1f, 1f),
    )

    private fun heroTopWithGrid(ids: List<String>): List<MosaicCell> = listOf(
        cell(ids[0], 0f, 0f, 1f, 0.44f - GAP / 2f),
        cell(ids[1], 0f, 0.44f + GAP / 2f, 0.5f - GAP / 2f, 0.72f - GAP / 2f),
        cell(ids[2], 0.5f + GAP / 2f, 0.44f + GAP / 2f, 1f, 0.72f - GAP / 2f),
        cell(ids[3], 0f, 0.72f + GAP / 2f, 0.5f - GAP / 2f, 1f),
        cell(ids[4], 0.5f + GAP / 2f, 0.72f + GAP / 2f, 1f, 1f),
    )

    private fun heroLeftWithGrid(ids: List<String>): List<MosaicCell> = listOf(
        cell(ids[0], 0f, 0f, 0.44f - GAP / 2f, 1f),
        cell(ids[1], 0.44f + GAP / 2f, 0f, 0.72f - GAP / 2f, 0.5f - GAP / 2f),
        cell(ids[2], 0.72f + GAP / 2f, 0f, 1f, 0.5f - GAP / 2f),
        cell(ids[3], 0.44f + GAP / 2f, 0.5f + GAP / 2f, 0.72f - GAP / 2f, 1f),
        cell(ids[4], 0.72f + GAP / 2f, 0.5f + GAP / 2f, 1f, 1f),
    )

    private fun twoThenThree(ids: List<String>): List<MosaicCell> = listOf(
        cell(ids[0], 0f, 0f, 0.5f - GAP / 2f, 0.52f - GAP / 2f),
        cell(ids[1], 0.5f + GAP / 2f, 0f, 1f, 0.52f - GAP / 2f),
        cell(ids[2], 0f, 0.52f + GAP / 2f, 1f / 3f - GAP / 2f, 1f),
        cell(ids[3], 1f / 3f + GAP / 2f, 0.52f + GAP / 2f, 2f / 3f - GAP / 2f, 1f),
        cell(ids[4], 2f / 3f + GAP / 2f, 0.52f + GAP / 2f, 1f, 1f),
    )

    private fun threeThenTwo(ids: List<String>): List<MosaicCell> = listOf(
        cell(ids[0], 0f, 0f, 1f / 3f - GAP / 2f, 0.48f - GAP / 2f),
        cell(ids[1], 1f / 3f + GAP / 2f, 0f, 2f / 3f - GAP / 2f, 0.48f - GAP / 2f),
        cell(ids[2], 2f / 3f + GAP / 2f, 0f, 1f, 0.48f - GAP / 2f),
        cell(ids[3], 0f, 0.48f + GAP / 2f, 0.5f - GAP / 2f, 1f),
        cell(ids[4], 0.5f + GAP / 2f, 0.48f + GAP / 2f, 1f, 1f),
    )

    private fun cropScore(candidate: Candidate): Double {
        val sourceById = candidate.images.associateBy { it.id }
        return candidate.plan.cells.sumOf { mosaicCell ->
            val sourceAspect = sourceById.getValue(mosaicCell.imageId).aspectRatio.safeAspect()
            val cellAspect = (mosaicCell.width * candidate.plan.canvasAspectRatio / mosaicCell.height)
                .coerceAtLeast(0.05f)
            val cropPenalty = abs(ln((sourceAspect / cellAspect).toDouble()))
            val cellArea = mosaicCell.width * mosaicCell.height
            cropPenalty * (0.55 + cellArea)
        }
    }

    private fun Float.safeAspect(): Float = takeIf { isFinite() && this > 0f } ?: 1f

    private fun cell(id: String, left: Float, top: Float, right: Float, bottom: Float) =
        MosaicCell(id, left, top, right, bottom)

    private data class Candidate(val plan: MosaicPlan, val images: List<MosaicImage>)
}
