package id.monpres.app.repository

import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.model.UserIdentity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserIdentityRepository @Inject constructor(): Repository<UserIdentity>() {
    override fun onStart() {
        // TODO("Not yet implemented")
    }

    override fun onDestroy() {
        // TODO("Not yet implemented")
    }

    override fun createRecord(record: UserIdentity) {
        // TODO("Not yet implemented")
    }

    override fun onRecordAdded(record: UserIdentity) {
        // TODO("Not yet implemented")
    }

    override fun onRecordDeleted(record: UserIdentity) {
        // TODO("Not yet implemented")
    }

    override fun onRecordCleared() {
        // TODO("Not yet implemented")
    }

    fun getRecordByUserId(userId: String): UserIdentity? {
        return getRecords().find { it.userId == userId }
    }
}