package cz.eidam.dependencyinspector

interface VersionService {
    suspend fun latest(group: String, artifact: String): Version?
}

class GradleVersionService(
    private val repository: RepositoryClient,
    private val comparator: GradleVersionComparator,
): VersionService {

    override suspend fun latest(
        group: String,
        artifact: String,
    ): Version? {
        val all = repository.versions(group, artifact)

        val filtered = all.filter(Version::isStable)
        if (filtered.isEmpty()) return null

        return filtered.maxWith(comparator::compare)
    }

}