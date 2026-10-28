package com.epiclibrary.mobile.data

import android.net.Uri
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Epic authentication adapter modeled after the currently used Legendary flow.
 * The app never asks for the user's Epic password; authentication happens in
 * the system browser, and the user brings the one-time authorization code back.
 */
class EpicAuth(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
        private const val CLIENT_SECRET = "daafbccc737745039dffe53d94fc76cf"
        private const val OAUTH_HOST = "https://account-public-service-prod03.ol.epicgames.com"
        private const val AUTHORIZE_REDIRECT =
            "https://www.epicgames.com/id/api/redirect?clientId=$CLIENT_ID&responseType=code"
        private const val USER_AGENT = "EpicLibraryMobile/0.2"
    }

    fun buildLoginUrl(): Uri = Uri.parse("https://www.epicgames.com/id/login")
        .buildUpon()
        .appendQueryParameter("redirectUrl", AUTHORIZE_REDIRECT)
        .build()

    suspend fun exchangeAuthorizationCode(code: String): EpicToken =
        requestToken(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("token_type", "eg1")
                .build()
        )

    suspend fun refresh(refreshToken: String): EpicToken =
        requestToken(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("token_type", "eg1")
                .build()
        )

    suspend fun verify(accessToken: String): EpicAccount {
        val request = Request.Builder()
            .url("$OAUTH_HOST/account/api/oauth/verify")
            .header("Authorization", "bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw authFailure(response.code, body)
        val json = JSONObject(body)
        return EpicAccount(
            accountId = json.optString("account_id"),
            displayName = json.optString("displayName").ifBlank { json.optString("email") }
        )
    }

    private suspend fun requestToken(form: FormBody): EpicToken {
        val request = Request.Builder()
            .url("$OAUTH_HOST/account/api/oauth/token")
            .header("Authorization", Credentials.basic(CLIENT_ID, CLIENT_SECRET))
            .header("User-Agent", USER_AGENT)
            .post(form)
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw authFailure(response.code, body)
        val json = JSONObject(body)
        return EpicToken(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").ifBlank { null },
            expiresInSeconds = json.optLong("expires_in", 7200L)
        )
    }
private fun authFailure(statusCode: Int, responseBody: String): EpicAuthException {
        val errorCode = runCatching {
            val json = JSONObject(responseBody)
            json.optString("errorCode").ifBlank { json.optString("error") }
        }.getOrDefault("")
        val rejected = statusCode in 400..403 && listOf(
            "invalid_grant",
            "invalid_refresh_token",
            "invalid_token",
            "token_expired"
        ).any { errorCode.contains(it, ignoreCase = true) }
        return EpicAuthException(statusCode = statusCode, refreshRejected = rejected)
    }
}

data class EpicAuthException(
    val statusCode: Int,
    val refreshRejected: Boolean
) : IOException("Epic authentication failed ($statusCode)")

data class EpicToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long
)

data class EpicAccount(
    val accountId: String,
    val displayName: String
)
