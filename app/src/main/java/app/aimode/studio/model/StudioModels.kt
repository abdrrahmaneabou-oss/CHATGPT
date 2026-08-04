package app.aimode.studio.model

enum class ThinkingLens {
    ANALYZE,
    COMPARE,
    EXTRACT,
    CREATE,
    SOLVE,
}

enum class AnswerShape {
    BRIEF,
    STEPS,
    TABLE,
    DEEP_DIVE,
}

enum class PrecisionControl {
    UNCERTAINTY,
    IMAGE_REFERENCES,
    ASK_BEFORE_ASSUMING,
}

data class VisualAsset(
    val id: String,
    val localPath: String,
    val caption: String = "",
)

data class Workspace(
    val goal: String = "",
    val lens: ThinkingLens = ThinkingLens.ANALYZE,
    val answerShape: AnswerShape = AnswerShape.BRIEF,
    val precision: Set<PrecisionControl> = setOf(
        PrecisionControl.UNCERTAINTY,
        PrecisionControl.IMAGE_REFERENCES,
    ),
    val visuals: List<VisualAsset> = emptyList(),
    val selectedVisualId: String? = null,
)

data class StudioUiState(
    val workspace: Workspace = Workspace(),
    val isImporting: Boolean = false,
    val isLaunching: Boolean = false,
    val lastBoardUri: String? = null,
    val removedVisual: VisualAsset? = null,
)

enum class ReadinessGap {
    GOAL,
    SPECIFICITY,
    VISUALS,
    VISUAL_LABELS,
}

data class Readiness(
    val score: Int,
    val nextGap: ReadinessGap?,
)
