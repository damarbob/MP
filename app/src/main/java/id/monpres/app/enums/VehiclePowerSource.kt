package id.monpres.app.enums

enum class VehiclePowerSource {
    HYBRID, // PETROL AND ELECTRIC
    GASOLINE,
    SOLAR,
    ELECTRIC;

    fun label(): String {
        return when (this) {
            HYBRID -> "Hybrid"
            GASOLINE -> "Gasoline"
            SOLAR -> "Solar"
            ELECTRIC -> "Electric"
        }
    }

    fun fromString(value: String): VehiclePowerSource {
        return when (value.uppercase()) {
            "HYBRID" -> HYBRID
            "GASOLINE" -> GASOLINE
            "SOLAR" -> SOLAR
            "ELECTRIC" -> ELECTRIC
            else -> throw IllegalArgumentException("Invalid value: $value")
        }

    }

    companion object {
        fun toListString(): List<String> {
            return listOf(HYBRID.toString(), GASOLINE.toString(), SOLAR.toString(), ELECTRIC.toString())
        }

        fun toList(): List<VehiclePowerSource> {
            return listOf(HYBRID, GASOLINE, SOLAR, ELECTRIC)
        }
    }
}