plugins {
	kotlin("jvm")
	`java-library`
}

dependencies {
	api("dev.kord:kord-core:0.18.1")
	api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

	testImplementation(kotlin("test-junit5"))
}
