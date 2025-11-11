package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
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

    // In your Repository or ViewModel
    operator fun invoke(onResult: (Result<List<MontirPresisiUser>>) -> Unit) {
        firestore
            .collection(MontirPresisiUser.COLLECTION)
            .whereEqualTo("role", UserRole.CUSTOMER)
            .whereEqualTo("verificationStatus", UserVerificationStatus.PENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val users = ArrayList<MontirPresisiUser>()
                querySnapshot.map { document ->
                    val partner = document.toObject(MontirPresisiUser::class.java)

                    users.add(partner)
                }
                Log.d(TAG, "New users: $users")
                newUserRepository.setRecords(users, true)
                onResult(Result.success(newUserRepository.getRecords()))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}