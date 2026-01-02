package id.monpres.app.utils

import java.util.Locale

object SearchHelper {

    /**
     * Generates a list of search tokens for Firestore array-contains queries.
     * It creates prefixes for every word in the input strings.
     *
     * Example: "Damar SM" -> ["d", "da", "dam", "dama", "damar", "s", "sm"]
     */
    fun generateSearchTokens(vararg inputs: String?): List<String> {
        val tokens = mutableListOf<String>()

        inputs.filterNotNull().forEach { input ->
            // 1. Clean the input: Lowercase and remove special chars if necessary
            val cleanedInput = input.lowercase(Locale.getDefault()).trim()

            // 2. Split into individual words
            val words = cleanedInput.split("\\s+".toRegex())

            // 3. Generate prefixes for each word
            words.forEach { word ->
                var currentTerm = ""
                word.forEach { char ->
                    currentTerm += char
                    tokens.add(currentTerm)
                }
            }
        }

        // Remove duplicates and return
        return tokens.distinct()
    }
}
