plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // api：SakuClient 公开暴露 OkHttpClient 类型，app 端需要按同一版本编译
    // （否则 app 会误用 coil 传递的 okhttp 4.x，编译 API 与运行版本不一致）
    api(libs.okhttp)
    implementation(libs.jsoup)
    // 二维码解码（QrDecoder）；zxing 只在 core 内部使用，app 不直接依赖
    implementation(libs.zxing.core)
    // api：StoredCookie / SakuJson 是 core 的公开 API，上层（app）要用到序列化类型
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
}
