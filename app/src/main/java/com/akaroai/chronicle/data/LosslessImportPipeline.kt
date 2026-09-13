package com.akaroai.chronicle.data

import java.security.MessageDigest

data class ImportSegment(
    val index: Int,
    val primaryStart: Int,
    val primaryEnd: Int,
    val contextStart: Int,
    val contextEnd: Int,
    val content: String
)

data class ImportCoverage(
    val complete: Boolean,
    val sourceLength: Int,
    val coveredCharacters: Int,
    val segmentCount: Int
)

object LosslessImportPipeline {
    const val DEFAULT_TARGET_CHARS = 3_500
    const val DEFAULT_OVERLAP_CHARS = 300

    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun segment(
        source: String,
        targetChars: Int = DEFAULT_TARGET_CHARS,
        overlapChars: Int = DEFAULT_OVERLAP_CHARS
    ): List<ImportSegment> {
        require(targetChars >= 2_000)
        require(overlapChars in 0 until targetChars)
        if (source.isEmpty()) return emptyList()

        val result = mutableListOf<ImportSegment>()
        var primaryStart = 0
        while (primaryStart < source.length) {
            val desiredEnd = (primaryStart + targetChars).coerceAtMost(source.length)
            val primaryEnd = if (desiredEnd == source.length) source.length else {
                safeBoundary(source, primaryStart, desiredEnd)
            }.coerceAtLeast((primaryStart + 1).coerceAtMost(source.length))

            val contextStart = (primaryStart - overlapChars).coerceAtLeast(0)
            val contextEnd = (primaryEnd + overlapChars).coerceAtMost(source.length)
            result += ImportSegment(
                index = result.size,
                primaryStart = primaryStart,
                primaryEnd = primaryEnd,
                contextStart = contextStart,
                contextEnd = contextEnd,
                content = source.substring(contextStart, contextEnd)
            )
            primaryStart = primaryEnd
        }
        check(verifyCoverage(source, result).complete) { "Import segmentation left a source gap." }
        return result
    }

    fun verifyCoverage(source: String, segments: List<ImportSegment>): ImportCoverage {
        var cursor = 0
        var covered = 0
        for (segment in segments.sortedBy { it.primaryStart }) {
            if (segment.primaryStart != cursor || segment.primaryEnd < segment.primaryStart) {
                return ImportCoverage(false, source.length, covered, segments.size)
            }
            covered += segment.primaryEnd - segment.primaryStart
            cursor = segment.primaryEnd
        }
        return ImportCoverage(
            complete = cursor == source.length && covered == source.length,
            sourceLength = source.length,
            coveredCharacters = covered,
            segmentCount = segments.size
        )
    }

    private fun safeBoundary(source: String, start: Int, desiredEnd: Int): Int {
        val searchFloor = (desiredEnd - 2_000).coerceAtLeast(start + 1)
        val paragraph = source.lastIndexOf("\n\n", desiredEnd)
        if (paragraph >= searchFloor) return paragraph + 2
        val line = source.lastIndexOf('\n', desiredEnd)
        if (line >= searchFloor) return line + 1
        val sentence = listOf(". ", "! ", "? ")
            .map { source.lastIndexOf(it, desiredEnd) }
            .maxOrNull() ?: -1
        return if (sentence >= searchFloor) sentence + 2 else desiredEnd
    }
}
