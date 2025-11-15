package id.monpres.app.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.MontirPresisiUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : Repository<MontirPresisiUser>() {

    // Private mutable state flow to hold the user record
    private val _userRecord = MutableStateFlow<MontirPresisiUser?>(null)

    // Public, read-only state flow for observers
    val userRecord: StateFlow<MontirPresisiUser?> = _userRecord.asStateFlow()

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
        _userRecord.value = null
    }

    fun setCurrentUserRecord(user: MontirPresisiUser) {
        _userRecord.value = user
        setRecords(listOf(user), false)
    }

    fun getCurrentUserRecord(): MontirPresisiUser? {
        return getRecords().find { it.userId == auth.currentUser?.uid }
    }

    fun getRecordByUserId(userId: String): MontirPresisiUser? {
        return getRecords().find { it.userId == userId }
    }

    suspend fun removeFcmToken(token: String) {
        val currentUserId = auth.currentUser?.uid

        if (currentUserId != null) {
            val userDocRef = firestore.collection("users").document(auth.currentUser?.uid!!)
            // Atomically remove the token from the array
            userDocRef.update("fcmTokens", FieldValue.arrayRemove(token))
                .await()
        } else {
            Log.w(TAG, "No user is signed in")
        }
    }

    companion object {
        private const val TAG = "UserRepository"
    }
}