package top.sakuraxy.client

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import top.sakuraxy.client.core.SakuJson
import top.sakuraxy.client.core.StoredCookie

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** 登录态的本地持久化（cookie 快照；不含密码） */
class SessionStore(private val context: Context) {

    private val cookieKey = stringPreferencesKey("cookies_json")
    private val cookieListSerializer = ListSerializer(StoredCookie.serializer())

    suspend fun load(): List<StoredCookie> {
        val raw = context.sessionDataStore.data.first()[cookieKey] ?: return emptyList()
        return runCatching { SakuJson.decodeFromString(cookieListSerializer, raw) }
            .getOrDefault(emptyList())
    }

    suspend fun save(cookies: List<StoredCookie>) {
        context.sessionDataStore.edit { it[cookieKey] = SakuJson.encodeToString(cookieListSerializer, cookies) }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(cookieKey) }
    }
}
