plugins {
	kotlin("jvm")
	`java-library`
}

base {
	archivesName = "kord-annotations-help"
}

dependencies {
	api(project(":core"))
	testImplementation(kotlin("test-junit5"))
}

