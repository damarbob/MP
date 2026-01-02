package id.monpres.app.utils

fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
    val smallCaseWord = word.lowercase()
    smallCaseWord.replaceFirstChar { it.uppercase() }
}
