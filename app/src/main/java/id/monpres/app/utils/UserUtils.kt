package id.monpres.app.utils

import id.monpres.app.model.MontirPresisiUser
import java.util.Locale

object UserUtils {

    /**
     * Prepares the user object for Firestore by generating optimized search tokens.
     * This ensures the user can be found by Name, ID, Phone, or Social Handle.
     */
    fun prepareUserForSave(user: MontirPresisiUser): MontirPresisiUser {
        val tokens = mutableListOf<String>()

        // 1. NAME (Generate Prefixes)
        // Allows searching "Damar" via "Da", "Dam", "Dama"...
        tokens.addAll(
            SearchHelper.generateSearchTokens(user.displayName)
        )

        // 2. SOCIAL MEDIA (Exact Match & Lowercase)
        // Users usually search these by exact handle or copy-paste
        user.facebookId?.let { tokens.add(it.lowercase(Locale.getDefault())) }
        user.instagramId?.let { tokens.add(it.lowercase(Locale.getDefault())) }

        // 3. PHONE NUMBER (Sanitized)
        user.phoneNumber?.let { rawPhone ->
            // Remove dashes, spaces, and plus signs (e.g., "+62-895" -> "62895")
            val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "")

            if (cleanPhone.isNotEmpty()) {
                // Add the raw clean number (e.g., "62895...")
                tokens.add(cleanPhone)

                // If it starts with 62, also index the '0' version (e.g., "0895...")
                // This allows users to find the person regardless of which format they type.
                if (cleanPhone.startsWith("62")) {
                    val localFormat = "0" + cleanPhone.substring(2)
                    tokens.add(localFormat)
                }
            }
        }

        // 4. USER ID (Exact Match)
        user.userId?.let {
            tokens.add(it) // Exact casing
            tokens.add(it.lowercase(Locale.getDefault())) // Lowercase safety
        }

        // 5. RETURN COPY
        // .distinct() removes duplicates to save space
        return user.copy(searchTokens = tokens.distinct())
    }
}
