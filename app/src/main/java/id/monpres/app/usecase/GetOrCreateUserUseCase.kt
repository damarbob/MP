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
            val usersCollection = firestore.collection(MontirPresisiUser.COLLECTION)

            // Get by document ID (document ID is equal to firebaseUser.uid)
            val userDocRef = usersCollection.document(firebaseUser.uid)
            val documentSnapshot = userDocRef.get().await()

            lateinit var token: String

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    // Handle the error
                    Log.e(TAG, "Fetching FCM registration token failed: ${task.exception}")
                    return@addOnCompleteListener
                }

                // Get the FCM token
                token = task.result
                Log.d(TAG, "FCM Token: $token")
            }.await()

            if (documentSnapshot.exists()) {
                Log.d(TAG, "User exists in Firestore for UID: ${firebaseUser.uid}")

                // User exists
                val existingUser = documentSnapshot.toObject(MontirPresisiUser::class.java)
                if (existingUser != null) {
                    val currentDisplayName = firebaseUser.displayName ?: "User"
                    val currentPhoneNumber = firebaseUser.phoneNumber ?: ""
                    val currentFcmToken = existingUser.fcmTokens
                    var updatedUser = existingUser

                    // Check if the user's profile has changed
                    if ((existingUser.displayName != currentDisplayName && currentDisplayName.isNotEmpty()) ||
                        (existingUser.phoneNumber != currentPhoneNumber && currentPhoneNumber.isNotEmpty())
                    ) {
                        Log.d(TAG, "User profile has changed for UID: ${firebaseUser.uid}")

                        updatedUser = updatedUser.copy(
                            displayName = currentDisplayName,
                            phoneNumber = currentPhoneNumber,
                            updatedAt = Timestamp.now().toDate().time.toDouble(),
                        )

                    }

                    if ((currentFcmToken?.isNotEmpty() == true && !currentFcmToken.contains(token)) ||
                        (currentFcmToken == null) || (currentFcmToken.isEmpty())) {
                        Log.d(TAG, "FCM token has changed for UID: ${firebaseUser.uid}")

                        updatedUser = updatedUser.copy(
                            updatedAt = Timestamp.now().toDate().time.toDouble(),
                            fcmTokens = currentFcmToken?.plus(token) ?: listOf(token))
                    }

                    userDocRef.set(updatedUser, SetOptions.merge())
                        .await() // Update Firestore with the new data
                    userRepository.addRecord(updatedUser) // Create record in local repository after successful Firestore update
                    Result.success(updatedUser)
                } else {
                    Result.failure(UserDataParseException("Failed to parse existing user data from Firestore for UID: ${firebaseUser.uid}"))
                }
            } else {
                Log.d(TAG, "User does not exist in Firestore for UID: ${firebaseUser.uid}")

                // User does not exist, create new
                val newUser = MontirPresisiUser(
                    userId = firebaseUser.uid,
                    displayName = firebaseUser.displayName
                        ?: "User", // Provide a default display name if null
                    role = role,
                    phoneNumber = firebaseUser.phoneNumber ?: "",
                    createdAt = Timestamp.now().toDate().time.toDouble(),
                    updatedAt = Timestamp.now().toDate().time.toDouble(),
                    fcmTokens = listOf(token)
                )
                usersCollection.document(firebaseUser.uid).set(newUser).await()
                userRepository.addRecord(newUser) // Create record in local repository after successful Firestore creation
                Result.success(newUser)
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
