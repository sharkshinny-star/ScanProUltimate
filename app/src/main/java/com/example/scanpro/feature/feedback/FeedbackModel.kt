package com.example.scanpro.feature.feedback

import androidx.annotation.StringRes
import com.example.scanpro.R

/** The five ratings of the rating sheet, worst first, each with its word; [rating] is 1 to 5 stars. */
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

/** What the rating sheet does after a rating. */
enum class RatingFollowUp { THANKS, PROBLEM }

/**
 * Four and five stars are thanked at once; one to three are asked what went wrong ("Tell us the problem you
 * encountered"). Null for anything that is not a rating.
 */
fun followUpFor(rating: Int): RatingFollowUp? = when (rating) {
    in 4..5 -> RatingFollowUp.THANKS
    in 1..3 -> RatingFollowUp.PROBLEM
    else -> null
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
