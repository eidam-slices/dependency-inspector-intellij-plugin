plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    // testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2025.3.5")
        // testFramework(TestFrameworkType.Platform)


        bundledPlugin("org.toml.lang")
    }
}
