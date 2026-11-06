import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * 网关地址：优先取 Gradle 属性（gradle.properties / local.properties / -P 命令行），
 * 其次取环境变量，最后回落到 Android 模拟器访问宿主机的 10.0.2.2。
 */
val gatewayUrl: String = sequenceOf(
    rootProject.findProperty("sakura.gateway.url") as String?,
    providers.gradleProperty("sakura.gateway.url").orNull,
    providers.environmentVariable("SAKURA_GATEWAY_URL").orNull,
).firstOrNull { !it.isNullOrBlank() } ?: "http://10.0.2.2:8787"

// 签名材料不入库；文件不存在时 release 包只是不签名，而不是构建失败。
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")

android {
    namespace = "com.sakura.music"
    compileSdk = 36

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.sakura.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "GATEWAY_BASE_URL", "\"${gatewayUrl.trimEnd('/')}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * 播放页 Web 效果只把源码放进仓库，assets 由 `nowplaying-web/npm run build` 现场生成。
 * 缺失时直接让构建失败，避免编出一个「能装、但雾窗白屏」的包（静默降级）。
 */
val webAssetsDir = projectDir.resolve("src/main/assets/nowplaying")
val checkWebAssets = tasks.register("checkWebAssets") {
    group = "verification"
    description = "检查播放页 Web 效果产物是否已生成"
    // 不声明 inputs：产物是外部生成的，每次构建都实检一次，不能靠 Gradle 缓存跳过。
    doLast {
        val effectDirs = webAssetsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val incomplete = effectDirs.filter { dir ->
            !dir.resolve("index.html").isFile || !dir.resolve("${dir.name}.js").isFile
        }
        if (effectDirs.isEmpty() || incomplete.isNotEmpty()) {
            val detail = if (incomplete.isEmpty()) {
                "目录不存在或为空：$webAssetsDir"
            } else {
                "以下效果缺少 index.html 或 <id>.js：" + incomplete.joinToString { it.name }
            }
            throw GradleException(
                """
                |播放页 Web 效果产物缺失，$detail
                |
                |仓库只放源码，assets 不入库。请先生成一次：
                |    cd nowplaying-web
                |    npm ci
                |    npm run build
                |
                |GitHub Actions 会在每次构建前自动执行这一步。
                """.trimMargin(),
            )
        }
    }
}

// preBuild 是所有变体共用的入口，Debug / Release 都会先过这道检查。
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkWebAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // 后台播放与媒体通知；播放器活在服务里，界面只是它的遥控器。
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    // 直连平台 CDN 时要自己附加 Referer / User-Agent。
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
}
