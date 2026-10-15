package top.sakuraxy.client.core

import kotlin.random.Random
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * zibll 主题的滑块拼图验证（machine-verification=slider）。
 *
 * 2026-10-15 二次实测（research/probe/：captcha.min.js / slidercaptcha.min.js / main.min.js
 * 与 curl 全流程复现）——算法与网页完全一致，不含任何伪造：
 *
 * 1. 网页随机出题：x ∈ [73,217]（拼图缺口位置），按 zibll 的编码生成 randstr 发到
 *    `wp-content/themes/zibll/action/captcha.php`，换回 token / rand_str / check；
 * 2. 用户在拼图上把滑块拖到缺口位置，拖动时记录纵向轨迹；
 * 3. 松手后把「拖到哪了」（distance）+ 轨迹 + check 一并提交给登录接口；
 * 4. 服务端核对 distance 与出题时的 x（容差与网页一致，实测偏 6px 通过、偏 50px 被拒）。
 *
 * 客户端只复刻网页行为：数据全部来自真实拖动，不做任何伪造。
 * 站点若关闭滑块（登录页无 captcha_mode），登录不携带本类数据。
 */
object SliderCaptcha {

    private const val CAPTCHA_PATH: String = "/wp-content/themes/zibll/action/captcha.php"

    fun captchaEndpoint(baseUrl: String): String = "$baseUrl$CAPTCHA_PATH"

    /** 拼图区逻辑坐标系（与网页 slidercaptcha 完全一致） */
    const val CANVAS_WIDTH = 280
    const val CANVAS_HEIGHT = 170

    /** 拼图块尺寸（网页 n = sliderL + 2*sliderR + 3 = 42 + 18 + 3） */
    const val PIECE_SIZE = 63

    /** 缺口横向取值范围 [73, 217]（网页 getX(n+10, width-(n+10))） */
    const val MIN_X = 73
    const val MAX_X = CANVAS_WIDTH - (PIECE_SIZE + 10)

    /** 缺口纵向取值范围 [28, 97]（网页 y = n(10+2*sliderR, height-(n+10))） */
    const val MIN_Y = 28
    const val MAX_Y = CANVAS_HEIGHT - (PIECE_SIZE + 10)

    /** 拖动量 a ∈ [0, 240] 与滑块视觉位置 d 的换算比（网页 (width-40-20)/(width-40)） */
    private const val DRAG_NUM = CANVAS_WIDTH - 40 - 20
    private const val DRAG_DEN = CANVAS_WIDTH - 40
    const val MAX_DRAG = DRAG_DEN

    /** 对齐容差：|d - x| < 8 视为拖动到位（网页 options.offset） */
    const val OFFSET = 8

    /** 背景图共 21 张（网页 img/captcha/0.jpg ~ 20.jpg 随机取一张） */
    const val IMAGE_COUNT = 21

    fun imageUrl(index: Int): HttpUrl =
        "${Site.BASE_URL}/wp-content/themes/zibll/img/captcha/$index.jpg".toHttpUrl()

    // ---------- 数据模型 ----------

    /** 一道滑块题：缺口位置 + 服务端下发的三项凭据 */
    data class Challenge(
        /** 缺口 x（逻辑坐标），由本客户端随机出题，与网页相同 */
        val x: Int,
        /** 缺口 y（逻辑坐标） */
        val y: Int,
        /** 背景图序号 0..20 */
        val imageIndex: Int,
        val token: String,
        val randStr: String,
        val check: String,
    )

    /** 用户完成拖动后生成的提交数据（对应网页 window.captcha 对象） */
    data class Solution(
        val ticket: String,
        val randstr: String,
        val spliced: Boolean,
        /** 拖动过程中的纵向偏移轨迹（逻辑坐标整数） */
        val trail: List<Int>,
        val check: String,
    )

    /** 拖动结果（由 App 的滑块 UI 提供，坐标已换算到逻辑体系） */
    data class DragResult(
        /** 松手时的滑块视觉位置 d = 220/240 * a，取整 */
        val distance: Int,
        /** 拖动过程中手指的纵向偏移（逻辑坐标取整），每次移动记录一次 */
        val trail: List<Int>,
    )

    // ---------- 出题（对应网页 getX） ----------

    /** 生成一道题的本地部分（x/y/图片），不联网；便于离线单测与 UI 先画图 */
    fun newLocalChallenge(random: Random = Random.Default): Challenge {
        val x = random.nextInt(MIN_X, MAX_X + 1)
        val y = random.nextInt(MIN_Y, MAX_Y + 1)
        val imageIndex = random.nextInt(0, IMAGE_COUNT)
        return Challenge(x, y, imageIndex, token = "", randStr = "", check = "")
    }

    /**
     * 按网页算法编码 randstr（内嵌出题数字），仅用于请求题目。
     * 结构：`<11..40><n个小写字母><数字><m个小写字母><11..40>`（n/m 即前后两个数）
     */
    internal fun encodeRandstr(value: Int, random: Random): String {
        val e = random.nextInt(11, 41)
        val i = random.nextInt(11, 41)
        return buildString {
            append(e)
            append(randomLetters(e, random))
            append(value)
            append(randomLetters(i, random))
            append(i)
        }
    }

    private fun randomLetters(count: Int, random: Random): String =
        buildString(count) { repeat(count) { append(('a' + random.nextInt(26))) } }

    /** 拖动完成后生成 ticket（对应网页 i(t.distance)，结构同 randstr 编码） */
    internal fun encodeTicket(distance: Int, random: Random): String = encodeRandstr(distance, random)

    /**
     * 生成提交用的 randstr 字段（对应网页 `o + rand_str.substring(o, r) + r`）。
     * 取 rand_str 的 [o, r) 子串并用两个数字包裹，证明客户端确实拿到过服务端下发的 rand_str。
     */
    internal fun encodeRandstrField(randStr: String, random: Random): String {
        val o = random.nextInt(1, 10)
        val r = random.nextInt(15, 26)
        val safeR = r.coerceAtMost(randStr.length)
        val start = o.coerceAtMost(safeR)
        return "$o${randStr.substring(start, safeR)}$r"
    }

    /** 由拖动结果生成完整提交数据（spliced = |d - x| < 8，与网页一致） */
    fun solve(challenge: Challenge, drag: DragResult, random: Random = Random.Default): Solution =
        Solution(
            ticket = encodeTicket(drag.distance, random),
            randstr = encodeRandstrField(challenge.randStr, random),
            spliced = isAligned(drag.distance, challenge.x),
            trail = drag.trail,
            check = challenge.check,
        )

    /** 拖动是否到位：|d - x| < OFFSET（网页 verify().spliced） */
    fun isAligned(distance: Int, x: Int): Boolean = kotlin.math.abs(distance - x) < OFFSET

    /** 拖动距离 a（触点位移，逻辑坐标）→ 滑块视觉位置 d（网页 220/240 换算） */
    fun dragToDistance(dragAmount: Float): Int {
        val clamped = dragAmount.coerceIn(0f, MAX_DRAG.toFloat())
        return (DRAG_NUM.toFloat() / DRAG_DEN * clamped).toInt()
    }

    // ---------- 联网：换取服务端凭据 ----------

    /**
     * 请求 captcha.php 换取 token / rand_str / check。
     * 请求携带的 randstr 内嵌题目数字（x），服务端据此得到"答案"。
     */
    suspend fun requestChallenge(
        client: OkHttpClient,
        local: Challenge,
        random: Random = Random.Default,
        baseUrl: String = Site.BASE_URL,
    ): Challenge {
        val url = captchaEndpoint(baseUrl).toHttpUrl().newBuilder()
            .addQueryParameter("type", "slider")
            .addQueryParameter("randstr", encodeRandstr(local.x, random))
            .build()
        val body = client.getText(url)
        val json = try {
            SakuJson.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw SakuHttpException(200, url.toString(), "滑块数据不是 JSON：${body.snippet(120)}")
        }
        fun field(name: String): String =
            json[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw SakuHttpException(200, url.toString(), "滑块数据缺少字段 $name")
        return local.copy(
            token = field("token"),
            randStr = field("rand_str"),
            check = field("check"),
        )
    }
}
