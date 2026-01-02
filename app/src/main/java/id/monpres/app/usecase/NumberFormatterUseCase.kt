package id.monpres.app.usecase

import android.icu.text.NumberFormat

/**
 * Formatter for converting numbers to localized string format with configurable decimal places.
 *
 * Supports Int, Long, Float, and Double inputs.
 */
class NumberFormatterUseCase {
    operator fun invoke(number: Int, decimalPlaces: Int = 0): String {
        return formatNumber(number.toDouble(), decimalPlaces)
    }

    operator fun invoke(number: Long, decimalPlaces: Int = 0): String {
        return formatNumber(number.toDouble(), decimalPlaces)
    }

    operator fun invoke(number: Float, decimalPlaces: Int = 0): String {
        return formatNumber(number.toDouble(), decimalPlaces)
    }

    operator fun invoke(number: Double, decimalPlaces: Int = 0): String {
        return formatNumber(number, decimalPlaces)
    }

    private fun formatNumber(number: Double, decimalPlaces: Int): String {
        val formattedNumber = NumberFormat.getInstance().apply {
            maximumFractionDigits = decimalPlaces
        }.format(number)
        return formattedNumber
    }
}
