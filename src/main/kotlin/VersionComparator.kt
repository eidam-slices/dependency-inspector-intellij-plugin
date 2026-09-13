package cz.eidam.dependencyinspector

import kotlin.math.max

interface VersionComparator {
    fun compare(a: Version, b: Version): Int
}

object GradleVersionComparator: VersionComparator {

    override fun compare(a: Version, b: Version): Int {
        val a = a.parse() ?: return 0
        val b = b.parse() ?: return 0

        compareNumbers(a.numbers, b.numbers).let { comparison ->
            if (comparison != 0) return comparison
        }

        return when {
            a is ParsedVersion.Release && b is ParsedVersion.Release -> 0

            a is ParsedVersion.Release -> 1
            b is ParsedVersion.Release -> -1

            a is ParsedVersion.Qualified && b is ParsedVersion.Qualified -> {
                val qualifierComparison = compareQualifiers(a.qualifier, b.qualifier)

                if (qualifierComparison != 0) qualifierComparison
                else (a.variant ?: 0).compareTo(b.variant ?: 0)
            }
            else -> 0
        }

    }

    private fun compareQualifiers(a: String, b: String): Int {
        val weightA = Version.QUALIFIERS[a.lowercase()] ?: return 0
        val weightB = Version.QUALIFIERS[b.lowercase()] ?: return 0
        return weightA.compareTo(weightB)
    }

    private fun compareNumbers(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until max(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }

            if (left != right) {
                return left.compareTo(right)
            }
        }
        return 0
    }
}

