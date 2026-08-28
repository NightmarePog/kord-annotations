import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.jetbrains.dokka.gradle.DokkaExtension

fun releaseArchiveName(version: String): String = "kord-annotations-$version-maven-repository.tar.gz"

plugins {
	kotlin("jvm") version "2.4.10" apply false
	id("com.google.devtools.ksp") version "2.3.10" apply false
	id("org.jetbrains.dokka") version "2.1.0" apply false
	id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

val publicationVersion = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")
val repositoryUrl = "https://github.com/NightmarePog/kord-annotations"
val publicationDescriptions = mapOf(
	"core" to "Annotations and runtime for generated Kord command handlers.",
	"processor" to "KSP processor for Kord Annotations command modules.",
	"spring" to "Spring Boot integration for Kord Annotations.",
	"gradle-plugin" to "Gradle plugin that configures Kord Annotations and KSP.",
	"help" to "Help catalog generation for Kord Annotations commands.",
	"testkit" to "In-memory command testing tools for Kord Annotations.",
)

allprojects {
	group = "io.github.nightmarepog"
	version = publicationVersion
}

subprojects {
	val publishedArtifactName = "kord-annotations-$name"
	val publicationDescription = checkNotNull(publicationDescriptions[name])

	pluginManager.apply("com.vanniktech.maven.publish")
	pluginManager.apply("org.jetbrains.dokka")
	extensions.configure<DokkaExtension> {
		moduleName = publishedArtifactName
		dokkaPublications.configureEach { failOnWarning = true }
		dokkaSourceSets.configureEach {
			jdkVersion = 21
			reportUndocumented = true
		}
	}

	plugins.withId("org.jetbrains.kotlin.jvm") {
		extensions.configure<BasePluginExtension> {
			archivesName = publishedArtifactName
		}
		extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
			jvmToolchain(21)
			compilerOptions {
				freeCompilerArgs.add("-Xjsr305=strict")
			}
		}
		if (project.name != "gradle-plugin") {
			extensions.configure<MavenPublishBaseExtension> {
				configure(KotlinJvm(JavadocJar.Dokka("dokkaGeneratePublicationHtml"), SourcesJar.Sources()))
			}
		}
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
	}

	extensions.configure<MavenPublishBaseExtension> {
		coordinates(group.toString(), publishedArtifactName, version.toString())
		publishToMavenCentral(automaticRelease = true)
		signAllPublications()
		pom {
			name = publishedArtifactName
			description = publicationDescription
			url = repositoryUrl
			licenses {
				license {
					name = "Mozilla Public License 2.0"
					url = "https://www.mozilla.org/MPL/2.0/"
					distribution = "repo"
				}
			}
			developers {
				developer {
					id = "NightmarePog"
					name = "NightmarePog"
					url = "https://github.com/NightmarePog"
				}
			}
			scm {
				connection = "scm:git:$repositoryUrl.git"
				developerConnection = "scm:git:ssh://git@github.com/NightmarePog/kord-annotations.git"
				url = repositoryUrl
			}
		}
	}

	extensions.configure<PublishingExtension> {
		publications.withType<MavenPublication>().configureEach {
			if (name == "pluginMaven") artifactId = publishedArtifactName
		}
		repositories.maven {
			name = "buildRepository"
			url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
		}
	}
}

tasks.register<ValidateRelease>("validateRelease") {
	releaseVersion = publicationVersion
	retryPluginPortalOnly = providers.gradleProperty("retryPluginPortalOnly").map(String::toBoolean).orElse(false)
}

tasks.register<Tar>("releaseRepositoryArchive") {
	dependsOn(subprojects.map { "${it.path}:publishAllPublicationsToBuildRepositoryRepository" })
	compression = Compression.GZIP
	archiveFileName = releaseArchiveName(publicationVersion)
	destinationDirectory = layout.buildDirectory.dir("release")
	from(layout.buildDirectory.dir("repository"))
}

tasks.register<GenerateChecksum>("releaseRepositoryChecksum") {
	dependsOn("releaseRepositoryArchive")
	archive = layout.buildDirectory.file("release/${releaseArchiveName(publicationVersion)}")
	checksum = layout.buildDirectory.file("release/${releaseArchiveName(publicationVersion)}.sha256")
}
