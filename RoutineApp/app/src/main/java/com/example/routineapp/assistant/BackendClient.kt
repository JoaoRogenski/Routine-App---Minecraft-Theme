package com.example.routineapp.assistant

import com.example.routineapp.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class BackendException(message: String) : Exception(message)

/**
 * Talks to the project's own backend (backend/), which holds the AI and telephony secrets.
 * The app only stores the backend URL and a per-user access token.
 */
class BackendClient(private val settings: SettingsRepository) {
    val isConfigured get() = settings.current.backendUrl.isNotBlank()

    suspend fun parse(body: JSONObject): JSONObject = post("/v1/parse", body)

    suspend fun requestCall(phone: String, taskTitle: String, idempotencyKey: String): JSONObject =
        post("/v1/call", JSONObject().put("to", phone).put("taskTitle", taskTitle).put("idempotencyKey", idempotencyKey))

    suspend fun health(): JSONObject = request("GET", "/v1/health", null)

    private suspend fun post(path: String, body: JSONObject) = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val s = settings.current
        if (s.backendUrl.isBlank()) throw BackendException("Servidor não configurado")
        val conn = try {
            URL(s.backendUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw BackendException("Endereço do servidor inválido")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer ${s.backendToken}")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299) throw BackendException(json?.optString("error")?.ifBlank { null }
                ?: when (code) { 401, 403 -> "Token do servidor inválido"; else -> "Servidor respondeu com erro $code" })
            json ?: throw BackendException("Resposta inválida do servidor")
        } catch (e: IOException) {
            throw BackendException("Sem conexão com o servidor")
        } finally {
            conn.disconnect()
        }
    }
}
