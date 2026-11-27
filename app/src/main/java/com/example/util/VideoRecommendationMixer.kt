package com.example.util

import com.example.data.local.VideoEntity
import java.util.Locale
import kotlin.math.abs

object VideoRecommendationMixer {

    private val turkishLocale = Locale.forLanguageTag("tr-TR")

    fun mixVideosForDiscovery(
        videos: List<VideoEntity>,
        seed: Long,
        currentPlayingVideo: VideoEntity? = null
    ): List<VideoEntity> {
        if (videos.size <= 1) return videos

        val currentTokens = currentPlayingVideo?.let { tokenizeTitle(it.title) }.orEmpty()

        data class ScoredCandidate(
            val video: VideoEntity,
            val baseScore: Double,
            val titleKey: String
        )

        val remaining = videos.map { video ->
            val jitter = deterministicUnitDouble(seed, video.id) * 1.20
            val favoriteBoost = if (video.isFavorite) 0.24 else 0.0
            val resumeBoost = if (
                video.lastPositionMs > 5_000L &&
                (video.durationMs <= 0L || video.lastPositionMs < video.durationMs - 10_000L)
            ) 0.18 else 0.0
            val freshnessBoost = 0.22 / (1.0 + video.playCount * 0.25)

            val contextCategoryBoost = if (
                currentPlayingVideo != null && video.categoryId == currentPlayingVideo.categoryId
            ) 0.28 else 0.0

            val overlapBoost = if (currentTokens.isNotEmpty()) {
                val overlap = tokenizeTitle(video.title).intersect(currentTokens).size
                (overlap * 0.16).coerceAtMost(0.32)
            } else {
                0.0
            }

            ScoredCandidate(
                video = video,
                baseScore = jitter + favoriteBoost + resumeBoost + freshnessBoost +
                    contextCategoryBoost + overlapBoost,
                titleKey = leadingTitleKey(video.title)
            )
        }.toMutableList()

        val result = ArrayList<VideoEntity>(remaining.size)
        var prev1: ScoredCandidate? = null
        var prev2: ScoredCandidate? = null

        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestScore = Double.NEGATIVE_INFINITY

            for (i in remaining.indices) {
                val cand = remaining[i]
                var score = cand.baseScore

                val p1 = prev1
                if (p1 != null) {
                    if (cand.video.categoryId == p1.video.categoryId) {
                        score -= 0.55
                    }
                    if (cand.titleKey.isNotEmpty() && cand.titleKey == p1.titleKey) {
                        score -= 0.70
                    }
                    val p2 = prev2
                    if (p2 != null) {
                        if (cand.video.sourceType == p1.video.sourceType &&
                            cand.video.sourceType == p2.video.sourceType
                        ) {
                            score -= 0.35
                        }
                        if (cand.video.categoryId == p2.video.categoryId) {
                            score -= 0.25
                        }
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }

            val chosen = remaining.removeAt(bestIdx)
            result.add(chosen.video)
            prev2 = prev1
            prev1 = chosen
        }

        return result
    }

    fun matchesSearchQuery(video: VideoEntity, rawQuery: String): Boolean {
        val cleanQuery = normalizeForSearch(rawQuery)
        if (cleanQuery.isEmpty()) return true

        val titleNorm = normalizeForSearch(video.title)
        val catNorm = normalizeForSearch(video.category.labelTr)
        val sourceNorm = normalizeForSearch(video.source.labelTr)

        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        return tokens.all { token ->
            titleNorm.contains(token) || catNorm.contains(token) || sourceNorm.contains(token)
        }
    }

    private fun normalizeForSearch(input: String): String {
        return input
            .trim()
            .lowercase(turkishLocale)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("\\s+"), " ")
    }

    private fun tokenizeTitle(title: String): Set<String> {
        return normalizeForSearch(title)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .toSet()
    }

    private fun leadingTitleKey(title: String): String {
        val cleaned = normalizeForSearch(title)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
        val firstWord = cleaned.substringBefore(' ').take(10)
        return if (firstWord.length >= 4) firstWord else cleaned.take(8)
    }

    private fun deterministicUnitDouble(seed: Long, itemId: Long): Double {
        var z = seed xor (itemId * -0x61c8864680b583ebL)
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        val positive = abs(z % 100_000L)
        return positive / 100_000.0
    }
}
