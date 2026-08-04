package app.aimode.studio.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.aimode.studio.model.ThinkingLens
import app.aimode.studio.model.Workspace
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

class CollageExporter(private val context: Context) {
    fun export(workspace: Workspace, arabic: Boolean): Uri? {
        if (workspace.visuals.isEmpty()) return null
        val decoded = workspace.visuals.mapNotNull { visual ->
            decodeScaled(File(visual.localPath), MAX_DECODE_SIDE)?.let { visual to it }
        }
        require(decoded.isNotEmpty()) { "No readable images" }

        val board = renderBoard(workspace, decoded, arabic)
        return try {
            save(board)
        } finally {
            decoded.forEach { (_, bitmap) -> if (!bitmap.isRecycled) bitmap.recycle() }
            board.recycle()
        }
    }

    private fun renderBoard(
        workspace: Workspace,
        decoded: List<Pair<app.aimode.studio.model.VisualAsset, Bitmap>>,
        arabic: Boolean,
    ): Bitmap {
        val output = Bitmap.createBitmap(BOARD_WIDTH, BOARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(PAPER)

        paint.color = INK
        canvas.drawRect(0f, 0f, BOARD_WIDTH.toFloat(), 18f, paint)
        paint.color = SOLAR
        canvas.drawRect(0f, 18f, BOARD_WIDTH * 0.34f, 34f, paint)

        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 58f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("AI MODE / CONTEXT BOARD", OUTER_GAP.toFloat(), 115f, brand)

        val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 28f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val lens = when (workspace.lens) {
            ThinkingLens.ANALYZE -> if (arabic) "تحليل" else "ANALYZE"
            ThinkingLens.COMPARE -> if (arabic) "مقارنة" else "COMPARE"
            ThinkingLens.EXTRACT -> if (arabic) "استخراج" else "EXTRACT"
            ThinkingLens.CREATE -> if (arabic) "ابتكار" else "CREATE"
            ThinkingLens.SOLVE -> if (arabic) "حلّ" else "SOLVE"
        }
        canvas.drawText("$lens  •  ${decoded.size} VISUAL${if (decoded.size == 1) "" else "S"}", OUTER_GAP.toFloat(), 166f, meta)

        val goalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 38f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        drawTextBlock(
            canvas = canvas,
            text = workspace.goal.trim().take(220),
            paint = goalPaint,
            left = OUTER_GAP,
            top = 205,
            width = BOARD_WIDTH - OUTER_GAP * 2,
            maxLines = 2,
            alignment = if (arabic) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL,
        )

        val cells = boardCells(decoded.size)
        decoded.forEachIndexed { index, (visual, bitmap) ->
            drawVisualCell(canvas, cells[index], bitmap, index + 1, visual.caption, arabic)
        }

        paint.color = INK
        canvas.drawRect(0f, (BOARD_HEIGHT - FOOTER_HEIGHT).toFloat(), BOARD_WIDTH.toFloat(), BOARD_HEIGHT.toFloat(), paint)
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.06f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            if (arabic) "جُهّز محليًا • استخدم أرقام الصور داخل المحادثة" else "PREPARED LOCALLY • CITE IMAGES BY NUMBER",
            BOARD_WIDTH / 2f,
            BOARD_HEIGHT - 47f,
            footer,
        )
        return output
    }

    private fun drawVisualCell(
        canvas: Canvas,
        cell: RectF,
        bitmap: Bitmap,
        number: Int,
        caption: String,
        arabic: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        canvas.drawRoundRect(cell, 34f, 34f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = BORDER
        canvas.drawRoundRect(cell, 34f, 34f, paint)
        paint.style = Paint.Style.FILL

        val captionHeight = if (caption.isBlank()) 0f else 112f
        val imageArea = RectF(
            cell.left + 18f,
            cell.top + 18f,
            cell.right - 18f,
            cell.bottom - 18f - captionHeight,
        )
        paint.color = Color.rgb(232, 229, 221)
        canvas.drawRoundRect(imageArea, 24f, 24f, paint)

        val scale = min(imageArea.width() / bitmap.width, imageArea.height() / bitmap.height)
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val left = (imageArea.left + (imageArea.width() - width) / 2f).toInt()
        val top = (imageArea.top + (imageArea.height() - height) / 2f).toInt()
        canvas.drawBitmap(bitmap, null, Rect(left, top, left + width, top + height), paint)

        paint.color = INK
        canvas.drawCircle(cell.left + 62f, cell.top + 62f, 37f, paint)
        paint.color = ACID
        paint.textSize = 34f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText(number.toString(), cell.left + 62f, cell.top + 74f, paint)

        if (caption.isNotBlank()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                textSize = 30f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            drawTextBlock(
                canvas = canvas,
                text = caption.trim(),
                paint = textPaint,
                left = (cell.left + 28f).toInt(),
                top = (cell.bottom - captionHeight + 18f).toInt(),
                width = (cell.width() - 56f).toInt(),
                maxLines = 2,
                alignment = if (arabic) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL,
            )
        }
    }

    private fun boardCells(count: Int): List<RectF> {
        val left = OUTER_GAP.toFloat()
        val right = (BOARD_WIDTH - OUTER_GAP).toFloat()
        val top = CONTENT_TOP.toFloat()
        val bottom = (BOARD_HEIGHT - FOOTER_HEIGHT - OUTER_GAP).toFloat()
        val gap = CELL_GAP.toFloat()
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        return when (count) {
            1 -> listOf(RectF(left, top, right, bottom))
            2 -> listOf(
                RectF(left, top, midX - gap / 2f, bottom),
                RectF(midX + gap / 2f, top, right, bottom),
            )
            3 -> listOf(
                RectF(left, top, right, midY - gap / 2f),
                RectF(left, midY + gap / 2f, midX - gap / 2f, bottom),
                RectF(midX + gap / 2f, midY + gap / 2f, right, bottom),
            )
            4 -> gridFour(left, top, right, bottom, gap)
            else -> {
                val heroBottom = top + (bottom - top) * 0.38f
                listOf(RectF(left, top, right, heroBottom)) +
                    gridFour(left, heroBottom + gap, right, bottom, gap)
            }
        }
    }

    private fun gridFour(left: Float, top: Float, right: Float, bottom: Float, gap: Float): List<RectF> {
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        return listOf(
            RectF(left, top, midX - gap / 2f, midY - gap / 2f),
            RectF(midX + gap / 2f, top, right, midY - gap / 2f),
            RectF(left, midY + gap / 2f, midX - gap / 2f, bottom),
            RectF(midX + gap / 2f, midY + gap / 2f, right, bottom),
        )
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        left: Int,
        top: Int,
        width: Int,
        maxLines: Int,
        alignment: Layout.Alignment,
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    private fun decodeScaled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val rotation = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        bitmap.recycle()
        return rotated
    }

    private fun save(bitmap: Bitmap): Uri {
        val name = "AI_Mode_Context_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Mode")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
            ) { "Could not create MediaStore item" }
            return try {
                context.contentResolver.openOutputStream(uri).use { stream -> write(bitmap, stream) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }

        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "AI Mode",
        )
        require(directory.exists() || directory.mkdirs()) { "Could not create output directory" }
        val file = File(directory, name)
        FileOutputStream(file).use { stream -> write(bitmap, stream) }
        val uri = Uri.fromFile(file)
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        return uri
    }

    private fun write(bitmap: Bitmap, stream: OutputStream?) {
        requireNotNull(stream) { "Could not open output stream" }
        require(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "Could not encode board" }
    }

    private companion object {
        const val BOARD_WIDTH = 1800
        const val BOARD_HEIGHT = 2200
        const val CONTENT_TOP = 390
        const val FOOTER_HEIGHT = 110
        const val OUTER_GAP = 46
        const val CELL_GAP = 30
        const val MAX_DECODE_SIDE = 1800
        val PAPER = Color.rgb(245, 241, 232)
        val INK = Color.rgb(21, 20, 17)
        val MUTED = Color.rgb(103, 99, 90)
        val SOLAR = Color.rgb(255, 91, 53)
        val ACID = Color.rgb(200, 255, 99)
        val BORDER = Color.rgb(205, 199, 187)
    }
}
