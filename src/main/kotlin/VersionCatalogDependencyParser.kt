package cz.eidam.dependencyinspector

import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.*

class VersionCatalogDependencyParser: DependencyParser {

    private data class VersionDefinition(
        val version: Version,
        val element: PsiElement
    )

    private data class DependencyDefinition(
        val group: String,
        val artifact: String,
        val version: DependencyDefinitionVersion,
        val element: PsiElement
    )

    private sealed interface DependencyDefinitionVersion {
        data class Direct(val version: Version, val element: PsiElement): DependencyDefinitionVersion
        data class Reference(val name: String /*val element: PsiElement*/): DependencyDefinitionVersion
    }

    override fun parse(file: PsiFile): List<Dependency>? {
        if (file !is TomlFile) return null

        val versions = mutableMapOf<String, VersionDefinition>()
        val dependencies = mutableListOf<DependencyDefinition>()

        val tables = file.childrenOfType<TomlTable>()
        for (table in tables) {

            val type = table.header.key?.text?.let { text ->
                VersionCatalogTable.from(text)
            } ?: continue

            for (entry in table.entries) {
                when (type) {
                    VersionCatalogTable.Versions -> {
                        val key = entry.key
                        val value = entry.value?.stringLiteralOrNull() ?: continue

                        versions[key.text] = VersionDefinition(Version(value.valueText), value)
                    }
                    VersionCatalogTable.Libraries, VersionCatalogTable.Plugins -> {
                        val value = entry.value?.inlineTableOrNull() ?: continue
                        val map = MappedDependencyDefinition(value.toValueMap())
                        val coords = map.coordinates(type) ?: continue
                        val version = map.version() ?: continue


                        dependencies += DependencyDefinition(
                            group = coords.group,
                            artifact = coords.artifact,
                            version = version,
                            element = when (type) {
                                VersionCatalogTable.Plugins -> {
                                    map["id"] ?: continue
                                }
                                VersionCatalogTable.Libraries -> {
                                    map["module"] ?: value
                                }
                            }
                        )
                    }

                }
            }
        }

        return buildList {
            for (dependency in dependencies) {

                val version = when (val version = dependency.version) {
                    is DependencyDefinitionVersion.Direct -> VersionDefinition(version.version, version.element)
                    is DependencyDefinitionVersion.Reference -> versions[version.name] ?: continue
                }



                this += Dependency(
                    group = dependency.group,
                    artifact = dependency.artifact,
                    version = version.version,
                    elements = Dependency.Elements(
                        version = version.element,
                        dependency = dependency.element
                    ),
                )
            }
        }
    }

    @JvmInline
    private value class MappedDependencyDefinition(private val map: Map<String, TomlLiteral>) {

        operator fun get(key: String): TomlLiteral? {
            return map[key]
        }

        fun version(): DependencyDefinitionVersion? {
            val direct = map["version"]
            val reference = map["version.ref"]

            return when {
                reference == null && direct != null -> {
                    DependencyDefinitionVersion.Direct(
                        version = Version(direct.valueText),
                        element = direct
                    )
                }
                reference != null && direct == null -> {
                    DependencyDefinitionVersion.Reference(
                        name = reference.valueText
                    )
                }
                else -> null
            }
        }

        fun coordinates(type: VersionCatalogTable): RepositoryCoordinates? {
            return when (type) {
                VersionCatalogTable.Libraries -> libraryCoordinates()
                VersionCatalogTable.Plugins -> pluginCoordinates()
                else -> null
            }
        }

        private fun libraryCoordinates(): RepositoryCoordinates? {
            val module = map["module"]
            val group = map["group"]
            val artifact = map["name"]

            return when {
                module == null && group != null && artifact != null -> {
                    RepositoryCoordinates(group.valueText, artifact.valueText)
                }
                module != null && group == null && artifact == null -> {
                    val parts = module.valueText.split(':')
                    if (parts.size != 2) return null
                    RepositoryCoordinates(group = parts[0], artifact = parts[1])
                }
                else -> null
            }
        }

        private fun pluginCoordinates(): RepositoryCoordinates? {
            val id = map["id"] ?: return null

            val group = id.valueText
            val artifact = "$group.gradle.plugin"

            return RepositoryCoordinates(group, artifact)
        }
    }

}

private enum class VersionCatalogTable {
    Versions, Libraries, Plugins;

    companion object {
        fun from(value: String): VersionCatalogTable? {
            return when (value) {
                "versions" -> Versions
                "libraries" -> Libraries
                "plugins" -> Plugins
                else -> null
            }
        }
    }
}

/* EXTENSIONS */

/** Returns text of a string TOML literal value. Unlike the [TomlLiteral.text], this returns text without quotes. */
val TomlLiteral.valueText: String
    get() = ElementManipulators.getValueText(this)

/** Returns map, where keys are key's texts and values are only string literals texts.*/
fun TomlInlineTable.toValueMap(): Map<String, TomlLiteral> {
    val table = this
    return buildMap {
        for (entry in table.entries) {
            val key = entry.key
            val value = entry.value?.stringLiteralOrNull() ?: continue

            this[key.text] = value
        }
    }
}

fun TomlValue.stringLiteralOrNull(): TomlLiteral? {
    if (this !is TomlLiteral) return null
    if (!this.isStringLiteral()) return null
    return this
}

fun TomlValue.inlineTableOrNull(): TomlInlineTable? {
    if (this !is TomlInlineTable) return null
    return this
}

fun TomlLiteral.isStringLiteral(): Boolean {
    return this.node.findChildByType(TomlElementTypes.BASIC_STRING) != null
}
