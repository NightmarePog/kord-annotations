plugins {
	kotlin("jvm")
	id("com.google.devtools.ksp")
	`java-library`
}

dependencies {
	api(project(":core"))
	testImplementation(kotlin("test-junit5"))
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
	kspTest(project(":processor"))
}
