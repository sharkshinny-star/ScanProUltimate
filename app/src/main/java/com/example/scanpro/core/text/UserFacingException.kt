package com.example.scanpro.core.text

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

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

/** Throws a [UserFacingException]: the localised counterpart of `error("…")` / `require(…) { "…" }`. */
fun userError(@StringRes messageRes: Int, vararg args: Any, cause: Throwable? = null): Nothing =
    throw UserFacingException(messageRes, *args, cause = cause)

/** Localised `require`: throws [UserFacingException] with [messageRes] when [condition] is false. */
fun userRequire(condition: Boolean, @StringRes messageRes: Int, vararg args: Any) {
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

    /**
     * [error]'s message in a form that can be written to disk and shown later in whatever language the app uses then:
     * the resource *name* (ids change between builds) plus arguments for a [UserFacingException], plain text otherwise.
     */
    fun storable(error: Throwable): String {
        val ctx = context
        val (kind, res, quantity, args) = when (error) {
            is UserFacingException -> Stored("string", error.messageRes, null, error.args)
            is UserFacingPluralException -> Stored("plurals", error.pluralsRes, error.quantity, error.args)
            else -> return error.message.orEmpty()
        }
        val name = ctx?.let { runCatching { it.resources.getResourceEntryName(res) }.getOrNull() } ?: return error.message.orEmpty()
        return JSONObject().put(STORED_KEY, name).put("kind", kind).put("fallback", error.message.orEmpty())
            .apply { if (quantity != null) put("quantity", quantity) }
            .put("args", JSONArray().apply { args.forEach { put(if (it is Number || it is Boolean) it else it.toString()) } })
            .toString()
    }

    /** Reads back what [storable] wrote (or older plain text) in the current language. */
    @SuppressLint("DiscouragedApi") // The id is looked up by name on purpose: stored ids would not survive an update.
    fun fromStorable(stored: String): String {
        val json = stored.takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return stored
        if (!json.has(STORED_KEY)) return stored
        val fallback = json.optString("fallback")
        val ctx = context ?: return fallback
        return runCatching {
            val kind = json.getString("kind")
            val id = ctx.resources.getIdentifier(json.getString(STORED_KEY), kind, ctx.packageName)
            if (id == 0) return fallback
            val array = json.getJSONArray("args")
            val args = Array<Any>(array.length()) { array.get(it) }
            if (kind == "plurals") plural(id, json.getInt("quantity"), *args) else get(id, *args)
        }.getOrDefault(fallback)
    }

    private data class Stored(val kind: String, val res: Int, val quantity: Int?, val args: Array<out Any>)
    private const val STORED_KEY = "messageRes"
}
