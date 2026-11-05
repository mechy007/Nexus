plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.16.0")
    implementation("io.ktor:ktor-client-cio:3.5.1")
    implementation("io.ktor:ktor-sse:3.5.1")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
