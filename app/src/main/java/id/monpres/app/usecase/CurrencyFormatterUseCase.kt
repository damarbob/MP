package id.monpres.app.usecase

import java.text.NumberFormat
import java.util.Locale

class CurrencyFormatterUseCase {
    private var value = 0f
    private fun format(): String {
        val deviceLocale = Locale.getDefault()
        val currencyLocale = Locale.Builder().setRegion("ID").setLanguage("id").build()
        return NumberFormat.getCurrencyInstance(currencyLocale)
            .apply { maximumFractionDigits = 0 }
            .format(value)
            .toString()
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            NumberFormatter.withLocale(deviceLocale)
//                .notation(Notation.simple())
//                .unit(Currency.getInstance(currencyLocale))
//                .unitWidth(NumberFormatter.UnitWidth.NARROW)
//                .precision(Precision.currency(Currency.CurrencyUsage.STANDARD))
//                .format(value).toString()
//        } else {
//            NumberFormat.getCurrencyInstance(
//                currencyLocale
//            ).apply {
//                maximumFractionDigits = 0
//            }.format(value).toString()
//        }
    }

    operator fun invoke(value: Float): String {
        this.value = value
        return format()
    }

    operator fun invoke(value: Int): String {
        this.value = value.toFloat()
        return format()
    }

    operator fun invoke(value: Double): String {
        this.value = value.toFloat()
        return format()
    }
}