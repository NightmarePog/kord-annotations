plugins {
	kotlin("jvm")
	`java-gradle-plugin`
}

dependencies {
	implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10")
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
	testImplementation(gradleTestKit())
	testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
	plugins {
		create("kordAnnotations") {
			id = "io.github.nightmarepog.kord-annotations"
			implementationClass = "io.github.nightmarepog.kordannotations.gradle.KordAnnotationsPlugin"
			displayName = "Kord Annotations"
			description = "Configures KSP to generate Kord command modules."
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
