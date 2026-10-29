package com.kalaiselvan.ride.bluetooth

/**
 * Decodes motorcycle factory VINs, model descriptor chunks (Frame 0x03),
 * and Bluetooth advertised names into clean, rider-friendly motorcycle model names.
 */
object BikeModelDecoder {

    /**
     * Decodes a bike model name from:
     * - VIN (17 or 13 chars, e.g. "ML5ZRDS1XXXXXXXXX")
     * - Raw model code or Frame 0x03 chunk (e.g. "ML5ZRDS1", "ZRDS1", "ZR900S")
     * - Bluetooth device name (e.g. "Bike-ZR900S", "Smart Bike")
     */
    fun decode(modelCode: String?, vin: String? = null): String {
        // 1. Try decoding from full VIN if available
        if (!vin.isNullOrBlank()) {
            val fromVin = decodeVin(vin)
            if (fromVin != null) return fromVin
        }

        // 2. Try decoding from modelCode / Bluetooth name / chunk
        if (!modelCode.isNullOrBlank()) {
            val fromCode = decodeCodeOrName(modelCode)
            if (fromCode != null) return fromCode
        }

        // 3. Fallback: sanitize raw code (strip manufacturer prefixes, underscores, etc.)
        return sanitizeFallback(modelCode)
    }

    private fun decodeVin(vin: String): String? {
        val cleanVin = vin.uppercase().trim()
        if (cleanVin.length < 8) return null

        // In standard VIN (ISO 3779):
        // Positions 1-3: WMI (e.g. ML5 = India, JKA/JKB = Japan/US, MLH = Thailand, 96P = Brazil)
        // Positions 4-8: VDS (e.g. ZRDS1, ZR900, ER650, EX400, ZX100, etc.)
        val vds = cleanVin.substring(3, 8.coerceAtMost(cleanVin.length))
        val fromVds = matchVds(vds)
        if (fromVds != null) return fromVds

        // Also check if anywhere in the VIN there is a known model pattern
        return decodeCodeOrName(cleanVin)
    }

    private fun matchVds(vds: String): String? {
        val v = vds.uppercase()
        return when {
            // Z900 variants (ZRDS1 = India Z900, ZR900B/D/F/K/S = global Z900 variants)
            v.startsWith("ZRDS") || v.startsWith("ZR900") || v.startsWith("ZR9") -> "Z900"
            v.startsWith("ZR11") -> "Z1100"
            v.startsWith("ZR10") || v.startsWith("ZR1000") -> "Z1000"
            v.startsWith("ZR800") || v.startsWith("ZR8") -> "Z800"
            v.startsWith("ZR750") || v.startsWith("ZR7") -> "Z750"
            v.startsWith("ZR650") || v.startsWith("ER650") -> "Z650"
            v.startsWith("ER500") -> "Z500"
            v.startsWith("ER400") -> "Z400"
            v.startsWith("EX400") -> "Ninja 400"
            v.startsWith("EX500") -> "Ninja 500"
            v.startsWith("EX650") -> "Ninja 650"
            v.startsWith("EX300") -> "Ninja 300"
            v.startsWith("EX250") -> "Ninja 250"
            v.startsWith("ZX10") || v.startsWith("ZXT00") || v.startsWith("ZXT02") -> "Ninja ZX-10R"
            v.startsWith("ZX6") || v.startsWith("ZX636") -> "Ninja ZX-6R"
            v.startsWith("ZX4") -> "Ninja ZX-4R"
            v.startsWith("ZX25") -> "Ninja ZX-25R"
            v.startsWith("KLE650") -> "Versys 650"
            v.startsWith("KLZ1000") || v.startsWith("KLE1100") -> "Versys 1000"
            v.startsWith("EN650") -> "Vulcan S"
            v.startsWith("EJ800") -> "W800"
            else -> null
        }
    }

    private fun decodeCodeOrName(input: String): String? {
        val s = input.uppercase().replace(Regex("[^A-Z0-9]"), "")

        return when {
            // Z900
            s.contains("Z900") || s.contains("ZR900") || s.contains("ZRDS") -> "Z900"
            // Z1100
            s.contains("Z1100") || s.contains("ZR1100") -> "Z1100"
            // Z1000
            s.contains("Z1000") || s.contains("ZR1000") -> "Z1000"
            // Z650 / Ninja 650
            s.contains("Z650") || s.contains("ER650") -> "Z650"
            s.contains("NINJA650") || s.contains("EX650") -> "Ninja 650"
            // Z500 / Ninja 500
            s.contains("Z500") || s.contains("ER500") -> "Z500"
            s.contains("NINJA500") || s.contains("EX500") -> "Ninja 500"
            // Z400 / Ninja 400
            s.contains("Z400") || s.contains("ER400") -> "Z400"
            s.contains("NINJA400") || s.contains("EX400") -> "Ninja 400"
            // ZX-10R / H2
            s.contains("ZH2") -> "Z H2"
            s.contains("ZX10R") || s.contains("ZX1000") || s.contains("ZX10") -> "Ninja ZX-10R"
            // ZX-6R
            s.contains("ZX6R") || s.contains("ZX636") || s.contains("ZX600") || s.contains("ZX6") -> "Ninja ZX-6R"
            // ZX-4R
            s.contains("ZX4R") || s.contains("ZX400") || s.contains("ZX4") -> "Ninja ZX-4R"
            // ZX-25R
            s.contains("ZX25R") || s.contains("ZX250") || s.contains("ZX25") -> "Ninja ZX-25R"
            // Versys
            s.contains("VERSYS1100") || s.contains("KLE1100") -> "Versys 1100"
            s.contains("VERSYS1000") || s.contains("KLZ1000") -> "Versys 1000"
            s.contains("VERSYS650") || s.contains("KLE650") -> "Versys 650"
            s.contains("VERSYS") -> "Versys"
            // Vulcan
            s.contains("VULCANS") || s.contains("EN650") -> "Vulcan S"
            s.contains("VULCAN") -> "Vulcan"
            // W800
            s.contains("W800") || s.contains("EJ800") -> "W800"
            // KLX
            s.contains("KLX") -> "KLX"
            else -> null
        }
    }

    private fun sanitizeFallback(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .uppercase()
    }
}
