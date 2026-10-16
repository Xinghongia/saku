package top.sakuraxy.client.core

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 上传文件（头像 / 封面 / 收款码 / 私信图片）。
 *
 * 站点限制（2026-10-16 实测 modal 表单）：jpg/gif/png，最大 3M；
 * 由调用方（app 层）负责在超限时压缩后再交给这里。
 */
data class UploadFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is UploadFile && fileName == other.fileName && mimeType == other.mimeType &&
                bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * (31 * fileName.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}

/**
 * 构造 zibll mini-upload 同款 multipart 表单：文件字段名由调用方给出
 * （头像/封面表单实测为 `file`，收款码等带 `data-tag` 的字段以后端表单为准），
 * 隐藏字段（action/_wpnonce/user_id 等）原样附上 [fields]。
 *
 * 纯函数（不发网络请求），可离线单测。
 */
internal fun buildMultipartBody(
    fields: Map<String, String>,
    files: List<Pair<String, UploadFile>>,
): RequestBody {
    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
    fields.forEach { (name, value) -> builder.addFormDataPart(name, value) }
    files.forEach { (fieldName, file) ->
        builder.addFormDataPart(
            fieldName,
            file.fileName,
            file.bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()),
        )
    }
    return builder.build()
}
