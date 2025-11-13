package id.monpres.app.usecase

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.NewUserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNewUsersUseCase @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val newUserRepository: NewUserRepository
) {
    companion object {
        private val TAG = GetNewUsersUseCase::class.java.simpleName
    }

    operator fun invoke(onResult: (Result<List<MontirPresisiUser>>) -> Unit) {
        // Firestore's whereIn doesn't support null. We need to do two separate queries.
        val pendingQuery = firestore
            .collection(MontirPresisiUser.COLLECTION)
            .where(
                Filter.and(
                    Filter.equalTo("role", UserRole.CUSTOMER),
                    Filter.equalTo("verificationStatus", UserVerificationStatus.PENDING)
                )
            )
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()

        val nullQuery = firestore
            .collection(MontirPresisiUser.COLLECTION)
            .where(
                Filter.and(
                    Filter.equalTo("role", UserRole.CUSTOMER),
                    Filter.equalTo("verificationStatus", null)
                )
            )
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()

        // Combine the results of both queries
        Tasks.whenAllSuccess<Any>(pendingQuery, nullQuery)
            .addOnSuccessListener { results ->
                val users = mutableSetOf<MontirPresisiUser>()
                results.forEach { result ->
                    (result as? com.google.firebase.firestore.QuerySnapshot)?.documents?.forEach { document ->
                        users.add(document.toObject(MontirPresisiUser::class.java)!!)
                    }
                }
                val userList = users.sortedByDescending { it.createdAt }
                Log.d(TAG, "New users: $users")
                newUserRepository.setRecords(userList, true)
                onResult(Result.success(newUserRepository.getRecords()))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}