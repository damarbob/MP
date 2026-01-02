package id.monpres.app.enums

enum class VehicleWheelDrive {
    AWD, // All Wheel Drive
    FWD, // Front Wheel Drive
    RWD; // Rear Wheel Drive

    fun label(): String {
        return when (this) {
            AWD -> "All Wheel Drive"
            FWD -> "Front Wheel Drive"
            RWD -> "Rear Wheel Drive"
        }
    }

    fun fromString(value: String): VehicleWheelDrive {
        return when (value.uppercase()) {
            "AWD" -> AWD
            "FWD" -> FWD
            "RWD" -> RWD
            else -> throw IllegalArgumentException("Invalid value: $value")
        }
    }

    companion object {
        fun toList(): List<VehicleWheelDrive> {
            return listOf(AWD, FWD, RWD)
        }

        fun toListString(): List<String> {
            return listOf(AWD.toString(), FWD.toString(), RWD.toString())
        }
    }
}
