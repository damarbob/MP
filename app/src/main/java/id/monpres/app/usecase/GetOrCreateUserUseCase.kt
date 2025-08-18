package id.monpres.app.usecase

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.UserRepository
import kotlinx.coroutines.tasks.await // Make sure to add 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services' dependency
import javax.inject.Inject

class GetOrCreateUserUseCase @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
    @param:ApplicationContext private val context: Context
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

            if (documentSnapshot.exists()) {
                Log.d(TAG, "User exists in Firestore for UID: ${firebaseUser.uid}")

                // User exists
                val existingUser = documentSnapshot.toObject(MontirPresisiUser::class.java)
                if (existingUser != null) {
                    val currentDisplayName = firebaseUser.displayName ?: "User"
                    val currentPhoneNumber = firebaseUser.phoneNumber ?: ""

                    // Check if the user's profile has changed
                    if ((existingUser.displayName != currentDisplayName && currentDisplayName.isNotEmpty()) ||
                        (existingUser.phoneNumber != currentPhoneNumber && currentPhoneNumber.isNotEmpty())) {
                        Log.d(TAG, "User profile has changed for UID: ${firebaseUser.uid}")

                        // Create updated user data
                        val updatedUser = existingUser.copy(
                            displayName = currentDisplayName,
                            phoneNumber = currentPhoneNumber,
                            updatedAt = Timestamp.now().toDate().time.toDouble()
                        )

                        userDocRef.set(updatedUser, SetOptions.merge()).await() // Update Firestore with the new data
                        userRepository.addRecord(updatedUser) // Create record in local repository after successful Firestore update
                        Result.success(updatedUser)
                    } else {
                        userRepository.addRecord(existingUser)
                        Result.success(existingUser)
                    }
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
                    updatedAt = Timestamp.now().toDate().time.toDouble()
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
