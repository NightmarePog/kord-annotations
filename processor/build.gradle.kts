plugins {
	kotlin("jvm")
	`java-library`
}

base {
	archivesName = "kord-annotations-processor"
}

dependencies {
	implementation(project(":core"))
	implementation("com.google.devtools.ksp:symbol-processing-api:2.3.10")
	testImplementation(kotlin("test-junit5"))
}

