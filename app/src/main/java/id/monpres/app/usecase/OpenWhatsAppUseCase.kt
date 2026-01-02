package id.monpres.app.usecase

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Use case for opening WhatsApp with a specific phone number and optional message.
 */
class OpenWhatsAppUseCase {
    /**
     * Opens WhatsApp with the specified phone number.
     *
     * @param context Android context for launching the intent
     * @param phone Phone number in international format (e.g., "62812345678")
     * @param message Optional pre-filled message
     * @param onFailure Callback invoked if WhatsApp cannot be opened
     */
    operator fun invoke(
        context: Context,
        phone: String,
        message: String? = null,
        onFailure: (Exception) -> Unit = { e ->
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://wa.me/$phone".toUri()
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure(e)
        }

    }
}