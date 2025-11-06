package id.monpres.app.usecase

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

class OpenWhatsAppUseCase {
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