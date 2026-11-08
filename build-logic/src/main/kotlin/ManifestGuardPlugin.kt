import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

interface ManifestGuardExtension {
    val allowedExported: SetProperty<String>
}

abstract class CheckManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowedExported: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val found = ManifestGuard.violations(manifest.get().asFile.readText(), allowedExported.get())
        val reportFile = report.get().asFile
        reportFile.writeText(if (found.isEmpty()) "No violations.\n" else found.joinToString("\n", postfix = "\n"))
        if (found.isNotEmpty()) {
            throw GradleException("Merged manifest breaks the privacy rules (see CLAUDE.md):\n" + found.joinToString("\n") { "  - $it" })
        }
    }
}

/** Fails the build when the merged manifest gains a banned permission or an unexpected exported component. */
class ManifestGuardPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("manifestGuard", ManifestGuardExtension::class.java)
        val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
        components.onVariants { variant ->
            val cap = variant.name.replaceFirstChar { it.uppercase() }
            val task = project.tasks.register("guard${cap}Manifest", CheckManifestTask::class.java) {
                manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                allowedExported.set(extension.allowedExported)
                report.set(project.layout.buildDirectory.file("reports/manifest-guard/${variant.name}.txt"))
            }
            project.tasks.matching { it.name in setOf("check", "package$cap", "bundle$cap") }
                .configureEach { dependsOn(task) }
        }
    }
}
