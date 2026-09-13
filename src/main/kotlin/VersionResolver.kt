@file:Suppress("ClassName")

package cz.eidam.dependencyinspector

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

data class RepositoryCoordinates(
    val group: String,
    val artifact: String,
)

sealed interface LatestVersionResult {
    val coordinates: RepositoryCoordinates
    data class Found(val version: Version, override val coordinates: RepositoryCoordinates): LatestVersionResult
    data class NotFound(override val coordinates: RepositoryCoordinates): LatestVersionResult
}

@Service(Service.Level.PROJECT)
class VersionResolver(
    private val coroutineScope: CoroutineScope,
): Disposable {

    private val logger = Logger.getInstance(VersionResolver::class.java)

    private val client = HttpClient(Java) { expectSuccess = true }
    private val repository = MavenCentralClient(client)

    // TODO: make service somehow replaceable for MavenVersionService in future
    private val service = GradleVersionService(repository, GradleVersionComparator)

    private val cache = ConcurrentHashMap<RepositoryCoordinates, LatestVersionResult>()
    private val loading = ConcurrentHashMap.newKeySet<RepositoryCoordinates>()

    fun cached(group: String, artifact: String): LatestVersionResult? {
        return cache[RepositoryCoordinates(group, artifact)]
    }

    fun request(group: String, artifact: String, onComplete: () -> Unit) {
        val coords = RepositoryCoordinates(group, artifact)

        val alreadyCached = cache.containsKey(coords)
        if (alreadyCached) return

        val alreadyLoading = !loading.add(coords)
        if (alreadyLoading) return

        coroutineScope.launch {
            try {
                val latestVersion = service.latest(group, artifact)

                val result = if (latestVersion != null) {
                    LatestVersionResult.Found(latestVersion, coords)
                } else {
                    LatestVersionResult.NotFound(coords)
                }
                cache[coords] = result
                onComplete.invoke()

            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    cache[coords] = LatestVersionResult.NotFound(coords)
                    onComplete()
                } else {
                    logFailure(group, artifact, e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(group, artifact, e)
            } finally {
                loading.remove(coords)
            }
        }
    }

    override fun dispose() {
        client.close()
    }

    private fun logFailure(group: String, artifact: String, e: Exception) {
        logger.warn("Failed to resolve latest version of \"$group:$artifact\"", e)
    }
}

