import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Force 16KB-aligned version of the native library that provides libandroidx.graphics.path.so
configurations.configureEach {
    resolutionStrategy {
        force("androidx.graphics:graphics-path:1.1.0")
    }
}

kotlin {
    androidLibrary {
        namespace = "com.filmlens.nicolink.sharedUI"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material)
            implementation(libs.compose.icons.core)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.coil.core)
            implementation(libs.coil.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.backdrop)
            implementation(libs.shapes)
            implementation(libs.zoomable)
            // Apache-2.0：App 内 .mov/.mp4 播放（与 GPLv3 显影引擎物理隔离，无许可冲突）
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            // 云端账号/热力同步 API 客户端（与 lazyfilm 同款 OkHttp）
            implementation(libs.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
