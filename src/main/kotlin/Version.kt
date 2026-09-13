package cz.eidam.dependencyinspector

@JvmInline
value class Version(val value: String) {
    companion object {
        val QUALIFIERS = mapOf(
            "snapshot" to 10,
            "alpha" to 20,
            "a" to 20,
            "beta" to 30,
            "b" to 30,
            "preview" to 40,
            "milestone" to 50,
            "m" to 50,
            "rc" to 60,
            "cr" to 60,
        )
    }
}

private val UNSTABLE_KEYWORDS = listOf(
    "alpha", "beta", "rc", "cr", "snapshot", "milestone", "preview", "dev", "test", "experimental", "wip"
)

fun Version.isStable(): Boolean {
    val normalized = this.value.lowercase()

    return UNSTABLE_KEYWORDS.none { keyword ->
        keyword in normalized
    }
}


sealed interface ParsedVersion {
    val numbers: List<Int>

    data class Release(override val numbers: List<Int>): ParsedVersion

    data class Qualified(
        override val numbers: List<Int>,
        val qualifier: String,
        val variant: Int? = null
    ): ParsedVersion
}

fun Version.parse(): ParsedVersion? {
    val chunks = value.split('-')


    val numbers = runCatching {

        chunks.first()
            .split('.')
            .map(String::toInt)

    }.getOrNull() ?: return null


    if (chunks.size == 1) {
        return ParsedVersion.Release(numbers)
    }

    val qualifier = chunks.get(1).lowercase()
    val variant = chunks.getOrNull(2)?.toIntOrNull()

    if (qualifier !in Version.QUALIFIERS) return null

    return ParsedVersion.Qualified(numbers, qualifier, variant)
}

