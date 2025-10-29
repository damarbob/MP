package id.monpres.app.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.MontirPresisiUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : Repository<MontirPresisiUser>() {
    override fun onStart() {
        // TODO("Not yet implemented")
    }

    override fun onDestroy() {
        // TODO("Not yet implemented")
    }

    override fun createRecord(record: MontirPresisiUser) {
        // TODO("Not yet implemented")
    }

    override fun onRecordAdded(record: MontirPresisiUser) {
        // TODO("Not yet implemented")
    }

    override fun onRecordDeleted(record: MontirPresisiUser) {
        // TODO("Not yet implemented")
    }

    override fun onRecordCleared() {
        // TODO("Not yet implemented")
    }

    fun setUserRecord(user: MontirPresisiUser) {
        setRecords(listOf(user), false)
    }

    fun getCurrentUserRecord(): MontirPresisiUser? {
        return getRecords().find { it.userId == auth.currentUser?.uid }
    }

    fun getRecordByUserId(userId: String): MontirPresisiUser? {
        return getRecords().find { it.userId == userId }
    }

    fun removeFcmToken(token: String, onSuccess: () -> Unit, onFailure: (() -> Unit)? = null) {
        val currentUserId = auth.currentUser?.uid

        if (currentUserId != null) {
            val userDocRef = firestore.collection("users").document(auth.currentUser?.uid!!)
            // Atomically remove the token from the array
            userDocRef.update("fcmTokens", FieldValue.arrayRemove(token))
                .addOnCompleteListener { removeTask ->
                    if (!removeTask.isSuccessful) {
                        Log.w(
                            TAG,
                            "FCM token removal failed.",
                            removeTask.exception
                        )
                        onFailure?.invoke()
                    }
                    onSuccess()
                }
        } else {
            Log.w(TAG, "No user is signed in")
            onFailure?.invoke()
        }
    }

    companion object {
        private const val TAG = "UserRepository"
    }
}