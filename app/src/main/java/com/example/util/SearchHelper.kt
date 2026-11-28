package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.Post
import com.example.model.SearchSuggestion
import com.example.model.SearchSuggestionType
import java.text.Normalizer

/**
 * Motor de búsqueda inteligente independiente del orden de palabras y tolerante a tildes/mayúsculas,
 * con equivalencia numérica para capítulos (ej. "cap 1" coincide con "cap 01" o "t1x01")
 * y resaltado visual de coincidencias.
 */
object SearchHelper {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val TOKEN_SPLIT_REGEX = Regex("[\\s,.;:!?/\\\\|_\\-]+")
    private val STOP_WORDS = setOf(
        "el", "la", "los", "las", "de", "del", "en", "y", "e", "o", "u",
        "con", "por", "para", "un", "una", "unos", "unas", "al", "a"
    )

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenize(query: String): List<String> {
        val norm = normalize(query)
        if (norm.isBlank()) return emptyList()
        val rawTokens = norm.split(TOKEN_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (rawTokens.isEmpty()) return emptyList()

        val meaningful = rawTokens.filter { token ->
            token.any { it.isDigit() } || (token.length >= 2 && token !in STOP_WORDS)
        }
        return if (meaningful.isNotEmpty()) meaningful else rawTokens
    }

    internal fun matchesToken(normalizedHaystack: String, token: String): Boolean {
        if (normalizedHaystack.isBlank() || token.isBlank()) return false
        if (normalizedHaystack.contains(token)) return true

        // Equivalencia de números con ceros a la izquierda (ej. "1" -> "01", "001", "t1x01")
        if (token.all { it.isDigit() }) {
            val numVal = token.toLongOrNull() ?: return false
            val numRegex = Regex("(?<![0-9])0*$numVal(?![0-9])")
            if (numRegex.containsMatchIn(normalizedHaystack)) return true
        }
        return false
    }

    fun computePostMatchScore(post: Post, query: String): Int {
        val normQuery = normalize(query)
        if (normQuery.isBlank()) return 0

        val normContent = normalize(post.contentText)
        val normAuthor = normalize(post.author.name)
        val normGroup = normalize(post.groupName.orEmpty())
        val normFeeling = normalize(post.feeling.orEmpty())
        val normLocation = normalize(post.location.orEmpty())
        val normMedia = normalize(post.mediaItems.joinToString(" ") { "${it.caption} ${it.url}" })

        val combined = "$normContent $normAuthor $normGroup $normFeeling $normLocation $normMedia"
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return 0

        var score = 0
        var matchedCount = 0

        // Coincidencia exacta completa
        if (normContent.contains(normQuery)) score += 120
        else if (normGroup.contains(normQuery)) score += 90
        else if (normAuthor.contains(normQuery)) score += 80
        else if (combined.contains(normQuery)) score += 60

        for (token in tokens) {
            var tokenScore = 0
            if (matchesToken(normContent, token)) tokenScore = maxOf(tokenScore, 30)
            if (matchesToken(normGroup, token)) tokenScore = maxOf(tokenScore, 24)
            if (matchesToken(normAuthor, token)) tokenScore = maxOf(tokenScore, 20)
            if (matchesToken(normLocation, token) || matchesToken(normFeeling, token)) tokenScore = maxOf(tokenScore, 14)
            if (matchesToken(normMedia, token)) tokenScore = maxOf(tokenScore, 12)
            if (tokenScore > 0) {
                matchedCount++
                score += tokenScore
            }
        }

        if (matchedCount == 0) return 0

        // Bonificación fuerte cuando coinciden TODAS las palabras escritas (sin importar el orden)
        if (matchedCount == tokens.size) {
            score += 80
        } else if (tokens.size > 1) {
            // Penalización parcial para que las coincidencias exactas/completas siempre vayan primero
            score += matchedCount * 10
        }

        return score
    }

    fun filterAndRankPosts(posts: List<Post>, query: String): List<Post> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return posts

        val scored = posts.mapNotNull { post ->
            val normCombined = normalize(
                buildString {
                    append(post.contentText).append(' ')
                    append(post.author.name).append(' ')
                    append(post.groupName.orEmpty()).append(' ')
                    append(post.feeling.orEmpty()).append(' ')
                    append(post.location.orEmpty()).append(' ')
                    append(post.mediaItems.joinToString(" ") { "${it.caption} ${it.url}" })
                }
            )
            val matchedTokens = tokens.count { matchesToken(normCombined, it) }
            if (matchedTokens == 0) null
            else Triple(post, matchedTokens == tokens.size, computePostMatchScore(post, query))
        }

        val allMatched = scored.filter { it.second }
        val chosen = if (allMatched.isNotEmpty()) allMatched else scored
        return chosen
            .sortedWith(
                compareByDescending<Triple<Post, Boolean, Int>> { it.third }
                    .thenByDescending { it.first.isPinned }
                    .thenByDescending { it.first.createdAt }
            )
            .map { it.first }
    }

    fun buildSuggestions(
        query: String,
        history: List<String>,
        members: List<FamilyMember>,
        groups: List<FamilyGroup>,
        cachedPosts: List<Post>
    ): List<SearchSuggestion> {
        val normQuery = normalize(query)
        val tokens = tokenize(query)
        val result = ArrayList<SearchSuggestion>(12)

        // 1. Historial reciente (todo cuando query está vacío, o coincidente mientras escribe)
        val matchingHistory = if (normQuery.isBlank()) {
            history.take(8)
        } else {
            history.filter { item ->
                val n = normalize(item)
                n.contains(normQuery) || tokens.all { matchesToken(n, it) }
            }.take(4)
        }
        matchingHistory.forEach { q ->
            result.add(
                SearchSuggestion(
                    id = "hist_$q",
                    title = q,
                    subtitle = "Búsqueda reciente",
                    query = q,
                    type = SearchSuggestionType.HISTORY,
                    emoji = "🕒"
                )
            )
        }

        if (normQuery.isNotBlank()) {
            // 2. Familiares coincidentes (0 ms local)
            members.filter { m ->
                val haystack = normalize("${m.name} ${m.role}")
                tokens.all { matchesToken(haystack, it) }
            }.take(3).forEach { m ->
                result.add(
                    SearchSuggestion(
                        id = "mem_${m.id}",
                        title = m.name,
                        subtitle = "Familiar · ${m.role}",
                        query = m.name,
                        type = SearchSuggestionType.MEMBER,
                        emoji = m.avatarEmoji,
                        targetMemberId = m.id
                    )
                )
            }

            // 3. Grupos o Series coincidentes (0 ms local)
            groups.filter { g ->
                val haystack = normalize("${g.name} ${g.description}")
                tokens.all { matchesToken(haystack, it) }
            }.take(4).forEach { g ->
                result.add(
                    SearchSuggestion(
                        id = "grp_${g.id}",
                        title = g.name,
                        subtitle = "${g.postsCount} publicaciones · ${g.organizationMode.label}",
                        query = g.name,
                        type = SearchSuggestionType.GROUP,
                        emoji = if (g.organizationMode.value == "SERIES") "🎬" else "👥",
                        targetGroupId = g.id
                    )
                )
            }

            // 4. Títulos rápidos en publicaciones cacheadas
            cachedPosts.asSequence()
                .filter { it.contentText.isNotBlank() }
                .filter { p ->
                    val haystack = normalize(p.contentText)
                    tokens.all { matchesToken(haystack, it) }
                }
                .map { it.contentText.trim().lineSequence().firstOrNull()?.take(56).orEmpty() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
                .forEach { snippet ->
                    if (result.none { normalize(it.query) == normalize(snippet) }) {
                        result.add(
                            SearchSuggestion(
                                id = "post_${snippet.hashCode()}",
                                title = snippet,
                                subtitle = "Coincidencia en publicaciones",
                                query = snippet,
                                type = SearchSuggestionType.POST,
                                emoji = "📝"
                            )
                        )
                    }
                }
        }

        return result.distinctBy { "${it.type}_${normalize(it.query)}_${it.targetGroupId}_${it.targetMemberId}" }.take(10)
    }

    /**
     * Construye un AnnotatedString resaltando cada término de [query] encontrado en [text]
     * sin distinguir tildes ni mayúsculas.
     */
    fun buildHighlightedText(
        text: String,
        query: String,
        highlightColor: Color,
        highlightBackground: Color
    ): AnnotatedString {
        if (text.isBlank() || query.isBlank()) return AnnotatedString(text)
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return AnnotatedString(text)

        // Construir mapeo índice normalizado -> índice original en [text]
        val normChars = StringBuilder(text.length)
        val originalIndices = ArrayList<Int>(text.length)
        for (i in text.indices) {
            val chStr = text[i].toString()
            val decomposed = Normalizer.normalize(chStr, Normalizer.Form.NFD)
            val stripped = DIACRITICS_REGEX.replace(decomposed, "").lowercase()
            for (c in stripped) {
                normChars.append(c)
                originalIndices.add(i)
            }
        }
        val normText = normChars.toString()
        val ranges = mutableListOf<IntRange>()

        tokens.forEach { token ->
            var startSearch = 0
            while (startSearch < normText.length) {
                val found = normText.indexOf(token, startIndex = startSearch)
                if (found < 0) break
                val endNorm = (found + token.length - 1).coerceAtMost(originalIndices.lastIndex)
                val origStart = originalIndices[found]
                val origEnd = (originalIndices[endNorm] + 1).coerceAtMost(text.length)
                if (origStart < origEnd) {
                    ranges.add(origStart until origEnd)
                }
                startSearch = found + token.length
            }
        }

        if (ranges.isEmpty()) return AnnotatedString(text)

        return buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                addStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        background = highlightBackground,
                        fontWeight = FontWeight.Bold
                    ),
                    start = range.first,
                    end = range.last + 1
                )
            }
        }
    }
}
