package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.utils.UserUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for updating user documents in Firestore with callback-based result handling.
 *
 * Prepares user data before saving and provides success/failure callbacks.
 */
@Singleton
class UpdateUserUseCase @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private val TAG = UpdateUserUseCase::class.java.simpleName
    }

    /**
     * Executes an update for a single user document in Firestore.
     * This will completely overwrite the document with the new [user] object.
     *
     * @param user The MontirPresisiUser object containing the new data.
     * The `userId` property must not be null or blank.
     * @param onResult A callback that returns Result.success(Unit) on success
     * or Result.failure(Exception) on failure.
     */
    operator fun invoke(user: MontirPresisiUser, onResult: (Result<Unit>) -> Unit) {
        // We must have a userId to update a document
        if (user.userId.isNullOrBlank()) {
            val error =
                IllegalArgumentException("User ID cannot be null or blank for an update operation.")
            Log.w(TAG, "Update failed: ${error.message}")
            onResult(Result.failure(error))
            return
        }

        // Prepare the user
        val user = UserUtils.prepareUserForSave(user)

        firestore
            .collection(MontirPresisiUser.COLLECTION)
            .document(user.userId!!) // Safe due to the check above
            .set(user) // Using set() to overwrite the document with the new object
            .addOnSuccessListener {
                Log.d(TAG, "Successfully updated user: ${user.userId}")
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to update user: ${user.userId}", exception)
                onResult(Result.failure(exception))
            }
    }
}
