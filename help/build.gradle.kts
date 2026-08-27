plugins {
	kotlin("jvm")
	`java-library`
}

dependencies {
	api(project(":core"))
	testImplementation(kotlin("test-junit5"))
}
