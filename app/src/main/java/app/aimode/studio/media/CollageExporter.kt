package app.aimode.studio.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.aimode.studio.domain.MosaicImage
import app.aimode.studio.domain.MosaicPlanner
import app.aimode.studio.model.VisualAsset
import app.aimode.studio.model.Workspace
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class CollageExporter(private val context: Context) {
    fun export(workspace: Workspace, arabic: Boolean): Uri? {
        val sources = workspace.visuals.mapNotNull(::sourceFor)
        if (sources.isEmpty()) return null

        val plan = MosaicPlanner.plan(
            sources.map { source -> MosaicImage(source.visual.id, source.aspectRatio) },
        )
        val (boardWidth, boardHeight) = boardSize(plan.canvasAspectRatio)
        val board = Bitmap.createBitmap(boardWidth, boardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(board)
        canvas.drawColor(GAP_COLOR)
        val sourceById = sources.associateBy { it.visual.id }

        try {
            plan.cells.forEachIndexed { index, cell ->
                val source = sourceById.getValue(cell.imageId)
                val destination = RectF(
                    cell.left * boardWidth,
                    cell.top * boardHeight,
                    cell.right * boardWidth,
                    cell.bottom * boardHeight,
                )
                val cellAspect = destination.width() / destination.height()
                val requiredLongSide = when {
                    source.aspectRatio >= cellAspect -> destination.height() * source.aspectRatio
                    else -> destination.width() / source.aspectRatio
                }.times(1.08f).roundToInt().coerceAtMost(MAX_DECODE_SIDE)
                val bitmap = requireNotNull(decodeScaled(source.file, requiredLongSide)) {
                    "Could not decode ${source.file.name}"
                }
                try {
                    drawImageCell(
                        canvas = canvas,
                        bitmap = bitmap,
                        destination = destination,
                        number = index + 1,
                        caption = source.visual.caption,
                        arabic = arabic,
                    )
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            drawWatermark(canvas, boardWidth, boardHeight)
            return save(board)
        } finally {
            board.recycle()
        }
    }

    private fun sourceFor(visual: VisualAsset): ImageSource? {
        val file = File(visual.localPath)
        if (!file.isFile) return null
        val dimensions = if (visual.width > 0 && visual.height > 0) {
            visual.width to visual.height
        } else {
            imageDimensions(file) ?: return null
        }
        return ImageSource(
            visual = visual,
            file = file,
            aspectRatio = (dimensions.first.toFloat() / dimensions.second).coerceIn(0.08f, 12f),
        )
    }

    private fun drawImageCell(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        number: Int,
        caption: String,
        arabic: Boolean,
    ) {
        val shortest = minOf(destination.width(), destination.height())
        val radius = (shortest * 0.035f).coerceIn(18f, 48f)
        val clipPath = Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val scale = max(destination.width() / bitmap.width, destination.height() / bitmap.height)
        val renderedWidth = bitmap.width * scale
        val renderedHeight = bitmap.height * scale
        val rendered = RectF(
            destination.centerX() - renderedWidth / 2f,
            destination.centerY() - renderedHeight / 2f,
            destination.centerX() + renderedWidth / 2f,
            destination.centerY() + renderedHeight / 2f,
        )

        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bitmap, null, rendered, paint)

        if (caption.isNotBlank()) {
            val overlayHeight = (destination.height() * 0.25f).coerceIn(150f, 330f)
            paint.shader = LinearGradient(
                0f,
                destination.bottom - overlayHeight,
                0f,
                destination.bottom,
                Color.TRANSPARENT,
                Color.argb(220, 10, 11, 13),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(
                destination.left,
                destination.bottom - overlayHeight,
                destination.right,
                destination.bottom,
                paint,
            )
            paint.shader = null
            drawCaption(canvas, destination, caption, arabic)
        }

        val badgeRadius = (shortest * 0.065f).coerceIn(38f, 62f)
        val badgeX = destination.left + badgeRadius + 24f
        val badgeY = destination.top + badgeRadius + 24f
        paint.color = Color.argb(226, 21, 20, 17)
        canvas.drawCircle(badgeX, badgeY, badgeRadius, paint)
        paint.color = ACID
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = badgeRadius * 0.92f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText(number.toString(), badgeX, badgeY + badgeRadius * 0.34f, paint)
        canvas.restore()
    }

    private fun drawCaption(canvas: Canvas, destination: RectF, caption: String, arabic: Boolean) {
        val horizontalPadding = (destination.width() * 0.045f).coerceIn(34f, 64f)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (minOf(destination.width(), destination.height()) * 0.07f).coerceIn(36f, 58f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val width = (destination.width() - horizontalPadding * 2f).roundToInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(caption.trim(), 0, caption.trim().length, textPaint, width)
            .setAlignment(if (arabic) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(
            destination.left + horizontalPadding,
            destination.bottom - layout.height - horizontalPadding,
        )
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawWatermark(canvas: Canvas, width: Int, height: Int) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(212, 255, 255, 255)
            textSize = (minOf(width, height) * 0.018f).coerceIn(38f, 54f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.08f
            textAlign = Paint.Align.RIGHT
            setShadowLayer(10f, 0f, 3f, Color.argb(170, 0, 0, 0))
        }
        canvas.drawText("AI MODE  •  HD MOSAIC", width - 34f, height - 34f, textPaint)
    }

    private fun decodeScaled(file: File, targetLongSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetLongSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val rotation = rotationFor(file)
        if (rotation == 0f) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation) },
            true,
        ).also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
    }

    private fun imageDimensions(file: File): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return if (rotationFor(file) in setOf(90f, 270f)) {
            bounds.outHeight to bounds.outWidth
        } else {
            bounds.outWidth to bounds.outHeight
        }
    }

    private fun rotationFor(file: File): Float = runCatching {
        when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)

    private fun boardSize(aspectRatio: Float): Pair<Int, Int> = if (aspectRatio <= 1f) {
        (OUTPUT_LONG_EDGE * aspectRatio).roundToInt() to OUTPUT_LONG_EDGE
    } else {
        OUTPUT_LONG_EDGE to (OUTPUT_LONG_EDGE / aspectRatio).roundToInt()
    }

    private fun save(bitmap: Bitmap): Uri {
        val name = "AI_Mode_HD_Mosaic_${System.currentTimeMillis()}.jpg"
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
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        return uri
    }

    private fun write(bitmap: Bitmap, stream: OutputStream?) {
        requireNotNull(stream) { "Could not open output stream" }
        require(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
            "Could not encode mosaic"
        }
    }

    private data class ImageSource(
        val visual: VisualAsset,
        val file: File,
        val aspectRatio: Float,
    )

    private companion object {
        const val OUTPUT_LONG_EDGE = 3_200
        const val MAX_DECODE_SIDE = 4_200
        const val JPEG_QUALITY = 98
        val GAP_COLOR = Color.rgb(12, 13, 15)
        val ACID = Color.rgb(200, 255, 99)
    }
}
