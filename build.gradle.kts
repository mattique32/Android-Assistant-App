import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.ktlint)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.google.services).apply(false)
    alias(libs.plugins.firebase.appdistribution).apply(false)
    alias(libs.plugins.play.publisher).apply(false)
    alias(libs.plugins.hilt).apply(false)
    alias(libs.plugins.kotlin.kapt).apply(false)
    alias(libs.plugins.kotlin.parcelize).apply(false)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}

gradle.projectsEvaluated {
    project(":app").tasks.matching { it.name.startsWith("publish") }.configureEach {
        mustRunAfter(project(":wear").tasks.matching { it.name.startsWith("publish") })
    }
}

tasks.register("clean").configure {
    delete("build")
}

ktlint {
    android.set(true)
}

tasks.register("versionFile").configure {
    group = "publishing"
    doLast {
        File(projectDir, "version.txt").writeText(project.version.toString())
    }
}
