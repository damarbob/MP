package id.monpres.app.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for saving bitmap images to the device's public gallery.
 *
 * Handles platform-specific MediaStore APIs for Android 10+ with fallback for older versions.
 */
@Singleton
class SaveImageToGalleryUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        val TAG = SaveImageToGalleryUseCase::class.simpleName
    }
    /**
     * Saves a bitmap to the device's public gallery (Pictures directory).
     * This function is safe to call from any coroutine scope and handles I/O on a background thread.
     * It uses modern MediaStore APIs for Android 10+ and a fallback for older versions.
     *
     * @param bitmap The image to save.
     * @param displayName The desired filename for the image (e.g., "my-invoice.jpg").
     * @return A [Result] object, containing `true` on success or an [Exception] on failure.
     */
    suspend operator fun invoke(bitmap: Bitmap, displayName: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            try {

                // On Android 10+, use MediaStore with the IS_PENDING flag for robust saving.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val uri =
                        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            ?: throw Exception("MediaStore returned a null URI")

                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream == null) throw Exception("Failed to open output stream for URI: $uri")
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }

                    // Now that the file is written, clear the IS_PENDING flag.
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                } else {
                    // Fallback for devices older than Android 10.
                    @Suppress("DEPRECATION")
                    val imagesDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val imageFile = File(imagesDir, displayName)
                    FileOutputStream(imageFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    }
                }
                Result.success(true)
            } catch (e: Exception) {
                Log.e("SaveImageUseCase", "Failed to save image to gallery", e)
                Result.failure(e)
            }
        }
}
