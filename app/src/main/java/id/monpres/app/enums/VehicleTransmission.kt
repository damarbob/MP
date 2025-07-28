package id.monpres.app.enums

enum class VehicleTransmission {
    AUTOMATIC,
    MANUAL;

    fun fromString(value: String): VehicleTransmission {
        return when (value.uppercase()) {
            "AUTOMATIC" -> AUTOMATIC
            "MANUAL" -> MANUAL
            else -> throw IllegalArgumentException("Invalid value: $value")
        }
    }

    companion object {

        fun toList(): List<VehicleTransmission> {
            return listOf(AUTOMATIC, MANUAL)
        }

        fun toListString(): List<String> {
            return listOf(AUTOMATIC.toString(), MANUAL.toString())
        }
    }
}