package app.aimode.studio.domain

import app.aimode.studio.model.AnswerShape
import app.aimode.studio.model.PrecisionControl
import app.aimode.studio.model.ThinkingLens
import app.aimode.studio.model.VisualAsset
import app.aimode.studio.model.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEngineTest {
    @Test
    fun `empty goal produces no compiled prompt`() {
        assertEquals("", PromptEngine.compile(Workspace(), arabic = false))
    }

    @Test
    fun `arabic prompt preserves visual order and precision`() {
        val workspace = Workspace(
            goal = "قارن التصميمين وحدد أيهما أوضح للمستخدم الجديد",
            lens = ThinkingLens.COMPARE,
            answerShape = AnswerShape.TABLE,
            precision = setOf(PrecisionControl.UNCERTAINTY, PrecisionControl.IMAGE_REFERENCES),
            visuals = listOf(
                VisualAsset("a", "/a.jpg", "الواجهة القديمة"),
                VisualAsset("b", "/b.jpg", "الواجهة المقترحة"),
            ),
        )

        val prompt = PromptEngine.compile(workspace, arabic = true)

        assertTrue(prompt.contains("الصورة 1: الواجهة القديمة"))
        assertTrue(prompt.contains("الصورة 2: الواجهة المقترحة"))
        assertTrue(prompt.indexOf("الصورة 1") < prompt.indexOf("الصورة 2"))
        assertTrue(prompt.contains("صرّح بما لا يمكن الجزم به"))
    }

    @Test
    fun `readiness rewards a specific labelled workspace`() {
        val sparse = Workspace(goal = "حلل")
        val rich = Workspace(
            goal = "Analyze the two checkout screens and recommend the clearest path for a first-time buyer.",
            visuals = listOf(VisualAsset("a", "/a.jpg", "Current checkout")),
        )

        assertTrue(PromptEngine.readiness(rich).score > PromptEngine.readiness(sparse).score)
        assertTrue(PromptEngine.readiness(rich).score >= 90)
    }
}
