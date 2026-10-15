package top.sakuraxy.client.core

/** 读取 core/src/test/resources/fixtures 下的样本文件 */
internal fun fixture(name: String): String =
    checkNotNull(GolinkTest::class.java.getResourceAsStream("/fixtures/$name")) {
        "缺少测试样本 fixtures/$name"
    }.use { it.readBytes().toString(Charsets.UTF_8) }
