plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.victorarana.leo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.victorarana.leo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric's FileDescriptor interceptor reaches into a JDK-internal package (needed on JDK 21 too).
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        lintConfig = file("lint.xml")
    }
}

// The exported schema JSON per version, committed so a migration can be written and tested against it.
room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    // One folder per test class, like the iOS "__Snapshots__" directories.
    outputDir.set(layout.projectDirectory.dir("src/test/snapshots"))
}

// Privacy promise: no INTERNET permission may survive manifest merging, in any variant.
// Wired as a transform of the merged manifest, so everything downstream of it (packaging, assemble<Variant>) runs it.
abstract class VerifyNoInternetPermission : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val verifiedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        // Match the element (uses-permission or uses-permission-sdk-23), not any mention such as a comment.
        val internetPermission =
            Regex("""<uses-permission(-sdk-23)?\s[^>]*android:name="android\.permission\.INTERNET"""")
        check(!internetPermission.containsMatchIn(manifest.readText())) {
            "Merged manifest ${manifest.path} declares android.permission.INTERNET, Leo must not."
        }
        manifest.copyTo(verifiedManifest.get().asFile, overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val taskName = "verify${variant.name.replaceFirstChar { it.uppercase() }}NoInternetPermission"
        val verify = tasks.register<VerifyNoInternetPermission>(taskName)
        variant.artifacts
            .use(verify)
            .wiredWithFiles(VerifyNoInternetPermission::mergedManifest, VerifyNoInternetPermission::verifiedManifest)
            .toTransform(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        tasks.named("check") { dependsOn(verify) }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.genai.prompt)
    ksp(libs.androidx.room.compiler)
    ksp(libs.mlkit.genai.schema.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
