package cz.eidam.dependencyinspector

import com.intellij.openapi.util.JDOMUtil
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

interface RepositoryClient {
    suspend fun versions(group: String, artifact: String): List<Version>
}

class MavenCentralClient(
    private val client: HttpClient
): RepositoryClient {

    companion object {

        private const val URL = "https://repo.maven.apache.org/maven2"
        private const val FILE = "maven-metadata.xml"

        fun url(group: String, artifact: String): String {
            return listOf(URL, group.replace('.', '/'), artifact, FILE).joinToString("/")
        }

    }

    override suspend fun versions(group: String, artifact: String): List<Version> {
        val response = client.get(url(group, artifact))
        val xml = response.bodyAsText()

        return parse(xml)
    }

    private fun parse(xml: String): List<Version> {
        val root = JDOMUtil.load(xml)
        val versioning = root.getChild("versioning") ?: error("Versioning (<versioning>) element not found in XML.")
        val versions = versioning.getChild("versions") ?: error("Versions (<versions>) element not found in XML.")
        val elements = versions.getChildren("version")

        return elements.map { element ->
            Version(element.textTrim)
        }
    }
}
