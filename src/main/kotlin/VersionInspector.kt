package cz.eidam.dependencyinspector

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile


class VersionInspector: LocalInspectionTool() {

    private val parser = VersionCatalogDependencyParser()
    private val comparator = GradleVersionComparator

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return DependencyVisitor(parser, comparator, holder)
    }

    private class DependencyVisitor(
        private val parser: DependencyParser,
        private val comparator: VersionComparator,
        private val holder: ProblemsHolder
    ): PsiElementVisitor() {

        override fun visitFile(file: PsiFile) {
            // extract dependencies or return if file isn't supported
            val dependencies = parser.parse(file) ?: return

            val resolver = file.project.service<VersionResolver>()

            for (dependency in dependencies) {
                val result = resolver.cached(dependency.group, dependency.artifact)

                when (result) {
                    is LatestVersionResult.Found -> {
                        val comparison = comparator.compare(result.version, dependency.version)
                        val newVersionAvailable = comparison > 0

                        if (newVersionAvailable) {
                            val context = "${result.coordinates.group}:${result.coordinates.artifact}"
                            holder.registerProblem(
                                dependency.elements.version,
                                "Newer version for '$context' is available: ${result.version.value}",
                                UpdateVersionQuickFix(result.version)
                            )
                        }
                    }
                    is LatestVersionResult.NotFound -> {}
                    null -> {
                        resolver.request(
                            group = dependency.group,
                            artifact = dependency.artifact,
                            onComplete = { restartInspection(file) }
                        )
                    }
                }
            }


        }
    }

}

private fun restartInspection(file: PsiFile) {
    val instance = DaemonCodeAnalyzer.getInstance(file.project)
    instance.restart(file, "VersionResolver cache got updated.")
}

