package cz.eidam.dependencyinspector

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

fun interface DependencyParser {
    /** Returns list of parsed dependencies, or null if file type is not supported. */
    fun parse(file: PsiFile): List<Dependency>?
}

data class Dependency(
    val group: String,
    val artifact: String,
    val version: Version,
    val elements: Dependency.Elements
) {
    data class Elements(
        val version: PsiElement,
        val dependency: PsiElement
    )
}

