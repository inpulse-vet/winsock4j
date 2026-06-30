import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "vet.inpulse"
version = System.getenv("VERSION")?.removePrefix("v")?.ifBlank { null } ?: "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "winsock4j", version.toString())

    pom {
        name.set("winsock4j")
        description.set(
            "Kotlin bindings to the Windows Winsock2 (ws2_32) API via the Java FFM API, focused on Bluetooth RFCOMM."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/inpulse-vet/winsock4j")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("noliveira")
                name.set("Nathaniel Salvador de Oliveira")
                email.set("noliveira@inpulse.vet")
            }
        }
        scm {
            url.set("https://github.com/inpulse-vet/winsock4j")
            connection.set("scm:git:git://github.com/inpulse-vet/winsock4j.git")
            developerConnection.set("scm:git:ssh://git@github.com/inpulse-vet/winsock4j.git")
        }
    }
}
