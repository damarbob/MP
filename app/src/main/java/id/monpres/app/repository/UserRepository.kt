package id.monpres.app.repository

import id.monpres.app.model.MontirPresisiUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(): Repository<MontirPresisiUser>() {
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

    fun getRecordByUserId(userId: String): MontirPresisiUser? {
        return getRecords().find { it.userId == userId }
    }
}