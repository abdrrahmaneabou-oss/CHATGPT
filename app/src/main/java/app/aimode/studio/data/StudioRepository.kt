package app.aimode.studio.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import app.aimode.studio.model.VisualAsset
import app.aimode.studio.model.Workspace
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class StudioRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val visualDirectory = File(context.filesDir, "context-visuals")

    fun loadWorkspace(): Workspace {
        val raw = preferences.getString(KEY_WORKSPACE, null) ?: return Workspace()
        return runCatching {
            val json = JSONObject(raw)
            val visuals = buildList {
                val array = json.optJSONArray("visuals") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val path = item.getString("path")
                    if (File(path).isFile) {
                        add(
                            VisualAsset(
                                id = item.getString("id"),
                                localPath = path,
                                caption = item.optString("caption"),
                                width = item.optInt("width"),
                                height = item.optInt("height"),
                            ),
                        )
                    }
                }
            }
            val selectedId = json.optString("selectedVisualId").takeIf { candidate ->
                visuals.any { it.id == candidate }
            }
            Workspace(
                visuals = visuals.map { visual ->
                    if (visual.width > 0 && visual.height > 0) visual
                    else imageDimensions(File(visual.localPath))?.let { (width, height) ->
                        visual.copy(width = width, height = height)
                    } ?: visual
                },
                selectedVisualId = selectedId ?: visuals.firstOrNull()?.id,
            )
        }.getOrDefault(Workspace())
    }

    fun saveWorkspace(workspace: Workspace) {
        val json = JSONObject().apply {
            put("selectedVisualId", workspace.selectedVisualId ?: "")
            put(
                "visuals",
                JSONArray().apply {
                    workspace.visuals.forEach { visual ->
                        put(
                            JSONObject().apply {
                                put("id", visual.id)
                                put("path", visual.localPath)
                                put("caption", visual.caption)
                                put("width", visual.width)
                                put("height", visual.height)
                            },
                        )
                    }
                },
            )
        }
        preferences.edit().putString(KEY_WORKSPACE, json.toString()).apply()
    }

    suspend fun importVisuals(uris: List<Uri>, availableSlots: Int): ImportResult = withContext(Dispatchers.IO) {
        if (availableSlots <= 0) return@withContext ImportResult(emptyList(), uris.size)
        if (!visualDirectory.exists()) visualDirectory.mkdirs()

        val imported = mutableListOf<VisualAsset>()
        var failures = 0
        uris.take(availableSlots).forEach { uri ->
            var target: File? = null
            val result = runCatching {
                val extension = extensionFor(uri)
                target = File(visualDirectory, "${UUID.randomUUID()}.$extension")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "No readable stream" }
                    FileOutputStream(requireNotNull(target)).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
                require(requireNotNull(target).length() in 1..MAX_IMPORT_BYTES) { "Unsupported image size" }
                val dimensions = requireNotNull(imageDimensions(requireNotNull(target))) { "Unreadable image" }
                VisualAsset(
                    id = UUID.randomUUID().toString(),
                    localPath = requireNotNull(target).absolutePath,
                    width = dimensions.first,
                    height = dimensions.second,
                )
            }
            result.onSuccess(imported::add).onFailure {
                target?.delete()
                failures += 1
            }
        }
        failures += (uris.size - availableSlots).coerceAtLeast(0)
        ImportResult(imported, failures)
    }

    fun deleteVisual(visual: VisualAsset) {
        runCatching { File(visual.localPath).delete() }
    }

    fun clearVisuals(visuals: List<VisualAsset>) {
        visuals.forEach(::deleteVisual)
    }

    private fun extensionFor(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.lowercase()
            ?.takeIf { it in ALLOWED_EXTENSIONS }
            ?: "img"
    }

    private fun imageDimensions(file: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val rotated = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            ) in setOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270)
        }.getOrDefault(false)
        return if (rotated) options.outHeight to options.outWidth else options.outWidth to options.outHeight
    }

    data class ImportResult(val visuals: List<VisualAsset>, val failedCount: Int)

    private companion object {
        const val PREFERENCES = "ai_mode_context_os"
        const val KEY_WORKSPACE = "workspace_v2"
        const val MAX_IMPORT_BYTES = 40L * 1024L * 1024L
        val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif")
    }
}
