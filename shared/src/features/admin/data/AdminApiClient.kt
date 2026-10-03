package features.admin.data

import api.*
import core.network.BackendConfig
import core.network.PlatformHttp
import kotlinx.serialization.json.Json

/** Backend error with its HTTP status and machine code (see ErrorDto). */
class ApiFailure(val status: Int, val code: String, message: String) : Exception(message) {
    val isUnauthorized get() = status == 401
}

/** Authorization and gallery-management calls of the backend REST API. */
class AdminApiClient(private val baseUrl: () -> String = { BackendConfig.baseUrl }) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun login(username: String, password: String): Result<TokenResponse> =
        send("POST", ApiPaths.LOGIN, null, json.encodeToString(CredentialsRequest.serializer(), CredentialsRequest(username, password)))
            .mapCatching { json.decodeFromString(TokenResponse.serializer(), it) }

    /** Creates a mall; the server assigns ids. Returns the new mall id. */
    suspend fun createMall(token: String, mall: MallDto): Result<Long> =
        send("POST", ApiPaths.ADMIN_MALLS, token, json.encodeToString(MallDto.serializer(), mall)).mapCatching(::readId)

    suspend fun updateMall(token: String, mall: MallDto): Result<Long> =
        send("PUT", ApiPaths.adminMall(mall.id), token, json.encodeToString(MallDto.serializer(), mall)).mapCatching(::readId)

    suspend fun deleteMall(token: String, id: Long): Result<Unit> =
        send("DELETE", ApiPaths.adminMall(id), token, null).map { }

    private fun readId(body: String): Long =
        (json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject)["id"]
            .let { it as kotlinx.serialization.json.JsonPrimitive }.content.toLong()

    private suspend fun send(method: String, path: String, token: String?, body: String?): Result<String> {
        val headers = buildMap {
            if (body != null) put("Content-Type", "application/json")
            put("Accept", "application/json")
            if (token != null) put("Authorization", "Bearer $token")
        }
        val response = PlatformHttp.request(method, baseUrl().trimEnd('/') + path, headers, body)
            ?: return Result.failure(ApiFailure(0, "unreachable", "Backend is unreachable"))
        if (response.status in 200..299) return Result.success(response.body)
        val error = runCatching { json.decodeFromString(ErrorDto.serializer(), response.body) }.getOrNull()
        return Result.failure(
            ApiFailure(response.status, error?.error ?: "http_${response.status}", error?.message ?: "HTTP ${response.status}")
        )
    }
}
