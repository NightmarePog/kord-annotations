plugins {
	kotlin("jvm") version "2.4.10" apply false
	id("com.google.devtools.ksp") version "2.3.10" apply false
}

allprojects {
	group = "io.github.nightmarepog"
	version = "0.1.0-SNAPSHOT"
}

subprojects {
	plugins.withId("org.jetbrains.kotlin.jvm") {
		extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
			jvmToolchain(21)
			compilerOptions {
				freeCompilerArgs.add("-Xjsr305=strict")
			}
		}
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
	}

	if (name != "gradle-plugin") {
		pluginManager.apply("maven-publish")
		extensions.configure<org.gradle.api.publish.PublishingExtension> {
			publications.create<org.gradle.api.publish.maven.MavenPublication>("library") {
				artifactId = "kord-annotations-${project.name}"
				pluginManager.withPlugin("java") { from(components["java"]) }
			}
			repositories.maven {
				name = "buildRepository"
				url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
			}
		}
	}
}
