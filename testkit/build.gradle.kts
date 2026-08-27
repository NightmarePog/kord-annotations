plugins {
	kotlin("jvm")
	id("com.google.devtools.ksp")
	`java-library`
}

base {
	archivesName = "kord-annotations-testkit"
}

dependencies {
	api(project(":core"))
	api(kotlin("test"))
	testImplementation(kotlin("test-junit5"))
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
	kspTest(project(":processor"))
}
