plugins {
	kotlin("jvm")
	`java-library`
}

base {
	archivesName = "kord-annotations-spring"
}

dependencies {
	api(project(":core"))
	compileOnly(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
	compileOnly("org.springframework.boot:spring-boot-autoconfigure")
	compileOnly("org.springframework:spring-context")

	testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation(kotlin("test-junit5"))
}

