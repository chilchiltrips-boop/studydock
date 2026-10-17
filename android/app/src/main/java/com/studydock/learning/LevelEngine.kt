package com.studydock.learning

/** Ten levels derived entirely from verified questions, never generated. */
data class StudyLevel(val number: Int, val questions: List<StudyQuestion>, val mastered: Int, val unlocked: Boolean) {
    val neededToUnlockNext: Int get() = (questions.size * 4 + 4) / 5
}
object LevelEngine {
    const val LEVEL_COUNT = 10
    fun levels(part: StudyPart?, progress: StudyProgress): List<StudyLevel> {
        val ordered = part?.questions?.sortedWith(compareBy({ it.sourcePage }, { it.id })).orEmpty()
        val mastered = progress.masteredIds()
        val result = ArrayList<StudyLevel>(LEVEL_COUNT)
        for (index in 0 until LEVEL_COUNT) {
            val batch = ordered.subList(index * ordered.size / LEVEL_COUNT, (index + 1) * ordered.size / LEVEL_COUNT)
            val completed = batch.count { it.id in mastered }
            val unlocked = if (index == 0) batch.isNotEmpty() else {
                val previous = result.last()
                previous.unlocked && previous.questions.isNotEmpty() && previous.mastered >= previous.neededToUnlockNext
            }
            result.add(StudyLevel(index + 1, batch.toList(), completed, unlocked))
        }
        return result
    }
}
