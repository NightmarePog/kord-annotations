import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
	kotlin("jvm") version "2.4.10" apply false
	id("com.google.devtools.ksp") version "2.3.10" apply false
}

val publicationVersion = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

allprojects {
	group = "io.github.nightmarepog"
	version = publicationVersion
}

subprojects {
	val publishedArtifactName = "kord-annotations-$name"
	val githubPackagesUsername =
		providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR"))
	val githubPackagesToken =
		providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN"))

	pluginManager.apply("maven-publish")

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
	}
	plugins.withId("java") {
		extensions.configure<JavaPluginExtension> {
			withSourcesJar()
		}
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
	}

	extensions.configure<PublishingExtension> {
		if (project.name != "gradle-plugin") {
			publications.create<MavenPublication>("library") {
				artifactId = publishedArtifactName
				pluginManager.withPlugin("java") { from(components["java"]) }
			}
		}

		publications.withType<MavenPublication>().configureEach {
			if (name == "pluginMaven") artifactId = publishedArtifactName
		}

		repositories {
			maven {
				name = "buildRepository"
				url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
			}
			maven {
				name = "GitHubPackages"
				url = uri("https://maven.pkg.github.com/nightmarepog/kord-annotations")
				credentials {
					username = githubPackagesUsername.orNull
					password = githubPackagesToken.orNull
				}
			}
		}
	}
}
