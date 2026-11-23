package com.kalaiselvan.ride.notifications

import java.util.Locale
import java.util.regex.Pattern

/**
 * Universal text and distance parser for turn-by-turn navigation notifications (Google Maps, Waze, HERE).
 *
 * Supports English, Major Indian Languages (Hindi, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali, Gujarati, Punjabi)
 * and Major International Languages (Spanish, French, German, Italian, Portuguese, Japanese, Chinese, Russian, Arabic).
 *
 * Extracts metric and imperial countdown distances and maps maneuver instructions
 * deterministically to motorcycle TFT cluster turn symbol IDs (0..55).
 */
object NavNotificationParser {

    // Regex to match metric and imperial distances across scripts and languages
    private val DISTANCE_REGEX = Pattern.compile(
        """(\d+(?:[.,]\d+)?)\s*(km|kms|kilometer|kilometers|kilometre|kilometres|mi|mile|miles|ft|feet|foot|yd|yds|yard|yards|m|meter|meters|metre|metres|किमी|कि\.मी\.|मीटर|मी\.|मी|கி\.மீ|மீட்டர்|மீ|కి\.మీ|మీటర్లు|కిమీ|ಕಿ\.ಮೀ|ಮೀಟರ್|കിലോമീറ്റർ|മീറ്റർ|কিলোমিটার|মিটার|કિમી|કિ\.મી\.|મીટર|км|м|quilômetros|metros|quilometros|kilometros|millas|milhas|pies|pés|mètres|kilomètres|milles|metri|chilometri|miglia|meilen|fuß|公里|千米|米|キロ|メートル|كم|متر|ميل|قدم)(?:(?<=[a-zA-Z0-9])\b|(?=[^a-zA-Z0-9])|$)""",
        Pattern.CASE_INSENSITIVE
    )

    private val ROUNDABOUT_EXIT_REGEX = Pattern.compile(
        """(?:take\s+the\s+|tome\s+la\s+|prenez\s+la\s+|nehmen\s+sie\s+die\s+|prendi\s+la\s+|pegue\s+a\s+)?(\d+)(?:st|nd|rd|th|ª|e|ème|\.ª|\.|\b)?\s*(?:exit|salida|sortie|ausfahrt|uscita|saída|निकास|வெளியேறு|నిష్క్రమణ|ನಿರ್ಗಮನ)""",
        Pattern.CASE_INSENSITIVE
    )

    private val ROUNDABOUT_EXIT_ALT_REGEX = Pattern.compile(
        """(?:exit|salida|sortie|ausfahrt|uscita|saída|निकास)\s+(\d+)""",
        Pattern.CASE_INSENSITIVE
    )

    private val GENERIC_DIGIT_REGEX = Pattern.compile("""\b([1-9]|1[0-2])\b""")

    // Multilingual Directional Lexicons
    private val RIGHT_KEYWORDS = listOf(
        // Indian languages
        "दाएं", "दाहिने", "दायां", "दहिने", "उजवी", "उजवे", // Hindi, Marathi
        "வலது", "வலப்புறம்", "வலப்பக்கம்", // Tamil
        "కుడి", // Telugu
        "ಬಲ", // Kannada
        "വലത്", "വലത്തോ", "വലത്തേക്ക്", // Malayalam
        "ডান", // Bengali
        "જમણે", "જમણી", // Gujarati
        "ਸੱਜੇ", "ਸੱਜਾ", // Punjabi
        "ଡାହାଣ", // Odia
        // International languages
        "derecha", // Spanish
        "droite", // French
        "rechts", // German
        "destra", // Italian
        "direita", // Portuguese
        "右", // Japanese, Chinese
        "прав", // Russian (направо, вправо, правее)
        "يمين", // Arabic
        "sağ", // Turkish
        "kanan", // Indonesian/Malay
        "ขวา", // Thai
        "오른쪽", "우회전", // Korean
        "phải" // Vietnamese
    )

    private val LEFT_KEYWORDS = listOf(
        // Indian languages
        "बाएं", "बायें", "बायां", "डावी", "डावे", // Hindi, Marathi
        "இடது", "இடப்புறம்", "இடப்பக்கம்", // Tamil
        "ఎడమ", // Telugu
        "ಎಡ", // Kannada
        "ഇടത്", "ഇടത്തോ", "ഇടത്തേക്ക്", // Malayalam
        "বাম", "বাঁ", // Bengali
        "ડાબે", "ડાબી", // Gujarati
        "ਖੱਬੇ", "ਖੱਬਾ", // Punjabi
        "ବାମ", // Odia
        // International languages
        "izquierda", // Spanish
        "gauche", // French
        "links", // German
        "sinistra", // Italian
        "esquerda", // Portuguese
        "左", // Japanese, Chinese
        "лев", // Russian (налево, влево, левее)
        "يسار", "شمال", // Arabic
        "sol", // Turkish
        "kiri", // Indonesian/Malay
        "ซ้าย", // Thai
        "왼쪽", "좌회전", // Korean
        "trái" // Vietnamese
    )

    private val U_TURN_KEYWORDS = listOf(
        "u-turn", "uturn", "u turn",
        "यू-टर्न", "यू टर्न", "यु-टर्न",
        "வாபஸ்", "யு-டர்ன்", "యు-టర్న్", "ಯು-ಟರ್ನ್", "യു-ടേൺ", "ইউ-টার্ন",
        "cambio de sentido", "media vuelta",
        "demi-tour",
        "wenden", "kehrtwende",
        "inversione",
        "retorno", "meia-volta",
        "転回", "uターン",
        "掉头", "迴轉", "回转",
        "разворот",
        "استدارة", "دوران للخلف"
    )

    private val SHARP_KEYWORDS = listOf(
        "sharp", "hairpin",
        "तीव्र", "तेज", "कठिन",
        "கடுமையான", "செங்குத்தான",
        "తీవ్ర", "తీక్షణ",
        "ತೀಕ್ಷ್ಣ", "ತೀವ್ರ",
        "തീവ്ര",
        "খাড়া",
        "તીવ્ર",
        "ਤਿੱਖਾ",
        "cerrada", "cerrado", "pronunciada", "pronunciado", "horquilla",
        "serré", "serrée", "en épingle",
        "scharf", "spitz",
        "stretta", "stretto", "secca",
        "fechada", "fechado", "acentuada",
        "急カーブ", "急な",
        "急转", "急轉",
        "крутой", "резкий",
        "حادة", "منعطف حاد"
    )

    private val SLIGHT_KEYWORDS = listOf(
        "slight", "bear", "lean",
        "हल्का", "थोड़ा",
        "லேசான",
        "కొద్దిగా", "స్వల్ప",
        "ಸ್ವಲ್ಪ",
        "നേരിയ",
        "সামান্য",
        "સહેજ",
        "ਹਲਕਾ",
        "ligera", "ligeramente", "leve", "suave",
        "légèrement", "léger", "légère",
        "leicht", "halb",
        "leggermente", "poco",
        "ligeiramente", "suavemente",
        "斜め", "少し",
        "稍向", "偏向", "稍微",
        "плавно", "небольшой",
        "طفيف", "قليلاً"
    )

    private val ROUNDABOUT_KEYWORDS = listOf(
        "roundabout", "traffic circle", "rotary",
        "गोलचक्कर", "चौक",
        "ரோட்டரி", "சுற்றுவட்டம்",
        "రౌండ్‌అబౌట్",
        "ವೃತ್ತ",
        "റൗണ്ട്എബൗട്ട്",
        "গোলচত্বর",
        "ગોળચક્કર",
        "ਚੌਂਕ",
        "rotonda", "glorieta",
        "rond-point", "carrefour giratoire", "giratoire",
        "kreisverkehr",
        "rotatoria",
        "rotatória", "rotunda",
        "ラウンドアバウト", "環状交差点", "ロータリー",
        "环岛", "環島", "圆环", "圓環",
        "круговое", "кольцо",
        "دوار", "ميدان",
        "dönel kavşak"
    )

    private val ARRIVAL_KEYWORDS = listOf(
        "destination", "you have arrived", "you've arrived", "arrived at", "destination reached", "at your destination", "approaching destination",
        "गंतव्य", "पहुंच गए", "पहुँच गए", "मंजिल",
        "சேருமிடம்", "சென்றடைந்தீர்கள்", "வந்தடைந்தீர்கள்", "இலக்கு",
        "గమ్యం", "చేరుకున్నారు", "లక్ష్యం",
        "ಗಮ್ಯಸ್ಥಾನ", "ತಲುಪಿದ್ದೀರಿ",
        "ലക്ഷ്യസ്ഥാനം", "എത്തിച്ചേർന്നു",
        "গন্তব্য", "পৌঁছেছেন",
        "ગંતવ્ય", "પહોંચી ગયા",
        "ਮੰਜ਼ਿਲ", "ਪਹੁੰਚ ਗਏ",
        "destino", "ha llegado", "has llegado", "llegada",
        "vous êtes arrivé", "arrivée",
        "ziel", "ziel erreicht", "angekommen",
        "destinazione", "sei arrivato",
        "você chegou", "chegada",
        "目的地", "到着",
        "已到达", "已抵達",
        "пункт назначения", "вы прибыли",
        "وجهتك", "وصلت"
    )

    private val STRAIGHT_KEYWORDS = listOf(
        "straight", "continue", "head ",
        "सीधा", "सीधे", "सरळ",
        "நேராக", "நேரே",
        "నేరుగా",
        "ನೇರವಾಗಿ",
        "നേരെ",
        "সোজা",
        "સીધા",
        "ਸਿੱਧਾ",
        "recto", "continúa", "sigue",
        "tout droit", "continuez",
        "geradeaus", "weiter",
        "dritto", "continua",
        "em frente",
        "直進", "まっすぐ",
        "直行", "直走",
        "прямо",
        "إلى الأمام", "مباشرة"
    )

    /**
     * Converts native Indic, Arabic and localized digits into standard Western ASCII digits ('0'..'9').
     */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '\u0966'..'\u096F' -> sb.append((ch - '\u0966' + '0'.code).toChar()) // Devanagari ०-९
                in '\u09E6'..'\u09EF' -> sb.append((ch - '\u09E6' + '0'.code).toChar()) // Bengali ০-৯
                in '\u0A66'..'\u0A6F' -> sb.append((ch - '\u0A66' + '0'.code).toChar()) // Gurmukhi ੦-੯
                in '\u0AE6'..'\u0AEF' -> sb.append((ch - '\u0AE6' + '0'.code).toChar()) // Gujarati ૦-૯
                in '\u0B66'..'\u0B6F' -> sb.append((ch - '\u0B66' + '0'.code).toChar()) // Odia ୦-୯
                in '\u0BE6'..'\u0BEF' -> sb.append((ch - '\u0BE6' + '0'.code).toChar()) // Tamil ௦-௯
                in '\u0C66'..'\u0C6F' -> sb.append((ch - '\u0C66' + '0'.code).toChar()) // Telugu ౦-౯
                in '\u0CE6'..'\u0CEF' -> sb.append((ch - '\u0CE6' + '0'.code).toChar()) // Kannada ೦-೯
                in '\u0D66'..'\u0D6F' -> sb.append((ch - '\u0D66' + '0'.code).toChar()) // Malayalam ൦-൯
                in '\u0660'..'\u0669' -> sb.append((ch - '\u0660' + '0'.code).toChar()) // Arabic ٠-٩
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Parses numeric distance and converts to meters across multiple languages and numeral scripts.
     * Returns Pair(distanceInMeters, unitString). If no distance is found, returns Pair(0, "").
     */
    fun parseDistance(text: String): Pair<Int, String> {
        if (text.isBlank()) return Pair(0, "")
        val normalized = normalizeDigits(text)
        val matcher = DISTANCE_REGEX.matcher(normalized)
        if (!matcher.find()) return Pair(0, "")

        val numRaw = matcher.group(1)?.replace(',', '.') ?: return Pair(0, "")
        val unitRaw = matcher.group(2)?.lowercase(Locale.ROOT) ?: ""
        val value = numRaw.toDoubleOrNull() ?: return Pair(0, "")

        val meters = when {
            unitRaw.startsWith("km") || unitRaw.startsWith("kilo") || unitRaw.contains("किमी") || unitRaw.contains("कि.मी") ||
            unitRaw.contains("கி.மீ") || unitRaw.contains("కి.మీ") || unitRaw.contains("ಕಿ.ಮೀ") || unitRaw.contains("കിലോ") ||
            unitRaw.contains("কিলো") || unitRaw.contains("કિમી") || unitRaw.contains("км") || unitRaw.contains("quilôm") ||
            unitRaw.contains("kilomet") || unitRaw.contains("kilomèt") || unitRaw.contains("chilometr") || unitRaw.contains("公里") ||
            unitRaw.contains("千米") || unitRaw.contains("キロ") || unitRaw.contains("كم") -> (value * 1000.0).toInt()

            unitRaw.startsWith("mi") || unitRaw.contains("mile") || unitRaw.contains("millas") || unitRaw.contains("milhas") ||
            unitRaw.contains("milles") || unitRaw.contains("miglia") || unitRaw.contains("meilen") || unitRaw.contains("миль") ||
            unitRaw.contains("ميل") -> (value * 1609.344).toInt()

            unitRaw.startsWith("ft") || unitRaw.startsWith("fee") || unitRaw.startsWith("foo") || unitRaw.contains("pies") ||
            unitRaw.contains("pés") || unitRaw.contains("pieds") || unitRaw.contains("piedi") || unitRaw.contains("fuß") ||
            unitRaw.contains("фут") || unitRaw.contains("قدم") -> (value * 0.3048).toInt()

            unitRaw.startsWith("yd") || unitRaw.startsWith("yar") -> (value * 0.9144).toInt()

            else -> value.toInt() // standard meters
        }

        return Pair(meters.coerceAtLeast(0), unitRaw)
    }

    /**
     * Returns true if instruction contains an explicit roundabout exit number.
     */
    fun hasRoundaboutExitNumber(instruction: String): Boolean {
        val clean = normalizeDigits(instruction.lowercase(Locale.ROOT))
        if (!ROUNDABOUT_KEYWORDS.any { clean.contains(it) }) {
            return false
        }
        return ROUNDABOUT_EXIT_REGEX.matcher(clean).find() ||
               ROUNDABOUT_EXIT_ALT_REGEX.matcher(clean).find() ||
               GENERIC_DIGIT_REGEX.matcher(clean).find()
    }

    fun isRight(clean: String): Boolean {
        val englishMatch = Pattern.compile("""\bright\b""", Pattern.CASE_INSENSITIVE).matcher(clean).find()
        return englishMatch || RIGHT_KEYWORDS.any { clean.contains(it) }
    }

    fun isLeft(clean: String): Boolean {
        val englishMatch = Pattern.compile("""\bleft\b""", Pattern.CASE_INSENSITIVE).matcher(clean).find()
        return englishMatch || LEFT_KEYWORDS.any { clean.contains(it) }
    }

    /**
     * Maps turn instructions across multiple languages to motorcycle cluster TFT turn IDs (0..55).
     *
     * @param instruction The raw navigation text (e.g. "In 200m, turn sharp left", "दाएं मुड़ें", "Gire a la derecha")
     * @param leftHandTraffic True for left-hand drive countries (India, UK, Japan, Australia: CW roundabouts 18..29);
     *                        False for right-hand drive countries (US, EU: CCW roundabouts 30..41).
     */
    fun parseManeuver(instruction: String, leftHandTraffic: Boolean = true): Int {
        if (instruction.isBlank()) return 0
        val clean = normalizeDigits(instruction.lowercase(Locale.ROOT))

        // 1. Destination / Arrival (ID 43)
        if (ARRIVAL_KEYWORDS.any { clean.contains(it) }) {
            return 43
        }

        // 2. Roundabouts (CW: 18..29, CCW: 30..41)
        if (ROUNDABOUT_KEYWORDS.any { clean.contains(it) }) {
            var exitNum = 1
            val m1 = ROUNDABOUT_EXIT_REGEX.matcher(clean)
            if (m1.find()) {
                exitNum = m1.group(1)?.toIntOrNull() ?: 1
            } else {
                val m2 = ROUNDABOUT_EXIT_ALT_REGEX.matcher(clean)
                if (m2.find()) {
                    exitNum = m2.group(1)?.toIntOrNull() ?: 1
                } else {
                    val m3 = GENERIC_DIGIT_REGEX.matcher(clean)
                    if (m3.find()) {
                        exitNum = m3.group(1)?.toIntOrNull() ?: 1
                    }
                }
            }
            val safeExit = exitNum.coerceIn(1, 12)
            return if (leftHandTraffic) 17 + safeExit else 29 + safeExit
        }

        val hasRight = isRight(clean)
        val hasLeft = isLeft(clean)

        // 3. U-Turns (ID 7 = Left, ID 8 = Right)
        if (U_TURN_KEYWORDS.any { clean.contains(it) }) {
            return if (hasRight && !hasLeft) 8 else 7
        }

        // 4. Sharp Turns (ID 3 = Sharp Left, ID 4 = Sharp Right)
        if (SHARP_KEYWORDS.any { clean.contains(it) }) {
            return if (hasRight && !hasLeft) 4 else 3
        }

        // 5. Slight Turns (ID 5 = Slight Left, ID 6 = Slight Right)
        if (SLIGHT_KEYWORDS.any { clean.contains(it) }) {
            return if (hasRight && !hasLeft) 6 else 5
        }

        // 6. Highway On-Ramps (ID 12 = On-ramp Left, ID 13 = On-ramp Right)
        if (clean.contains("on ramp") || clean.contains("on-ramp") || clean.contains("enter freeway") ||
            clean.contains("enter highway") || clean.contains("incorporación") || clean.contains("einfädeln")
        ) {
            return if (hasRight && !hasLeft) 13 else 12
        }

        // 7. Highway Off-Ramps & Exits (ID 14 = Off-ramp Left, ID 15 = Off-ramp Right)
        if (clean.contains("off ramp") || clean.contains("off-ramp") || clean.contains("motorway exit") ||
            clean.contains("salida") || clean.contains("sortie") || clean.contains("ausfahrt") ||
            clean.contains("uscita") || clean.contains("saída") || clean.contains("निकास") ||
            (clean.contains("exit") && !ROUNDABOUT_KEYWORDS.any { clean.contains(it) })
        ) {
            return if (hasRight && !hasLeft) 15 else 14
        }

        // 8. Highway Merges (ID 16 = Merge Left, ID 17 = Merge Right)
        if (clean.contains("merge") || clean.contains("fusionar") || clean.contains("merger") ||
            clean.contains("zusammenführen") || clean.contains("विलय")
        ) {
            return if (hasRight && !hasLeft) 17 else 16
        }

        // 9. Forks & Lane Keep (ID 9 = Fork Left, ID 10 = Fork Right, ID 11 = Middle)
        if (clean.contains("fork") || clean.contains("keep") || clean.contains("mantente") ||
            clean.contains("serrez") || clean.contains("halten") || clean.contains("बने रहें") ||
            clean.contains("தொடரவும்")
        ) {
            return when {
                clean.contains("middle") || clean.contains("center") || clean.contains("मध्य") -> 11
                hasRight && !hasLeft -> 10
                hasLeft && !hasRight -> 9
                else -> 0
            }
        }

        // 10. Standard 90° Turns (ID 1 = Normal Left, ID 2 = Normal Right)
        if (hasLeft && !hasRight) return 1
        if (hasRight && !hasLeft) return 2

        // 11. Straight & Head (Departure 42 is remapped to 0 for motorcycle cluster compatibility)
        if (STRAIGHT_KEYWORDS.any { clean.contains(it) }) {
            return 0
        }

        return 0
    }
}
