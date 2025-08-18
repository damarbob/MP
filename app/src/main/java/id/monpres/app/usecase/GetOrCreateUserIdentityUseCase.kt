package id.monpres.app.usecase

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import id.monpres.app.model.UserIdentity
import id.monpres.app.repository.UserIdentityRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class GetOrCreateUserIdentityUseCase @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userIdentityRepository: UserIdentityRepository,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val TAG: String = GetOrCreateUserIdentityUseCase::class.java.simpleName
    }

    suspend operator fun invoke(): Result<UserIdentity> {
        val firebaseUser = auth.currentUser
            ?: return Result.failure(Exception("User not authenticated"))

        return try {
            val usersCollection = firestore.collection(UserIdentity.COLLECTION)

            // Get by document ID (document ID is equal to firebaseUser.uid)
            val userDocRef = usersCollection.document(firebaseUser.uid)
            val documentSnapshot = userDocRef.get().await()

            if (documentSnapshot.exists()) {
                Log.d(TAG, "User exists in Firestore for UID: ${firebaseUser.uid}")

                // User exists
                val existingIdentity = documentSnapshot.toObject(UserIdentity::class.java)
                if (existingIdentity != null) {
                    val currentEmail = firebaseUser.email

                    // Check if the user's identity has changed
                    if (existingIdentity.email != currentEmail) {
                        Log.d(TAG, "User identity has changed for UID: ${firebaseUser.uid}")

                        // Create updated user data
                        val updatedUserIdentity = existingIdentity.copy(
                            email = currentEmail,
                            updatedAt = Timestamp.now().toDate().time.toDouble()
                        )

                        userDocRef.set(updatedUserIdentity, SetOptions.merge()).await() // Update Firestore with the new data
                        userIdentityRepository.addRecord(updatedUserIdentity) // Create record in local repository after successful Firestore update
                        Result.success(updatedUserIdentity)
                    } else {
                        userIdentityRepository.addRecord(existingIdentity) // Create record in local repository after successful Firestore creation
                        Result.success(existingIdentity)
                    }
                } else {
                    Result.failure(Exception("Failed to parse existing user data from Firestore for UID: ${firebaseUser.uid}"))
                }
            } else {
                Log.d(TAG, "User does not exist in Firestore for UID: ${firebaseUser.uid}")

                // User does not exist, create new
                val newIdentity = UserIdentity(
                    userId = firebaseUser.uid,
                    email = firebaseUser.email,
                    createdAt = Timestamp.now().toDate().time.toDouble(),
                    updatedAt = Timestamp.now().toDate().time.toDouble()
                )
                usersCollection.document(firebaseUser.uid).set(newIdentity).await()
                userIdentityRepository.addRecord(newIdentity) // Create record in local repository after successful Firestore creation
                Result.success(newIdentity)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error in $TAG: ${e.message}",
                e
            )
            Result.failure(e)
        }
    }
}