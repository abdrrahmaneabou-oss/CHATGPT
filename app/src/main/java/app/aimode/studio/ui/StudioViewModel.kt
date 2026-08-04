package app.aimode.studio.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aimode.studio.data.StudioRepository
import app.aimode.studio.media.CollageExporter
import app.aimode.studio.model.StudioUiState
import app.aimode.studio.model.VisualAsset
import app.aimode.studio.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudioRepository(application)
    private val collageExporter = CollageExporter(application)

    private val _state = MutableStateFlow(StudioUiState(workspace = repository.loadWorkspace()))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<StudioEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun selectVisual(id: String) = updateWorkspace { copy(selectedVisualId = id) }

    fun setVisualCaption(id: String, caption: String) = updateWorkspace {
        copy(
            visuals = visuals.map { visual ->
                if (visual.id == id) visual.copy(caption = caption.take(MAX_CAPTION_LENGTH)) else visual
            },
        )
    }

    fun moveVisual(id: String, delta: Int) = updateWorkspace {
        val currentIndex = visuals.indexOfFirst { it.id == id }
        if (currentIndex < 0) return@updateWorkspace this
        val target = (currentIndex + delta).coerceIn(0, visuals.lastIndex)
        if (target == currentIndex) return@updateWorkspace this
        val reordered = visuals.toMutableList()
        val item = reordered.removeAt(currentIndex)
        reordered.add(target, item)
        copy(visuals = reordered)
    }

    fun removeVisual(id: String) {
        val existingRemoved = _state.value.removedVisual
        existingRemoved?.let(repository::deleteVisual)
        val workspace = _state.value.workspace
        val index = workspace.visuals.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = workspace.visuals[index]
        val nextVisuals = workspace.visuals.filterNot { it.id == id }
        val nextSelected = when {
            workspace.selectedVisualId != id -> workspace.selectedVisualId
            nextVisuals.isEmpty() -> null
            else -> nextVisuals[minOf(index, nextVisuals.lastIndex)].id
        }
        val nextWorkspace = workspace.copy(visuals = nextVisuals, selectedVisualId = nextSelected)
        repository.saveWorkspace(nextWorkspace)
        _state.update { it.copy(workspace = nextWorkspace, removedVisual = removed) }
        emit(StudioEvent.VisualRemoved)
    }

    fun undoRemove() {
        val removed = _state.value.removedVisual ?: return
        updateWorkspace { copy(visuals = visuals + removed, selectedVisualId = removed.id) }
        _state.update { it.copy(removedVisual = null) }
    }

    fun importVisuals(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isImporting) return
        val available = MAX_IMAGES - _state.value.workspace.visuals.size
        if (available <= 0) {
            emit(StudioEvent.MaxImages)
            return
        }
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val result = repository.importVisuals(uris, available)
            val current = _state.value.workspace
            val visuals = current.visuals + result.visuals
            val next = current.copy(
                visuals = visuals,
                selectedVisualId = result.visuals.firstOrNull()?.id ?: current.selectedVisualId,
            )
            repository.saveWorkspace(next)
            _state.update { it.copy(workspace = next, isImporting = false) }
            if (result.failedCount > 0) emit(StudioEvent.ImportFailed)
        }
    }

    fun resetWorkspace() {
        val state = _state.value
        repository.clearVisuals(state.workspace.visuals)
        state.removedVisual?.let(repository::deleteVisual)
        val empty = Workspace()
        repository.saveWorkspace(empty)
        _state.value = StudioUiState(workspace = empty)
    }

    fun exportBoard(arabic: Boolean) {
        if (_state.value.isExporting) return
        val workspace = _state.value.workspace
        if (workspace.visuals.isEmpty()) return
        _state.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val exportResult = runCatching {
                withContext(Dispatchers.IO) { requireNotNull(collageExporter.export(workspace, arabic)) }
            }
            val boardUri = exportResult.getOrNull()?.toString()
            _state.update { it.copy(isExporting = false, lastBoardUri = boardUri ?: it.lastBoardUri) }
            if (boardUri != null) _events.emit(StudioEvent.BoardExported(boardUri))
            else _events.emit(StudioEvent.BoardExportFailed)
        }
    }

    fun acceptSharedIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    parcelableUri(intent, Intent.EXTRA_STREAM)?.let { importVisuals(listOf(it)) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    importVisuals(parcelableUriList(intent, Intent.EXTRA_STREAM))
                }
            }
        }
    }

    private fun updateWorkspace(transform: Workspace.() -> Workspace) {
        val next = _state.value.workspace.transform()
        repository.saveWorkspace(next)
        _state.update { it.copy(workspace = next) }
    }

    private fun emit(event: StudioEvent) {
        _events.tryEmit(event)
    }

    @Suppress("DEPRECATION")
    private fun parcelableUri(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(key, Uri::class.java)
        else intent.getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private fun parcelableUriList(intent: Intent, key: String): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
        else intent.getParcelableArrayListExtra<Uri>(key).orEmpty()

    sealed interface StudioEvent {
        data object ImportFailed : StudioEvent
        data object MaxImages : StudioEvent
        data object VisualRemoved : StudioEvent
        data class BoardExported(val uri: String) : StudioEvent
        data object BoardExportFailed : StudioEvent
    }

    private companion object {
        const val MAX_IMAGES = 5
        const val MAX_CAPTION_LENGTH = 120
    }
}
