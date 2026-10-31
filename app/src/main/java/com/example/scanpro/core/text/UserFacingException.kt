package com.example.scanpro.core.text

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * An error whose message is shown to the user. It carries a string resource rather than English text, and its
 * [message] is read in the app's current language each time it is asked for, so every screen that shows
 * `error.message` (snackbars, notices, the "Unsaved scans" list) shows it translated without knowing about this class.
 */
open class UserFacingException(
    @param:StringRes val messageRes: Int,
    vararg val args: Any,
    cause: Throwable? = null
) : IllegalArgumentException(null as String?, cause) {
    override val message: String get() = AppStrings.get(messageRes, *args)
}

/** Like [UserFacingException], with a count that picks the plural form. */
class UserFacingPluralException(
    @param:PluralsRes val pluralsRes: Int,
    val quantity: Int,
    vararg val args: Any,
    cause: Throwable? = null
) : IllegalArgumentException(null as String?, cause) {
    override val message: String get() = AppStrings.plural(pluralsRes, quantity, *args)
}

/** Throws a [UserFacingException]: the localized counterpart of `error("…")` / `require(…) { "…" }`. */
fun userError(@StringRes messageRes: Int, vararg args: Any, cause: Throwable? = null): Nothing =
    throw UserFacingException(messageRes, *args, cause = cause)

/** Localized `require`: throws [UserFacingException] with [messageRes] when [condition] is false. */
inline fun userRequire(condition: Boolean, @StringRes messageRes: Int, vararg args: Any) {
    if (!condition) throw UserFacingException(messageRes, *args)
}

/**
 * The application's resources, for code that has no Context of its own (engines written as plain objects). Set once
 * in [com.example.scanpro.ScanProApplication.onCreate]. In JVM unit tests it is never set, and messages read as the
 * resource id followed by the arguments, which tests can still check.
 */
// Holds the application context only (see init), which lives as long as the process: nothing to leak.
@SuppressLint("StaticFieldLeak")
object AppStrings {
    @Volatile private var context: Context? = null

    fun init(context: Context) { this.context = context.applicationContext }

    fun get(@StringRes res: Int, vararg args: Any): String =
        context?.let { if (args.isEmpty()) it.getString(res) else it.getString(res, *args) } ?: fallback(res, args)

    fun plural(@PluralsRes res: Int, quantity: Int, vararg args: Any): String =
        context?.resources?.getQuantityString(res, quantity, *args) ?: fallback(res, args)

    private fun fallback(res: Int, args: Array<out Any>) = "#$res " + args.joinToString(" ")
}
