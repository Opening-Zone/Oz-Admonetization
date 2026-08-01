import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.PublishingExtension
import java.util.Properties
import java.util.Base64
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun getPublishProperty(name: String, defaultValue: String = ""): String {
    return (localProperties.getProperty(name) ?: project.findProperty(name) as? String ?: defaultValue).trim()
}

android {
    namespace = "com.oz.android.ads_core"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        buildConfigField("String", "LIB_VERSION", "\"${getPublishProperty("PUBLISH_VERSION", "1.0.4")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }



    //noinspection UseTomlInstead
    dependencies {
        // AndroidX
        implementation("androidx.appcompat:appcompat:1.7.1")
        implementation("androidx.core:core-ktx:1.17.0")
        implementation("androidx.constraintlayout:constraintlayout:2.2.1")
        implementation("androidx.window:window:1.5.1")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
        implementation("androidx.lifecycle:lifecycle-process:2.10.0")


        // Kotlin Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

        // Google Play Services Ads + UMP
        api("com.google.android.gms:play-services-ads:25.4.0")
        api("com.google.android.ump:user-messaging-platform:4.0.0")
        api("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1") {
            exclude(group = "com.google.android.gms", module = "play-services-ads-api")
        }


        // Shimmer
        implementation("io.github.usefulness:shimmer-android-core:1.0.0")

        // Testing
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.3.0")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

        // Firebase (using BOM for version alignment)
        implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
        implementation("com.google.firebase:firebase-analytics")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

localProperties.forEach { key, value ->
    val keyStr = key.toString()
    if (keyStr.startsWith("signing.")) {
        extra.set(keyStr, value.toString().trim())
    }
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications {
            register<MavenPublication>("release") {
                groupId = getPublishProperty("PUBLISH_GROUP_ID", "com.opening-zone.software")
                artifactId = getPublishProperty("PUBLISH_ARTIFACT_ID", "android-ads")
                version = getPublishProperty("PUBLISH_VERSION", "1.0.0")

                from(components["release"])

                pom {
                    name.set("Opening Zone Mobile Ads")
                    description.set("Android library for Opening Zone ad monetization")
                    url.set("https://github.com/Opening-Zone/oz.mobile.ad_monetization")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("openingzone")
                            name.set("Opening Zone")
                            email.set("info@opening-zone.software")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/Opening-Zone/oz.mobile.ad_monetization.git")
                        developerConnection.set("scm:git:ssh://github.com/Opening-Zone/oz.mobile.ad_monetization.git")
                        url.set("https://github.com/Opening-Zone/oz.mobile.ad_monetization")
                    }
                }
            }
        }
        repositories {
            // Local file repository used to generate the ZIP bundle with all checksums and signatures
            maven {
                name = "Bundle"
                url = uri(layout.buildDirectory.dir("repo"))
            }

            // GitHub Packages Repository (linked to Opening-Zone/oz.mobile.ad_monetization)
            val ghUsername = getPublishProperty("GITHUB_USERNAME")
            val ghToken = getPublishProperty("GITHUB_TOKEN")
            if (ghUsername.isNotEmpty() && ghToken.isNotEmpty()) {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/Opening-Zone/oz.mobile.ad_monetization")
                    credentials {
                        username = ghUsername
                        password = ghToken
                    }
                }
            }

            val repoUrl = getPublishProperty("PUBLISH_REPO_URL")
            if (repoUrl.isNotEmpty() && !repoUrl.contains("central.sonatype.com") && !repoUrl.contains("maven.pkg.github.com")) {
                maven {
                    url = uri(repoUrl)
                    credentials {
                        username = getPublishProperty("PUBLISH_REPO_USERNAME")
                        password = getPublishProperty("PUBLISH_REPO_PASSWORD")
                    }
                }
            }
        }
        
        val gpgKeyId = System.getenv("SIGNING_KEY_ID") ?: getPublishProperty("signing.keyId")
        val gpgKey = System.getenv("SIGNING_KEY") ?: getPublishProperty("signing.key")
        val gpgPassword = System.getenv("SIGNING_PASSWORD") ?: getPublishProperty("signing.password")

        val hasInMemoryKey = gpgKey.isNotEmpty() && gpgPassword.isNotEmpty()
        val hasGpgCmd = project.hasProperty("signing.gnupg.keyName")
        val hasSigningKey = project.hasProperty("signing.keyId") || 
                             project.hasProperty("signing.secretKeyRingFile")
                             
        val signing = extensions.getByType<org.gradle.plugins.signing.SigningExtension>()
        if (hasInMemoryKey) {
            signing.useInMemoryPgpKeys(gpgKeyId, gpgKey, gpgPassword)
            signing.sign(extensions.getByType<PublishingExtension>().publications["release"])
        } else if (hasGpgCmd) {
            signing.useGpgCmd()
            signing.sign(extensions.getByType<PublishingExtension>().publications["release"])
        } else if (hasSigningKey) {
            signing.sign(extensions.getByType<PublishingExtension>().publications["release"])
        }
    }
}

tasks.register<Zip>("zipReleaseBundle") {
    dependsOn("publishReleasePublicationToBundleRepository")
    
    val groupId = getPublishProperty("PUBLISH_GROUP_ID", "com.opening-zone.software")
    val artifactId = getPublishProperty("PUBLISH_ARTIFACT_ID", "android-ads")
    val version = getPublishProperty("PUBLISH_VERSION", "1.0.0")
    
    val groupPath = groupId.replace('.', '/')
    
    // We zip from the root of 'build/repo' and preserve the folder structure!
    from(layout.buildDirectory.dir("repo")) {
        include("$groupPath/$artifactId/$version/**")
    }
    
    archiveFileName.set("$artifactId-$version-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/bundle"))
}

tasks.register("publishBundleToSonatype") {
    dependsOn("zipReleaseBundle")
    
    doLast {
        val username = getPublishProperty("PUBLISH_REPO_USERNAME")
        val password = getPublishProperty("PUBLISH_REPO_PASSWORD")
        val zipFile = file("${layout.buildDirectory.get().asFile}/outputs/bundle/android-ads-${getPublishProperty("PUBLISH_VERSION", "1.0.0")}-bundle.zip")
        
        if (username.isEmpty() || password.isEmpty()) {
            throw GradleException("Sonatype credentials (PUBLISH_REPO_USERNAME and PUBLISH_REPO_PASSWORD) must be configured in local.properties")
        }
        
        println("Uploading bundle ${zipFile.name} to Sonatype Central...")
        val base64Auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        
        project.exec {
            commandLine(
                "curl", "-s", "-w", "\nHTTP_STATUS:%{http_code}",
                "-X", "POST",
                "-H", "Authorization: Bearer $base64Auth",
                "-F", "bundle=@${zipFile.absolutePath}",
                "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
            )
            standardOutput = outputStream
            errorOutput = errorStream
            isIgnoreExitValue = true
        }
        
        val output = outputStream.toString().trim()
        val error = errorStream.toString().trim()
        
        println("Sonatype Response:\n$output")
        if (error.isNotEmpty()) {
            println("Errors:\n$error")
        }
        
        val httpStatus = output.split("\n").lastOrNull { line -> line.startsWith("HTTP_STATUS:") }
            ?.substringAfter("HTTP_STATUS:")?.trim()?.toIntOrNull() ?: 0
            
        if (httpStatus !in 200 until 300) {
            throw GradleException("Sonatype upload failed with HTTP Status: $httpStatus")
        } else {
            println("Successfully uploaded bundle to Sonatype Central!")
        }
    }
}

tasks.named("publish") {
    val repoUrl = getPublishProperty("PUBLISH_REPO_URL")
    if (repoUrl.contains("central.sonatype.com")) {
        dependsOn("publishBundleToSonatype")
    }
}


