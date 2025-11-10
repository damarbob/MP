package id.monpres.app.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavePdfToDownloadsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Converts a bitmap to a PDF and saves it to the device's public Downloads directory.
     * This is safe to call from any coroutine scope as it performs I/O on a background thread.
     *
     * @param bitmap The image to be converted into a single-page PDF.
     * @param displayName The desired filename for the PDF (e.g., "invoice.pdf").
     * @return A [Result] object, containing `true` on success or an [Exception] on failure.
     */
    suspend operator fun invoke(bitmap: Bitmap, displayName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            // For older versions, this will require WRITE_EXTERNAL_STORAGE permission
            // and is more complex. Sticking to Android 10+ for this example is cleaner.
            // A full implementation would use the File API here.
            null // Simplification for this example
        }

        if (uri == null) {
            return@withContext Result.failure(Exception("Failed to create new MediaStore entry."))
        }

        try {
            // 1. Create a PDF document from the bitmap
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            // 2. Write the PDF to the output stream provided by the resolver
            resolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            } ?: throw Exception("Failed to open output stream for URI: $uri")

            // 3. Close the PDF document
            pdfDocument.close()

            Result.success(true)
        } catch (e: Exception) {
            Log.e("SavePdfUseCase", "Failed to save PDF", e)
            // Clean up the created entry if the write fails
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    }
}
