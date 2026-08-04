package app.aimode.studio.domain

import app.aimode.studio.model.AnswerShape
import app.aimode.studio.model.PrecisionControl
import app.aimode.studio.model.Readiness
import app.aimode.studio.model.ReadinessGap
import app.aimode.studio.model.ThinkingLens
import app.aimode.studio.model.Workspace

object PromptEngine {
    fun readiness(workspace: Workspace): Readiness {
        val goal = workspace.goal.trim()
        var score = when {
            goal.length >= 90 -> 48
            goal.length >= 40 -> 42
            goal.length >= 18 -> 32
            goal.isNotBlank() -> 18
            else -> 0
        }

        score += 14 // A thinking lens is always explicit.
        score += 13 // An answer shape is always explicit.
        score += minOf(10, workspace.precision.size * 4)

        if (workspace.visuals.isNotEmpty()) {
            score += 9
            val labelled = workspace.visuals.count { it.caption.trim().length >= 3 }
            score += ((labelled.toFloat() / workspace.visuals.size) * 6).toInt()
        }

        val nextGap = when {
            goal.isBlank() -> ReadinessGap.GOAL
            goal.length < 18 -> ReadinessGap.SPECIFICITY
            workspace.visuals.isEmpty() -> ReadinessGap.VISUALS
            workspace.visuals.any { it.caption.trim().length < 3 } -> ReadinessGap.VISUAL_LABELS
            else -> null
        }

        return Readiness(score.coerceIn(0, 100), nextGap)
    }

    fun compile(workspace: Workspace, arabic: Boolean): String {
        val goal = workspace.goal.trim()
        if (goal.isEmpty()) return ""
        return if (arabic) compileArabic(workspace, goal) else compileEnglish(workspace, goal)
    }

    private fun compileArabic(workspace: Workspace, goal: String): String = buildString {
        appendLine("المهمة")
        appendLine(goal)
        appendLine()
        appendLine("عدسة التفكير: ${lensArabic(workspace.lens)}")
        appendLine("شكل الإجابة: ${shapeArabic(workspace.answerShape)}")

        if (workspace.visuals.isNotEmpty()) {
            appendLine()
            appendLine("السياق البصري — استخدم أرقام الصور عند الاستشهاد:")
            workspace.visuals.forEachIndexed { index, visual ->
                val role = visual.caption.trim().ifBlank { "مرجع بصري رقم ${index + 1}" }
                appendLine("- الصورة ${index + 1}: $role")
            }
        }

        if (workspace.precision.isNotEmpty()) {
            appendLine()
            appendLine("ضوابط الدقة:")
            workspace.precision.sortedBy { it.ordinal }.forEach { control ->
                appendLine("- ${precisionArabic(control)}")
            }
        }

        appendLine()
        append("ابدأ بالنتيجة مباشرة، ثم فسّر منطقك بوضوح. لا تخترع تفاصيل غير موجودة في السؤال أو الصور.")
    }

    private fun compileEnglish(workspace: Workspace, goal: String): String = buildString {
        appendLine("MISSION")
        appendLine(goal)
        appendLine()
        appendLine("Thinking lens: ${lensEnglish(workspace.lens)}")
        appendLine("Answer shape: ${shapeEnglish(workspace.answerShape)}")

        if (workspace.visuals.isNotEmpty()) {
            appendLine()
            appendLine("VISUAL CONTEXT — cite images by number:")
            workspace.visuals.forEachIndexed { index, visual ->
                val role = visual.caption.trim().ifBlank { "Visual reference ${index + 1}" }
                appendLine("- Image ${index + 1}: $role")
            }
        }

        if (workspace.precision.isNotEmpty()) {
            appendLine()
            appendLine("PRECISION CONTROLS")
            workspace.precision.sortedBy { it.ordinal }.forEach { control ->
                appendLine("- ${precisionEnglish(control)}")
            }
        }

        appendLine()
        append("Lead with the result, then explain the reasoning clearly. Do not invent details that are not present in the request or images.")
    }

    private fun lensArabic(lens: ThinkingLens) = when (lens) {
        ThinkingLens.ANALYZE -> "حلّل الأنماط والأسباب والآثار"
        ThinkingLens.COMPARE -> "قارن بالمعايير نفسها ووضّح الفروق الحاسمة"
        ThinkingLens.EXTRACT -> "استخرج الحقائق والبيانات القابلة للاستخدام"
        ThinkingLens.CREATE -> "ابتكر حلًا أصيلًا قابلًا للتنفيذ"
        ThinkingLens.SOLVE -> "شخّص السبب الجذري واقترح الحل"
    }

    private fun lensEnglish(lens: ThinkingLens) = when (lens) {
        ThinkingLens.ANALYZE -> "Analyze patterns, causes, and implications"
        ThinkingLens.COMPARE -> "Compare with consistent criteria and expose decisive differences"
        ThinkingLens.EXTRACT -> "Extract usable facts and structured data"
        ThinkingLens.CREATE -> "Create an original, executable solution"
        ThinkingLens.SOLVE -> "Diagnose the root cause and propose the fix"
    }

    private fun shapeArabic(shape: AnswerShape) = when (shape) {
        AnswerShape.BRIEF -> "موجز ذكي يبدأ بالخلاصة"
        AnswerShape.STEPS -> "خطوات مرتبة مع أفعال واضحة"
        AnswerShape.TABLE -> "جدول مقارن ثم توصية"
        AnswerShape.DEEP_DIVE -> "تحليل عميق منظم بعناوين"
    }

    private fun shapeEnglish(shape: AnswerShape) = when (shape) {
        AnswerShape.BRIEF -> "A smart brief that leads with the conclusion"
        AnswerShape.STEPS -> "Ordered steps with clear actions"
        AnswerShape.TABLE -> "A comparison table followed by a recommendation"
        AnswerShape.DEEP_DIVE -> "A structured deep dive with headings"
    }

    private fun precisionArabic(control: PrecisionControl) = when (control) {
        PrecisionControl.UNCERTAINTY -> "صرّح بما لا يمكن الجزم به بدل ملء الفراغات بالتخمين."
        PrecisionControl.IMAGE_REFERENCES -> "اربط كل ملاحظة بعبارة «الصورة 1» أو «الصورة 2» عند الحاجة."
        PrecisionControl.ASK_BEFORE_ASSUMING -> "إذا كان نقص معلومة سيغيّر النتيجة جذريًا، اطرح سؤالًا واحدًا قبل الإجابة."
    }

    private fun precisionEnglish(control: PrecisionControl) = when (control) {
        PrecisionControl.UNCERTAINTY -> "State what cannot be known instead of filling gaps with guesses."
        PrecisionControl.IMAGE_REFERENCES -> "Tie visual claims to “Image 1”, “Image 2”, and so on."
        PrecisionControl.ASK_BEFORE_ASSUMING -> "If one missing fact would materially change the result, ask one question first."
    }
}
