plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.apptasticsoftware:rssreader:3.7.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}