import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ValidateRelease : DefaultTask() {
    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val retryPluginPortalOnly: Property<Boolean>

    @TaskAction
    fun validate() {
        check(releaseVersion.get().isSemanticVersion()) {
            "Release versions must use MAJOR.MINOR.PATCH, for example 0.2.0."
        }
        missingCredentials(retryPluginPortalOnly.get()).let {
            check(it.isEmpty()) { "Missing release credentials: ${it.joinToString()}" }
        }
    }
}

private fun missingCredentials(retryPluginPortalOnly: Boolean): Set<String> = mapOf(
    "mavenCentralUsername" to System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername"),
    "mavenCentralPassword" to System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword"),
    "signingInMemoryKey" to System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey"),
    "GRADLE_PUBLISH_KEY" to System.getenv("GRADLE_PUBLISH_KEY"),
    "GRADLE_PUBLISH_SECRET" to System.getenv("GRADLE_PUBLISH_SECRET"),
).filter { (name, value) -> value.isNullOrBlank() && (!name.isCentralCredential() || !retryPluginPortalOnly) }.keys

private fun String.isCentralCredential(): Boolean = startsWith("maven") || startsWith("signing")

private fun String.isSemanticVersion(): Boolean =
    Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(this)
