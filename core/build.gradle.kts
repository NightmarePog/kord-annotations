plugins {
	kotlin("jvm")
	`java-library`
}

base {
	archivesName = "kord-annotations-core"
}

dependencies {
	api("dev.kord:kord-core:0.18.1")
	api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
	implementation("org.yaml:snakeyaml:2.4")
	implementation("com.ibm.icu:icu4j:77.1")

	testImplementation(kotlin("test-junit5"))
}

