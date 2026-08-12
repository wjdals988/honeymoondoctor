import groovy.json.JsonSlurper
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Firebase 설정 파일(google-services.json)이 없어도 빌드가 실패하지 않도록,
// 파일이 존재할 때만 google-services 플러그인을 적용한다.
val firebaseConfigFile = file("google-services.json")
val hasFirebaseConfig = firebaseConfigFile.exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

// google-services.json에서 Google 로그인(Credential Manager)에 필요한 "웹 클라이언트 ID"
// (client_type == 3)만 추출한다. 파일이 없으면 빈 문자열로 두어 데모 모드 빌드가 실패하지 않게 한다.
// R.string 리소스가 아니라 BuildConfig로 노출하는 이유: google-services 플러그인이 생성하는
// 리소스와 이름이 겹치면 리소스 병합 충돌이 날 수 있어, 아예 별도 경로로 우회한다.
//
// providers.fileContents(...)로 읽는 이유: file.readText()나 JsonSlurper().parse(File)처럼
// 파일을 직접 읽으면 Configuration Cache(gradle.properties의 org.gradle.configuration-cache=true)가
// 이 파일을 입력으로 추적하지 못해, 캐시가 재사용될 때 이 값이 빈 문자열로 굳어버리는 실제
// 버그를 겪었다(실기기에서 "GOOGLE_WEB_CLIENT_ID가 설정되지 않았습니다" 오류로 발견).
// Gradle의 Provider API를 통해 읽어야 Configuration Cache가 파일 변경/캐시 무효화를 올바르게 추적한다.
@Suppress("UNCHECKED_CAST")
fun extractGoogleWebClientId(): String {
    val content = providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull
        ?: return ""
    val root = JsonSlurper().parseText(content) as Map<String, Any?>
    val clients = root["client"] as? List<Map<String, Any?>> ?: emptyList()
    val oauthClients = clients.firstOrNull()?.get("oauth_client") as? List<Map<String, Any?>> ?: emptyList()
    val webClient = oauthClients.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
    return (webClient?.get("client_id") as? String).orEmpty()
}

val googleWebClientId: String = if (hasFirebaseConfig) extractGoogleWebClientId() else ""

// 서명 설정 (release 서명 정보는 로컬 keystore.properties에서만 읽는다. 커밋 금지)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigning = keystorePropertiesFile.exists()
if (hasReleaseSigning) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.jeongmin.honeymoondoctor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jeongmin.honeymoondoctor"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfig.toString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // 개인 2인용 앱이라 release와 동시 설치할 일이 없어, Firebase 앱 등록을 하나로
            // 유지하기 위해 접미사를 붙이지 않는다(패키지명 com.jeongmin.honeymoondoctor로 통일).
            buildConfigField("boolean", "IS_DEMO_FORCED", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "IS_DEMO_FORCED", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("test") {
            // 시드 JSON을 Android Context 없이도 유닛테스트에서 읽을 수 있도록 클래스패스에 포함한다.
            resources.directories += "src/main/assets"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.play.services.location)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
}
