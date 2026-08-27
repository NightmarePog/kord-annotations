plugins {
	kotlin("jvm")
	`java-library`
}

dependencies {
	implementation("com.google.devtools.ksp:symbol-processing-api:2.3.10")
	testImplementation(kotlin("test-junit5"))
}
