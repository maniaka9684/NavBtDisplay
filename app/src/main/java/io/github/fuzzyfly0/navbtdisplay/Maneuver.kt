package io.github.fuzzyfly0.navbtdisplay

/** Side of a maneuver; NONE when the text does not say. */
enum class Side { NONE, LEFT, RIGHT }

/** Maneuver types we draw our own icon for. UNKNOWN falls back to the notification picture. */
enum class ManeuverType {
    STRAIGHT, TURN, SLIGHT, SHARP, KEEP, UTURN,
    ROUNDABOUT, ROUNDABOUT_EXIT, OFF_RAMP, ON_RAMP, MERGE, FERRY, DESTINATION, UNKNOWN,
}

/**
 * One classified maneuver.
 * [exit] is the roundabout exit number, if the text names it.
 * [roundaboutDirection] is the direction inside a roundabout ("take the exit on the left"), if given.
 */
data class Maneuver(
    val type: ManeuverType,
    val side: Side = Side.NONE,
    val exit: Int? = null,
    val roundaboutDirection: ManeuverType? = null,
)

/**
 * Turns the maneuver text of a navigation notification into a [Maneuver].
 *
 * Pure Kotlin (no Android classes), unit-tested on the JVM.
 * The Bulgarian wording is an ASSUMPTION until checked against real Google Maps
 * notifications from the debug log; English is kept as a fallback.
 */
object ManeuverParser {

    /** Word start/end that also work for Cyrillic (JVM \\b is ASCII-only since Java 19). */
    private const val S = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"

    private val left = Regex("наляво|вляво|отляво|${S}ляв|\\bleft\\b")
    private val right = Regex("надясно|вдясно|отдясно|${S}десн|\\bright\\b")

    private val roundabout = Regex("кръгов|\\broundabout|\\brotary|\\btraffic circle")
    private val roundaboutExit = Regex("(?:излезте|напуснете)\\s+(?:от\\s+)?кръгов|\\bexit the (?:roundabout|rotary)")
    private val uturn = Regex("обратен завой|обърнете|\\bu-?turn\\b|turn around")
    private val destination = Regex("дестинац|пристигн|${S}целта$E|${S}цел$E|\\bdestination\\b|\\barrive")
    private val ferry = Regex("ферибот|\\bferry\\b")
    private val merge = Regex("влейте|вливане|\\bmerge\\b")
    private val onRamp = Regex("рампа|качете се на|магистрала\\s|\\bon-?ramp\\b|take the ramp")
    private val offRamp = Regex("${S}изход|отбивк|слезте от|\\bexit\\b|off-?ramp")
    private val keep = Regex("дръжте|придържайте|останете|\\bkeep\\b|\\bstay\\b|разклон|\\bfork\\b")
    private val sharp = Regex("рязко|\\bsharp\\b")
    private val slight = Regex("леко|плавно|\\bslight|\\bbear\\b")
    private val straight = Regex("направо|продължете|продължавайте|\\bstraight\\b|\\bcontinue\\b|\\bhead\\b")
    private val turnWord = Regex("завийте|завой|\\bturn\\b")

    private val exitDigits = Regex("(\\d{1,2})\\s*[-–]?\\s*(?:\\p{L}{1,3})?$E\\s*(?:изход|exit)|(?:изход|exit)\\s*(?:№|no\\.?|номер)?\\s*(\\d{1,2})$E")
    private val exitWords = listOf(
        Regex("първ\\p{L}*\\s+изход|\\bfirst exit") to 1,
        Regex("втор\\p{L}*\\s+изход|\\bsecond exit") to 2,
        Regex("трет\\p{L}*\\s+изход|\\bthird exit") to 3,
        Regex("четвърт\\p{L}*\\s+изход|\\bfourth exit") to 4,
        Regex("пет(?:и|ия|ият)\\s+изход|\\bfifth exit") to 5,
        Regex("шест(?:и|ия|ият)\\s+изход|\\bsixth exit") to 6,
        Regex("седм\\p{L}*\\s+изход|\\bseventh exit") to 7,
        Regex("осм\\p{L}*\\s+изход|\\beighth exit") to 8,
    )

    fun side(text: String): Side {
        val l = left.find(text)?.range?.first
        val r = right.find(text)?.range?.first
        return when {
            l != null && (r == null || l < r) -> Side.LEFT
            r != null -> Side.RIGHT
            else -> Side.NONE
        }
    }

    fun exitNumber(text: String): Int? {
        exitDigits.find(text)?.let { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
            if (n != null && n in 1..12) return n
        }
        return exitWords.firstOrNull { it.first.containsMatchIn(text) }?.second
    }

    /**
     * [text] is the maneuver incl. street ("Завийте надясно към ул. Шипка"),
     * [iconKey] the direction key from the notification icon name, if any (see NavText.directionKey).
     */
    fun parse(text: String?, iconKey: String? = null): Maneuver {
        val t = (text ?: "").lowercase()
        val s = side(t)
        val fromText = when {
            t.isBlank() -> null
            roundaboutExit.containsMatchIn(t) -> Maneuver(ManeuverType.ROUNDABOUT_EXIT)
            roundabout.containsMatchIn(t) -> {
                val dir = when {
                    uturn.containsMatchIn(t) -> ManeuverType.UTURN
                    s == Side.NONE -> if (straight.containsMatchIn(t)) ManeuverType.STRAIGHT else null
                    sharp.containsMatchIn(t) -> ManeuverType.SHARP
                    slight.containsMatchIn(t) -> ManeuverType.SLIGHT
                    else -> ManeuverType.TURN
                }
                Maneuver(ManeuverType.ROUNDABOUT, if (dir == null) Side.NONE else s, exitNumber(t), dir)
            }
            uturn.containsMatchIn(t) -> Maneuver(ManeuverType.UTURN, Side.LEFT)
            destination.containsMatchIn(t) -> Maneuver(ManeuverType.DESTINATION, s)
            ferry.containsMatchIn(t) -> Maneuver(ManeuverType.FERRY)
            merge.containsMatchIn(t) -> Maneuver(ManeuverType.MERGE, s)
            onRamp.containsMatchIn(t) && !offRamp.containsMatchIn(t) -> Maneuver(ManeuverType.ON_RAMP, s)
            offRamp.containsMatchIn(t) -> Maneuver(ManeuverType.OFF_RAMP, s)
            keep.containsMatchIn(t) && s != Side.NONE -> Maneuver(ManeuverType.KEEP, s)
            sharp.containsMatchIn(t) && s != Side.NONE -> Maneuver(ManeuverType.SHARP, s)
            slight.containsMatchIn(t) && s != Side.NONE -> Maneuver(ManeuverType.SLIGHT, s)
            s != Side.NONE -> Maneuver(ManeuverType.TURN, s)
            straight.containsMatchIn(t) -> Maneuver(ManeuverType.STRAIGHT)
            turnWord.containsMatchIn(t) -> null
            else -> null
        }
        return fromText ?: fromIconKey(iconKey) ?: Maneuver(ManeuverType.UNKNOWN)
    }

    /** Keys of [NavText.directionKey]. */
    fun fromIconKey(key: String?): Maneuver? {
        val k = key ?: return null
        val s = when {
            k.endsWith("left") -> Side.LEFT
            k.endsWith("right") -> Side.RIGHT
            else -> Side.NONE
        }
        return when {
            k == "roundabout" -> Maneuver(ManeuverType.ROUNDABOUT)
            k == "uturn" -> Maneuver(ManeuverType.UTURN, Side.LEFT)
            k == "destination" -> Maneuver(ManeuverType.DESTINATION)
            k == "straight" -> Maneuver(ManeuverType.STRAIGHT)
            k.startsWith("exit") -> Maneuver(ManeuverType.OFF_RAMP, s)
            k.startsWith("sharp") -> Maneuver(ManeuverType.SHARP, s)
            k.startsWith("slight") -> Maneuver(ManeuverType.SLIGHT, s)
            k.startsWith("keep") -> Maneuver(ManeuverType.KEEP, s)
            s != Side.NONE -> Maneuver(ManeuverType.TURN, s)
            else -> null
        }
    }

    /** Short Bulgarian label for the car display; null for UNKNOWN (the original text is used). */
    fun label(m: Maneuver): String? {
        fun lr(l: String, r: String, none: String) = when (m.side) {
            Side.LEFT -> l
            Side.RIGHT -> r
            Side.NONE -> none
        }
        return when (m.type) {
            ManeuverType.STRAIGHT -> "Направо"
            ManeuverType.TURN -> lr("Наляво", "Надясно", "Завийте")
            ManeuverType.SLIGHT -> lr("Леко наляво", "Леко надясно", "Леко завийте")
            ManeuverType.SHARP -> lr("Рязко наляво", "Рязко надясно", "Рязък завой")
            ManeuverType.KEEP -> lr("Дръжте ляво", "Дръжте дясно", "Дръжте лентата")
            ManeuverType.UTURN -> "Обратен завой"
            ManeuverType.ROUNDABOUT -> m.exit?.let { "Кръгово – изход $it" } ?: "Кръгово"
            ManeuverType.ROUNDABOUT_EXIT -> "Излезте от кръговото"
            ManeuverType.OFF_RAMP -> lr("Изход вляво", "Изход вдясно", "Изход")
            ManeuverType.ON_RAMP -> "Към магистралата"
            ManeuverType.MERGE -> "Вливане"
            ManeuverType.FERRY -> "Ферибот"
            ManeuverType.DESTINATION -> lr("Целта е вляво", "Целта е вдясно", "Пристигане")
            ManeuverType.UNKNOWN -> null
        }
    }

    private val distanceRe = Regex("(\\d+(?:[.,]\\d+)?)\\s?(km|км|mi|m|м|ft|yd)$E", RegexOption.IGNORE_CASE)

    /** "300 м" → 300, "1,2 км" → 1200, "0.5 mi" → 805; null if no distance. */
    fun meters(distance: String?): Int? {
        val m = distanceRe.find(distance ?: return null) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val f = when (m.groupValues[2].lowercase()) {
            "km", "км" -> 1000.0
            "mi" -> 1609.34
            "ft" -> 0.3048
            "yd" -> 0.9144
            else -> 1.0
        }
        return Math.round(v * f).toInt()
    }
}
