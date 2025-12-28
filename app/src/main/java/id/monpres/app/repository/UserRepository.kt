package id.monpres.app.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.usecase.ObserveCollectionByIdUseCase
import id.monpres.app.usecase.UpdateDataByIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val observeCollectionByIdUseCase: ObserveCollectionByIdUseCase,
    private val updateDataByIdUseCase: UpdateDataByIdUseCase
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

    fun observeUserById(): Flow<MontirPresisiUser> {
        val currentUserId = auth.currentUser?.uid

        return if (currentUserId == null) {
            Log.w(TAG, "No user is signed in")
            _userRecord.value = null
            emptyFlow()
        } else {
            observeCollectionByIdUseCase(
                auth.currentUser?.uid ?: throw IllegalStateException("No user is signed in"),
                MontirPresisiUser.COLLECTION, MontirPresisiUser::class.java
            )
                .mapNotNull { user ->
                    setRecords(listOf(user!!), false)
                    _userRecord.value = user
                    user
                }
//            .distinctUntilChanged()
                .catch {
                    Log.e(TAG, "Error observing user", it)
                }
                .flowOn(Dispatchers.IO) // Run the collection and mapping on an IO thread
        }
    }

    suspend fun updateUser(user: MontirPresisiUser) {
        if (user.userId == null) throw IllegalArgumentException("User ID cannot be null")
        else {
            updateDataByIdUseCase(user.userId!!, MontirPresisiUser.COLLECTION, user)
            _userRecord.value = user
        }
    }

    companion object {
        private const val TAG = "UserRepository"
    }
}