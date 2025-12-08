package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import id.monpres.app.utils.UserUtils
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class GetOrCreateUserUseCase @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
) {
    companion object {
        private val TAG: String = GetOrCreateUserUseCase::class.java.simpleName
    }

    // Custom exceptions for better error identification
    class UserNotAuthenticatedException(message: String = "User not authenticated") :
        Exception(message)

    class UserDataParseException(message: String) : Exception(message)
    class FirestoreOperationException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    suspend operator fun invoke(role: UserRole): Result<MontirPresisiUser> {
        val firebaseUser = auth.currentUser
            ?: return Result.failure(UserNotAuthenticatedException())

        return try {
            // Force getting the token synchronously within the coroutine
            val token = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.e(TAG, "Fetching FCM registration token failed", e)
                // It's critical to fail here if we can't get a token.
                return Result.failure(FirestoreOperationException("Failed to retrieve FCM token", e))
            }

            val usersCollection = firestore.collection(MontirPresisiUser.COLLECTION)
            val userDocRef = usersCollection.document(firebaseUser.uid)
            val documentSnapshot = userDocRef.get().await()

            if (documentSnapshot.exists()) {
                Log.d(TAG, "User exists in Firestore for UID: ${firebaseUser.uid}")
                val existingUser = documentSnapshot.toObject(MontirPresisiUser::class.java)
                    ?: return Result.failure(UserDataParseException("Failed to parse user data"))

                val currentDisplayName = firebaseUser.displayName ?: "User"
                val currentPhoneNumber = firebaseUser.phoneNumber ?: ""
                var needsUpdate = false
                var updatedUser = existingUser

                // Check if display name or phone number has changed
                if ((updatedUser.displayName != currentDisplayName && currentDisplayName.isNotEmpty()) ||
                    (updatedUser.phoneNumber != currentPhoneNumber && currentPhoneNumber.isNotEmpty())
                ) {
                    Log.d(TAG, "User profile data has changed for UID: ${firebaseUser.uid}")
                    updatedUser = updatedUser.copy(
                        displayName = currentDisplayName,
                        phoneNumber = currentPhoneNumber
                    )
                    needsUpdate = true
                }

                // Check if FCM token needs to be added
                if (updatedUser.fcmTokens?.contains(token) != true) {
                    Log.d(TAG, "FCM token needs to be updated for UID: ${firebaseUser.uid}")
                    updatedUser = updatedUser.copy(
                        fcmTokens = (updatedUser.fcmTokens ?: emptyList()) + token
                    )
                    needsUpdate = true
                }

                // Only perform a write if something has actually changed
                if (needsUpdate) {
                    Log.d(TAG, "Updating user document for UID: ${firebaseUser.uid}")
                    updatedUser = updatedUser.copy(updatedAt = Timestamp.now().toDate().time.toDouble())
                    userDocRef.set(updatedUser, SetOptions.merge()).await()
                    userRepository.setCurrentUserRecord(updatedUser)
                    Result.success(updatedUser)
                } else {
                    // No changes needed, just return the existing user and ensure local repo is up to date
                    Log.d(TAG, "No user data changes detected for UID: ${firebaseUser.uid}")
                    userRepository.setCurrentUserRecord(existingUser)
                    Result.success(existingUser)
                }

            } else {
                Log.d(TAG, "User does not exist in Firestore for UID: ${firebaseUser.uid}")

                // User does not exist, create new
                val newUser = MontirPresisiUser(
                    userId = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "User",
                    role = role,
                    phoneNumber = firebaseUser.phoneNumber ?: "",
                    createdAt = Timestamp.now().toDate().time.toDouble(),
                    updatedAt = Timestamp.now().toDate().time.toDouble(),
                    fcmTokens = listOf(token)
                )

                // Prepare the user
                val finalNewUser = UserUtils.prepareUserForSave(newUser)

                userDocRef.set(finalNewUser).await()
                userRepository.setCurrentUserRecord(finalNewUser)
                Result.success(finalNewUser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in $TAG: ${e.message}", e)
            Result.failure(
                FirestoreOperationException(
                    "Failed to get or create user: ${e.localizedMessage}",
                    e
                )
            )
        }
    }
}
