package id.monpres.app.usecase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import id.monpres.app.model.MontirPresisiUser
import id.monpres.app.utils.SearchHelper
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MigrateUsersSearchTokens @Inject constructor() {

    companion object {
        private val TAG = MigrateUsersSearchTokens::class.simpleName
    }

    suspend operator fun invoke() {
        val firestore = FirebaseFirestore.getInstance()
        val usersCollection = firestore.collection(MontirPresisiUser.COLLECTION)

        try {
            val snapshot = usersCollection.get().await()
            val documents = snapshot.documents

            if (documents.isEmpty()) return

            var batch = firestore.batch()
            var counter = 0

            documents.forEach { doc ->
                val user = doc.toObject(MontirPresisiUser::class.java)

                if (user != null) {
                    // 1. Start with a Mutable List
                    // Generate prefix tokens for Name (and Socials if you want "starts-with" search for them too)
                    val tokens = SearchHelper.generateSearchTokens(
                        user.displayName
                        // Recommendation: Add social IDs here if you want to find "damarbob" by typing "dama"
                        // user.instagramId,
                        // user.facebookId
                    ).toMutableList()

                    // 2. Add Exact Matches (No prefixes, just the full string)
                    // Note: We use safe calls (.let) to add only if not null

                    user.facebookId?.let { tokens.add(it.lowercase()) }
                    user.instagramId?.let { tokens.add(it.lowercase()) }

                    // 3. Phone Number Logic (Sanitize it!)
                    // If you save "+62-812", users can't find it by typing "0812"
                    user.phoneNumber?.let { rawPhone ->

                        // If rawPhone is blank, cancel
                        if (rawPhone.isBlank()) return@let

                        val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "") // "62812..."
                        tokens.add(cleanPhone) // Add raw digits

                        // If it's Indonesia format, add the "0" version too
                        if (cleanPhone.startsWith("62")) {
                            tokens.add("0" + cleanPhone.substring(2)) // "0812..."
                        }
                    }

                    // 4. User ID (Exact match only)
                    user.userId?.let {
                        tokens.add(it) // Exact case
                        tokens.add(it.lowercase()) // Lowercase for safety
                    }

                    // 5. Final Cleanup (Remove duplicates)
                    val finalTokens = tokens.distinct()

                    batch.update(doc.reference, "searchTokens", finalTokens)
                    counter++

                    if (counter == 450) {
                        batch.commit().await()
                        batch = firestore.batch()
                        counter = 0
                    }
                }
            }

            if (counter > 0) {
                batch.commit().await()
            }

            Log.i(TAG, "Migration Success")

        } catch (e: Exception) {
            Log.e(TAG, "Migration Failed: ${e.message}")
        }
    }
}