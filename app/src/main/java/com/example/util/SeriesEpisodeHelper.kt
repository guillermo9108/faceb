package com.example.util

import com.example.model.Post

data class SeriesEpisodeKey(
    val hasEpisode: Boolean,
    val season: Int,
    val episode: Int,
    val subPart: Int,
    val naturalTitle: String,
    val hasExplicitSeason: Boolean = false
)

/**
 * Parser inteligente para organización de series, telenovelas, anime y cursos por capítulos/episodios.
 * Soporta formatos heterogéneos mezclados dentro de la misma carpeta del NAS:
 * - Prefijos de capítulo/episodio: "cap 01", "Cap. 004", "Capítulo 12", "Ep 05", "E09", "#12"
 * - Temporada + episodio: "t1x05", "S02E14", "1x05", "Temporada 2 Capitulo 3", "Cap 04 (Temporada 2)"
 * - Números sueltos con ceros variables: "02", "3", "004", "0006", "7", "Novela - 012 [1080p x264]"
 * - Partes de episodio: "Cap 12 Parte 2", "Cap 12.2"
 */
object SeriesEpisodeHelper {

    // Nivel 1A: t1x05, S01E05, T1 E05, t02.cap03, S1-E05
    private val REGEX_T_OR_S_WITH_EP = Regex(
        """(?<![a-zA-Z0-9])[tTsS]\s*0*(\d{1,3})\s*[xXeE.\-_ ]+\s*(?:[cCeE](?:ap(?:[ií]tulo)?|h(?:apter)?|p(?:isodio|isode)?)?[.\-_ ]*)?0*(\d{1,5})(?:\.(\d{1,2})(?![0-9]))?(?![a-zA-Z0-9])"""
    )

    // Nivel 1B: 1x05, 02x12, 1×104 (se descarta 1920x1080 / 1280x720 por límite \d{1,2})
    private val REGEX_NUM_X_NUM = Regex(
        """(?<![a-zA-Z0-9])0*(\d{1,2})\s*[xX×]\s*0*(\d{1,5})(?:\.(\d{1,2})(?![0-9]))?(?![a-zA-Z0-9])"""
    )

    // Nivel 1C: Temporada 2 - Cap 05 / Season 1 Episode 4
    private val REGEX_TEMPORADA_EP = Regex(
        """(?:temporada|temp\.?|season)\s*0*(\d{1,3})[\s\-–—_.,|/]+(?:cap(?:[ií]tulo|s)?\.?|ep(?:isodio|isode|s)?\.?|ch(?:apter)?\.?|#)?\s*0*(\d{1,5})(?:\.(\d{1,2})(?![0-9]))?\b""",
        RegexOption.IGNORE_CASE
    )

    // Temporada suelta (ej. en nombre o en subcarpeta "(Temporada 2)", "(T2)", "Temp 1")
    private val REGEX_SEASON_WORD = Regex(
        """(?:temporada|temp\.?|season)\s*0*(\d{1,3})\b""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_SEASON_SHORT = Regex(
        """(?:^|[\s(\[/_\-])(?:t|s)0*(\d{1,2})(?=[\s)\]/_\-]|$)""",
        RegexOption.IGNORE_CASE
    )

    // Sub-parte explícita (Parte 2, Pt. 2, Part 2)
    private val REGEX_SUB_PART = Regex(
        """\b(?:parte?|pt)\.?\s*0*(\d{1,2})\b""",
        RegexOption.IGNORE_CASE
    )

    // Nivel 2: Palabra clave de capítulo / episodio
    private val REGEX_EP_KEYWORD = Regex(
        """(?<![a-zA-Z])(?:cap(?:[ií]tulo|itulos|s)?|ep(?:isodio|isode|s)?|ch(?:apter)?|epis|folge)\.?\s*[#:\-_]*\s*0*(\d{1,5})(?:\.(\d{1,2})(?![0-9]))?(?![a-zA-Z0-9])""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_SHORT_EP_TAG = Regex(
        """(?:^|[\s._\-\(\[])[eE][pP]?0*(\d{1,5})(?:\.(\d{1,2}))?(?=[\s._\-\)\]]|$)"""
    )
    private val REGEX_HASH_EP = Regex("""#\s*0*(\d{1,5})\b""")

    // Ruido técnico habitual en ripeos de series y novelas que no debe confundirse con capítulo
    private val REGEX_TECH_NOISE = Regex(
        """\b(?:\d{3,4}[pPiI]|[xh]\.?26[45]|10bit|8bit|24fps|25fps|30fps|50fps|60fps|4k|8k|hd|fhd|uhd|hdr|sdr|aac|ac3|eac3|dts|hevc|av1|xvid|divx|dvdrip|bdrip|brrip|webrip|web[\-_ ]?dl|hdtv|mp4|mkv|avi|mov|latino|castellano|espa[nñ]ol|sub(?:titulado)?|5\.1|7\.1)\b""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_RELEASE_YEAR = Regex("""(?:^|[\s(\[_\-])(19[5-9]\d|20[0-3]\d)(?=[\s)\]._\-]|$)""")
    private val REGEX_PURE_DIGITS = Regex("""^\s*0*(\d{1,6})\s*$""")
    private val REGEX_LEADING_NUMBER = Regex("""^\s*0*(\d{1,5})(?:\.(\d{1,2}))?(?=$|[\s._\-\(\[])""")
    private val REGEX_ANY_NUMBER = Regex("""(?<!\d)0*(\d{1,5})(?!\d)""")
    private val REGEX_RES_PAIR = Regex("""\b(?:1920x1080|1280x720|3840x2160|720x480|640x480|16x9|4x3)\b""", RegexOption.IGNORE_CASE)

    fun parseEpisodeKey(rawTitle: String, fallbackPath: String? = null): SeriesEpisodeKey {
        val cleanTitle = rawTitle.trim()
        val pathStem = fallbackPath
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.substringBeforeLast('.')
            ?.trim()
            .orEmpty()

        val primarySource = cleanTitle.ifBlank { pathStem }
        val parsedPrimary = parseSingleSource(primarySource)
        if (parsedPrimary.hasEpisode || pathStem.isBlank() || cleanTitle == pathStem) {
            return parsedPrimary
        }

        // Si el texto de la publicación es descriptivo sin números (ej. "Gran estreno"), intentar con el nombre del archivo
        val parsedFromPath = parseSingleSource(pathStem)
        return if (parsedFromPath.hasEpisode) {
            parsedFromPath.copy(naturalTitle = primarySource)
        } else {
            parsedPrimary
        }
    }

    private fun parseSingleSource(source: String): SeriesEpisodeKey {
        if (source.isBlank()) {
            return SeriesEpisodeKey(
                hasEpisode = false,
                season = 1,
                episode = Int.MAX_VALUE,
                subPart = 0,
                naturalTitle = ""
            )
        }

        // Caso especial directo: título compuesto únicamente por dígitos ("02", "3", "004", "0006", "7")
        REGEX_PURE_DIGITS.matchEntire(source)?.let { pure ->
            val ep = pure.groupValues[1].toIntOrNull()
            if (ep != null) {
                return SeriesEpisodeKey(
                    hasEpisode = true,
                    season = 1,
                    episode = ep,
                    subPart = 0,
                    naturalTitle = source,
                    hasExplicitSeason = false
                )
            }
        }

        val sanitizedForCombined = REGEX_RES_PAIR.replace(source, " ")

        // Nivel 1: Temporada + Episodio combinados (t1x05, S01E05, 1x05, Temporada 1 Cap 05)
        val combinedMatch = REGEX_T_OR_S_WITH_EP.find(sanitizedForCombined)
            ?: REGEX_NUM_X_NUM.find(sanitizedForCombined)
            ?: REGEX_TEMPORADA_EP.find(sanitizedForCombined)

        if (combinedMatch != null) {
            val seasonNum = combinedMatch.groupValues[1].toIntOrNull() ?: 1
            val epNum = combinedMatch.groupValues[2].toIntOrNull() ?: 0
            val dotSub = combinedMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
            val subPart = if (dotSub > 0) dotSub else extractSubPart(source, combinedMatch.range)
            return SeriesEpisodeKey(
                hasEpisode = true,
                season = seasonNum,
                episode = epNum,
                subPart = subPart,
                naturalTitle = source,
                hasExplicitSeason = true
            )
        }

        // Detectar etiqueta de temporada independiente (ej. "Cap 05 (Temporada 2)" o "Temp 2 / 05")
        var detectedSeason: Int? = null
        var textWithoutSeason = sanitizedForCombined
        val seasonWordMatch = REGEX_SEASON_WORD.find(textWithoutSeason)
        if (seasonWordMatch != null) {
            detectedSeason = seasonWordMatch.groupValues[1].toIntOrNull()
            textWithoutSeason = textWithoutSeason.removeRange(seasonWordMatch.range)
        } else {
            val seasonShortMatch = REGEX_SEASON_SHORT.find(textWithoutSeason)
            if (seasonShortMatch != null) {
                detectedSeason = seasonShortMatch.groupValues[1].toIntOrNull()
                textWithoutSeason = textWithoutSeason.removeRange(seasonShortMatch.range)
            }
        }

        val effectiveSeason = detectedSeason ?: 1
        val hasExplicitSeason = detectedSeason != null

        // Nivel 2: Palabra clave de episodio ("cap 01", "Cap. 004", "Ep 05", "#12")
        val kwMatch = REGEX_EP_KEYWORD.find(textWithoutSeason)
            ?: REGEX_SHORT_EP_TAG.find(textWithoutSeason)
            ?: REGEX_HASH_EP.find(textWithoutSeason)

        if (kwMatch != null) {
            val epNum = kwMatch.groupValues[1].toIntOrNull()
            if (epNum != null) {
                val dotSub = kwMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val subPart = if (dotSub > 0) dotSub else extractSubPart(textWithoutSeason, kwMatch.range)
                return SeriesEpisodeKey(
                    hasEpisode = true,
                    season = effectiveSeason,
                    episode = epNum,
                    subPart = subPart,
                    naturalTitle = source,
                    hasExplicitSeason = hasExplicitSeason
                )
            }
        }

        // Nivel 2B: Si dice "Parte 2" / "Pt 03" como único identificador numérico principal
        val partAsEpisode = REGEX_SUB_PART.find(textWithoutSeason)
        val cleanedForBare = stripTechnicalNoise(textWithoutSeason)

        // Nivel 3: Números sueltos o delimitados ("02", "3", "004", "0006", "7", "Novela - 004")
        val leadingMatch = REGEX_LEADING_NUMBER.find(cleanedForBare)
        if (leadingMatch != null) {
            val epNum = leadingMatch.groupValues[1].toIntOrNull()
            if (epNum != null) {
                val dotSub = leadingMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val subPart = if (dotSub > 0) dotSub else extractSubPart(cleanedForBare, leadingMatch.range)
                return SeriesEpisodeKey(
                    hasEpisode = true,
                    season = effectiveSeason,
                    episode = epNum,
                    subPart = subPart,
                    naturalTitle = source,
                    hasExplicitSeason = hasExplicitSeason
                )
            }
        }

        // Si había "Parte N" y no hay otro número antes, usar Parte N como episodio
        val allNumbers = REGEX_ANY_NUMBER.findAll(cleanedForBare).toList()
        if (allNumbers.isNotEmpty()) {
            // Excluir el número de "Parte N" si existe otro número principal antes
            val chosenMatch = if (partAsEpisode != null && allNumbers.size >= 2) {
                val beforePart = allNumbers.filter { it.range.first < partAsEpisode.range.first }
                beforePart.lastOrNull() ?: allNumbers.last()
            } else {
                allNumbers.last()
            }
            val epNum = chosenMatch.groupValues[1].toIntOrNull()
            if (epNum != null) {
                val subPart = if (partAsEpisode != null && chosenMatch.range.first != partAsEpisode.range.first) {
                    partAsEpisode.groupValues[1].toIntOrNull() ?: 0
                } else 0
                return SeriesEpisodeKey(
                    hasEpisode = true,
                    season = effectiveSeason,
                    episode = epNum,
                    subPart = subPart,
                    naturalTitle = source,
                    hasExplicitSeason = hasExplicitSeason
                )
            }
        }

        return SeriesEpisodeKey(
            hasEpisode = false,
            season = effectiveSeason,
            episode = Int.MAX_VALUE,
            subPart = 0,
            naturalTitle = source,
            hasExplicitSeason = hasExplicitSeason
        )
    }

    private fun extractSubPart(text: String, mainMatchRange: IntRange): Int {
        val m = REGEX_SUB_PART.find(text) ?: return 0
        if (m.range.first <= mainMatchRange.last && m.range.last >= mainMatchRange.first) return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    private fun stripTechnicalNoise(input: String): String {
        var cleaned = REGEX_TECH_NOISE.replace(input, " ")
        cleaned = REGEX_RELEASE_YEAR.replace(cleaned) { mr ->
            // Mantener números con ceros a la izquierda aunque tengan 4 dígitos (ej. 0006); aquí solo matchea 1950..2039
            val rawGroup = mr.groupValues[1]
            if (rawGroup.startsWith("0")) mr.value else " "
        }
        return cleaned.trim()
    }

    fun seriesComparator(): Comparator<Post> = Comparator { a, b -> compareSeriesPosts(a, b) }

    fun compareSeriesPosts(a: Post, b: Post): Int {
        if (a.isPinned != b.isPinned) {
            return if (a.isPinned) -1 else 1
        }
        val ka = parseEpisodeKey(a.contentText, a.mediaItems.firstOrNull()?.url)
        val kb = parseEpisodeKey(b.contentText, b.mediaItems.firstOrNull()?.url)

        if (ka.hasEpisode != kb.hasEpisode) {
            return if (ka.hasEpisode) -1 else 1
        }
        if (ka.season != kb.season) {
            return ka.season.compareTo(kb.season)
        }
        if (ka.episode != kb.episode) {
            return ka.episode.compareTo(kb.episode)
        }
        if (ka.subPart != kb.subPart) {
            return ka.subPart.compareTo(kb.subPart)
        }
        val natCmp = compareNatural(ka.naturalTitle, kb.naturalTitle)
        if (natCmp != 0) return natCmp
        val timeCmp = a.createdAt.compareTo(b.createdAt)
        if (timeCmp != 0) return timeCmp
        return a.id.compareTo(b.id)
    }

    fun formatEpisodeBadge(post: Post, fallbackIndex: Int): String {
        val key = parseEpisodeKey(post.contentText, post.mediaItems.firstOrNull()?.url)
        if (!key.hasEpisode) return "Episodio ${fallbackIndex + 1}"
        val partSuffix = if (key.subPart > 0) " · Pt. ${key.subPart}" else ""
        return if (key.hasExplicitSeason) {
            "Temp. ${key.season} · Ep. ${key.episode}$partSuffix"
        } else {
            "Cap. ${key.episode}$partSuffix"
        }
    }

    fun compareNatural(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        while (ia < a.length && ib < b.length) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = ia
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = ib
                while (endB < b.length && b[endB].isDigit()) endB++

                val numA = a.substring(ia, endA).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(ib, endB).trimStart('0').ifEmpty { "0" }
                if (numA.length != numB.length) {
                    return numA.length.compareTo(numB.length)
                }
                val numCmp = numA.compareTo(numB)
                if (numCmp != 0) return numCmp
                ia = endA
                ib = endB
            } else {
                val charCmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (charCmp != 0) return charCmp
                ia++
                ib++
            }
        }
        return a.length.compareTo(b.length)
    }
}
