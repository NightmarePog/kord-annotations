import org.gradle.plugin.compatibility.compatibility

plugins {
	kotlin("jvm")
	`java-gradle-plugin`
	id("com.gradle.plugin-publish") version "2.1.1"
}

dependencies {
	implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10")
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
	testImplementation(gradleTestKit())
	testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
	website.set("https://github.com/NightmarePog/kord-annotations")
	vcsUrl.set("https://github.com/NightmarePog/kord-annotations")
	plugins {
		create("kordAnnotations") {
			id = "io.github.nightmarepog.kord-annotations"
			implementationClass = "io.github.nightmarepog.kordannotations.gradle.KordAnnotationsPlugin"
			displayName = "Kord Annotations"
			description = "Configures KSP to generate Kord command modules."
			tags.set(listOf("discord", "kord", "kotlin", "ksp", "commands"))
			compatibility {
				features {
					configurationCache = false
				}
			}
		}
	}
}

tasks.processResources {
	val publicationVersion = project.version.toString()
	inputs.property("publicationVersion", publicationVersion)
	filesMatching("kord-annotations-version.txt") {
		expand("publicationVersion" to publicationVersion)
	}
}
