package id.monpres.app.repository

import com.google.firebase.auth.FirebaseAuth
import id.monpres.app.model.MontirPresisiUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewUserRepository @Inject constructor(
    private val auth: FirebaseAuth
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

    fun setCurrentUserRecord(user: MontirPresisiUser) {
        setRecords(listOf(user), false)
    }

    fun getCurrentUserRecord(): MontirPresisiUser? {
        return getRecords().find { it.userId == auth.currentUser?.uid }
    }

    fun getRecordByUserId(userId: String): MontirPresisiUser? {
        return getRecords().find { it.userId == userId }
    }

    companion object {
        private val TAG = NewUserRepository::class.java.simpleName
    }
}