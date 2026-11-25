import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(projects.sharedUI)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.filmlens.nicolink"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.filmlens.nicolink"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        // 只保留 arm64-v8a：显影引擎 .so 仅有 arm64，减小包体、避免装入不兼容 ABI
        ndk { abiFilters += listOf("arm64-v8a") }
        // 桌面图标名称：正式版 NicoLink，Canary 版由 debug buildType 覆盖
        manifestPlaceholders["appLabel"] = "NicoLink"
    }

    // 签名密钥不入库：从环境变量 / CI Secret 注入，GitHub 公开仓库零密钥风险。
    // 本地开发未配置环境变量时回退到 Android debug 默认签名，保证 assembleDebug 可用。
    // 详见 docs/SIGNING.md。
    val keystoreB64 = System.getenv("SIGNING_KEYSTORE_BASE64")
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    val storePass = System.getenv("SIGNING_STORE_PASSWORD")
    val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
    val keyAliasEnv = System.getenv("SIGNING_KEY_ALIAS")
    val hasReleaseKeystore = !keystoreB64.isNullOrBlank() || !keystorePath.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                // 优先 base64（CI Secret），其次文件路径（本地/CI 挂载）
                storeFile = if (!keystoreB64.isNullOrBlank()) {
                    val f = file("${layout.buildDirectory.get().asFile}/keystore/release.jks")
                    f.parentFile.mkdirs()
                    f.writeBytes(Base64.getDecoder().decode(keystoreB64))
                    f
                } else {
                    file(keystorePath!!)
                }
                storePassword = storePass ?: ""
                keyAlias = keyAliasEnv ?: ""
                keyPassword = keyPass ?: ""
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("debug") {
            // Canary 版：独立 applicationId，可与正式版同机共存、数据隔离
            applicationIdSuffix = ".canary"
            manifestPlaceholders["appLabel"] = "NicoLink Canary"
            // 本地未注入密钥时用 Android debug 默认签名；注入后用 release 签名
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

}

// 正式版 APK 文件名带版本号与 ABI：NicoLink-v<versionName>-arm64.apk
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val ver = variant.outputs.firstOrNull()?.versionName?.get() ?: "0.1.0"
        variant.outputs.forEach { output ->
            @Suppress("UNCHECKED_CAST")
            (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                "NicoLink-v$ver-arm64.apk"
            )
        }
    }
}
