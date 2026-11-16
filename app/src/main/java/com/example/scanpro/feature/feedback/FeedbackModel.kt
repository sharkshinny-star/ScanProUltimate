package com.example.scanpro.feature.feedback

import androidx.annotation.StringRes
import com.example.scanpro.R

/** The five faces of "Rate your experience", worst first; [rating] is 1 to 5. */
enum class Mood(val rating: Int, @param:StringRes val label: Int) {
    AWFUL(1, R.string.feedback_face_awful),
    POOR(2, R.string.feedback_face_poor),
    OKAY(3, R.string.feedback_face_okay),
    GOOD(4, R.string.feedback_face_good),
    LOVED(5, R.string.feedback_face_loved);

    companion object {
        fun of(rating: Int): Mood? = entries.firstOrNull { it.rating == rating }
    }
}

/** The follow-up chips under the faces. Which ones show depends on the rating ([reasonsFor]). */
enum class FeedbackReason(@param:StringRes val label: Int) {
    BLURRY(R.string.feedback_reason_blurry),
    EDGES(R.string.feedback_reason_edges),
    SLOW(R.string.feedback_reason_slow),
    HARD_TO_FIND(R.string.feedback_reason_hard_to_find),
    CRASHED(R.string.feedback_reason_crashed),
    QUALITY(R.string.feedback_reason_quality),
    TOOLS(R.string.feedback_reason_tools),
    EASY(R.string.feedback_reason_easy),
    FAST(R.string.feedback_reason_fast),
    TEXT(R.string.feedback_reason_text),
    OTHER(R.string.feedback_reason_other)
}

private val LOW_REASONS = listOf(
    FeedbackReason.BLURRY, FeedbackReason.EDGES, FeedbackReason.SLOW,
    FeedbackReason.HARD_TO_FIND, FeedbackReason.CRASHED, FeedbackReason.OTHER
)
private val HIGH_REASONS = listOf(
    FeedbackReason.QUALITY, FeedbackReason.TOOLS, FeedbackReason.EASY,
    FeedbackReason.FAST, FeedbackReason.TEXT, FeedbackReason.OTHER
)

/** One to three faces ask what to fix; four and five ask what the user likes. */
fun reasonsFor(rating: Int): List<FeedbackReason> = when (rating) {
    in 1..3 -> LOW_REASONS
    in 4..5 -> HIGH_REASONS
    else -> emptyList()
}

@StringRes
fun followUpQuestion(rating: Int): Int = when (rating) {
    1, 2 -> R.string.feedback_follow_low
    3 -> R.string.feedback_follow_mid
    else -> R.string.feedback_follow_high
}

/** A face is needed; "Other" on its own says nothing until the user writes what it is. */
fun canSubmitRating(rating: Int, reasons: Set<FeedbackReason>, text: String): Boolean =
    Mood.of(rating) != null && !(reasons == setOf(FeedbackReason.OTHER) && text.isBlank())

/** Whether "Other" is chosen, which turns the optional comment box into "Tell us more". */
fun asksForDetail(reasons: Set<FeedbackReason>): Boolean = FeedbackReason.OTHER in reasons

/** The body of a rating email: the rating line, the follow-up question with the chosen answers, then the comments. */
fun ratingEmailText(ratingLine: String, question: String, reasons: List<String>, commentsLabel: String, comments: String): String =
    buildString {
        append(ratingLine)
        if (reasons.isNotEmpty()) {
            append("\n\n").append(question)
            reasons.forEach { append("\n• ").append(it) }
        }
        if (comments.isNotBlank()) append("\n\n").append(commentsLabel).append('\n').append(comments.trim())
    }

/** The topics of "Report an issue". */
enum class IssueCategory(@param:StringRes val label: Int) {
    SLOW(R.string.feedback_cat_slow),
    QUALITY(R.string.feedback_cat_quality),
    FILTER(R.string.feedback_cat_filter),
    SAVE_SHARE(R.string.feedback_cat_save_share),
    CRASH(R.string.feedback_cat_crash),
    OTHERS(R.string.feedback_cat_others)
}

/** The form's two uses: an issue report (categories, diagnostics attached) or a feature suggestion (no categories). */
enum class FeedbackFormKind { ISSUE, FEATURE }

const val MIN_DESCRIPTION_LENGTH = 6
const val MAX_SCREENSHOTS = 5

/** An issue needs a category and a description of at least [MIN_DESCRIPTION_LENGTH] characters; a suggestion only the text. */
fun canSubmitForm(kind: FeedbackFormKind, categories: Set<IssueCategory>, text: String): Boolean =
    (kind == FeedbackFormKind.FEATURE || categories.isNotEmpty()) && text.trim().length >= MIN_DESCRIPTION_LENGTH
