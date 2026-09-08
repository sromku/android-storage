plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.snatik.storage"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

mavenPublishing {
    coordinates("com.snatik", "storage", "3.0.0-SNAPSHOT")
    pom {
        name.set("android-storage")
        description.set("Create, read, append, copy, move, delete and encrypt files on Android with a small Kotlin API.")
        url.set("https://github.com/sromku/android-storage")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("sromku")
                name.set("Roman Kushnarenko")
                email.set("sromku@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/sromku/android-storage")
            connection.set("scm:git:git://github.com/sromku/android-storage.git")
            developerConnection.set("scm:git:ssh://git@github.com/sromku/android-storage.git")
        }
    }
}
