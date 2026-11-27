package com.example.scanpro.imaging

import android.net.Uri
import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingException

/**
 * [uri] needs a password to open: none was given, or [wrongPassword] is true because the one given did not work.
 * The Tools screen asks for it and runs the tool again. Anywhere else it reads as the usual "unlock it first" message.
 */
class PdfPasswordNeededException(val uri: Uri, val wrongPassword: Boolean, cause: Throwable? = null) :
    UserFacingException(R.string.tools_err_password_protected, cause = cause)
