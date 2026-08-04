package app.aimode.studio.model

data class VisualAsset(
    val id: String,
    val localPath: String,
    val caption: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

data class Workspace(
    val visuals: List<VisualAsset> = emptyList(),
    val selectedVisualId: String? = null,
)

data class StudioUiState(
    val workspace: Workspace = Workspace(),
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val lastBoardUri: String? = null,
    val removedVisual: VisualAsset? = null,
)
