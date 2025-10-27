package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.enums.UserRole
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.repository.PartnerRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPartnersUseCase @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val partnerRepository: PartnerRepository
) {
    companion object {
        private val TAG = GetPartnersUseCase::class.java.simpleName
    }

    // In your Repository or ViewModel
    operator fun invoke(onResult: (Result<List<MontirPresisiUser>>) -> Unit) {
        firestore
            .collection(MontirPresisiUser.COLLECTION) // TODO: Hardcoded collection name
            .whereEqualTo("role", UserRole.PARTNER)
//            .whereNotEqualTo("active", false) // TODO: Do server-side filtering instead
            .get()
            .addOnSuccessListener { querySnapshot ->
                val partners = ArrayList<MontirPresisiUser>()
                querySnapshot.map { document ->
                    val partner = document.toObject(MontirPresisiUser::class.java)

                    // Filter out inactive partners TODO: Do server-side filtering instead
                    if (partner.active != false) {
                        partners.add(partner)
                    }
                }
                Log.d(TAG, "Partners: $partners")
                partnerRepository.setRecords(partners, true)
                onResult(Result.success(partnerRepository.getRecords()))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}