package id.monpres.app.ui.common.mapper

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import id.monpres.app.R
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException

object ErrorMessageMapper {

    /**
     * Convert any Throwable to a user-friendly localized message
     */
    fun getLocalizedError(context: Context, throwable: Throwable?): String {
        return when (throwable) {
            is FirebaseAuthException -> mapFirebaseAuthException(context, throwable)
            is FirebaseFirestoreException -> mapFirestoreException(context, throwable)
            is FirebaseNetworkException -> context.getString(R.string.error_network)
            is TimeoutCancellationException -> context.getString(R.string.error_timeout)
            is IOException -> mapIOException(context, throwable)
            is SecurityException -> mapSecurityException(context, throwable)
            else -> context.getString(R.string.error_unknown)
        }
    }

    /**
     * Get localized error with additional logging
     */
    fun getLocalizedErrorWithLog(
        context: Context,
        throwable: Throwable,
        tag: String = "ErrorMessageMapper"
    ): String {
        val localizedMessage = getLocalizedError(context, throwable)

        // Log the actual exception for debugging
        when (throwable) {
            else -> {
                Log.e(tag, "Unexpected error: ${throwable.message}", throwable)
            }
        }

        return localizedMessage
    }

    private fun mapFirebaseAuthException(
        context: Context,
        exception: FirebaseAuthException
    ): String {
        return when (exception.errorCode) {
            "ERROR_INVALID_EMAIL" -> context.getString(R.string.error_invalid_email)
            "ERROR_INVALID_CREDENTIAL" -> context.getString(R.string.error_invalid_credential)
            "ERROR_WRONG_PASSWORD" -> context.getString(R.string.error_wrong_password)
            "ERROR_USER_NOT_FOUND" -> context.getString(R.string.error_user_not_found)
            "ERROR_USER_DISABLED" -> context.getString(R.string.error_user_disabled)
            "ERROR_EMAIL_ALREADY_IN_USE" -> context.getString(R.string.error_email_in_use)
            "ERROR_WEAK_PASSWORD" -> context.getString(R.string.error_weak_password)
            "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.error_network)
            else -> context.getString(R.string.error_auth_generic)
        }
    }

    private fun mapFirestoreException(
        context: Context,
        exception: FirebaseFirestoreException
    ): String {
        return when (exception.code) {
            FirebaseFirestoreException.Code.NOT_FOUND -> context.getString(R.string.error_document_not_found)
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> context.getString(R.string.error_permission_denied)
            FirebaseFirestoreException.Code.UNAVAILABLE -> context.getString(R.string.error_network)
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> context.getString(R.string.error_timeout)
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> context.getString(R.string.error_unauthenticated)
            else -> context.getString(R.string.error_firestore_generic)
        }
    }

    private fun mapIOException(context: Context, exception: IOException): String {
        return when {
            exception.message?.contains("ENOSPC", ignoreCase = true) == true ->
                context.getString(R.string.error_storage_full)

            exception.message?.contains("ENOENT", ignoreCase = true) == true ->
                context.getString(R.string.error_file_not_found)

            exception is SocketTimeoutException ->
                context.getString(R.string.error_timeout)

            exception.message?.contains(FIREBASE_PENDING_WRITE) == true -> context.getString(R.string.operation_postponed_until_online)
            else -> context.getString(R.string.error_network)
        }
    }

    private fun mapSecurityException(context: Context, exception: SecurityException): String {
        return when {
            exception.message?.contains("permission", ignoreCase = true) == true ->
                context.getString(R.string.error_storage_permission)

            else -> context.getString(R.string.error_permission_denied)
        }
    }

    const val FIREBASE_PENDING_WRITE = "FIREBASE_PENDING_WRITE"
}
