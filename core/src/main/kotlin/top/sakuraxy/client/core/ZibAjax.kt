package top.sakuraxy.client.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody

/**
 * zibll admin-ajax / action.php 的通用响应模型。
 *
 * 站点约定（2026-10-16 实测）：`{"error":0,"msg":"…"}` 或 `{"error":false,…}` 为成功；
 * `error` 为 true / 非 0 数字时为失败，文案在 `msg`/`message`；要求人机验证时的文案
 * 与登录一致（「环境异常」「人机验证」等，见 [looksLikeCaptcha]）。
 */
sealed interface AjaxResult {
    /** 成功；[json] 保留完整响应供读取 url/details_link 等附加字段 */
    data class Ok(val msg: String, val json: JsonObject) : AjaxResult

    /** 站点明确拒绝（error 非 0），[msg] 为站点原文 */
    data class Fail(val msg: String) : AjaxResult

    /** 需要完成滑块验证（error 文案含验证类关键词），调用方应重走滑块后重试 */
    data class NeedsCaptcha(val msg: String) : AjaxResult

    /** 非 JSON 响应（可能被安全层拦截或返回 HTML），保留片段便于排查 */
    data class NotJson(val snippet: String) : AjaxResult
}

/** 解析 zibll ajax 响应（纯函数，离线可测） */
internal fun parseAjaxResponse(status: Int, body: String): AjaxResult {
    if (status !in 200..299) return AjaxResult.Fail("服务器返回 HTTP $status")
    val json = try {
        SakuJson.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return AjaxResult.NotJson(body.snippet(160))
    }
    val error = json["error"]?.jsonPrimitive
    val msg = json["msg"]?.jsonPrimitive?.contentOrNull
        ?: json["message"]?.jsonPrimitive?.contentOrNull
        ?: ""
    return when {
        error == null -> AjaxResult.Fail("无法识别的响应：${body.snippet(120)}")
        error.isFalsey() -> AjaxResult.Ok(msg, json)
        msg.looksLikeCaptcha() -> AjaxResult.NeedsCaptcha(msg)
        else -> {
            val code = error.intOrNull ?: 1
            AjaxResult.Fail(msg.ifBlank { "操作失败（error=$code）" })
        }
    }
}

private fun JsonPrimitive.isFalsey(): Boolean =
    booleanOrNull == false || intOrNull == 0

/**
 * 服务端把「环境异常，请刷新后重新提交」（缺滑块数据）与
 * 「环境异常，人机验证失败」（拖动未对齐）等都归入需要重新走滑块的类别。
 */
internal fun String.looksLikeCaptcha(): Boolean =
    listOf("验证", "captcha", "滑动", "拼图", "环境异常").any { contains(it, ignoreCase = true) }

/**
 * 把滑块验证数据并入表单 —— 对应网页 zib_ajax 把 window.captcha 对象
 * 合并进提交数据的行为（jQuery 序列化为 `captcha[ticket]=…` 等字段）。
 * 登录、账户安全、投稿等凡带 captcha_mode 的表单提交共用。
 */
internal fun FormBody.Builder.addCaptcha(solution: SliderCaptcha.Solution): FormBody.Builder = apply {
    add("captcha[ticket]", solution.ticket)
    add("captcha[randstr]", solution.randstr)
    add("captcha[spliced]", solution.spliced.toString())
    // 与 JS JSON.stringify([1,2]) 相同的紧凑数组格式
    add("captcha[trail]", solution.trail.joinToString(",", "[", "]"))
    add("captcha[check]", solution.check)
}

/**
 * 合并表单提交数据（网页 zib_ajax 序列化行为的等价物）：
 * 隐藏字段原样转发 + 下拉取选中值 + [values] 覆盖用户编辑的字段 + action。
 * 账户安全、注册、找回密码等所有 zib_ajax 表单提交共用。
 */
internal fun mergeFormValues(
    form: PanelForm,
    values: Map<String, String>,
    action: String,
): LinkedHashMap<String, String> {
    val merged = linkedMapOf<String, String>()
    form.fields.forEach { (k, v) -> if (k != "action") merged[k] = v }
    form.selects.forEach { select -> select.selectedValue?.let { merged[select.name] = it } }
    merged.putAll(values)
    merged["action"] = action
    return merged
}

/** [mergeFormValues] + 滑块数据 → 可直接 POST 的表单体 */
internal fun buildAjaxFormBody(
    form: PanelForm,
    values: Map<String, String>,
    action: String,
    captcha: SliderCaptcha.Solution?,
): FormBody {
    val body = FormBody.Builder()
    mergeFormValues(form, values, action).forEach { (k, v) -> body.add(k, v) }
    captcha?.let { body.addCaptcha(it) }
    return body.build()
}
