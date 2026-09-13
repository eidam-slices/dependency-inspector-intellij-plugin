package cz.eidam.dependencyinspector

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators

class UpdateVersionQuickFix(private val version: Version): LocalQuickFix {
    override fun getFamilyName(): @IntentionFamilyName String {
        return "Update to ${version.value}"
    }

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor
    ) {
        val element = descriptor.psiElement

        ElementManipulators.handleContentChange(
            element, version.value
        )
    }
}