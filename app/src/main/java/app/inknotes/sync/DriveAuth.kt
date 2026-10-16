package app.inknotes.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google authorization for the drive.appdata scope only. The AuthorizationClient shows its own
 * account picker and consent screen, so no separate sign-in step is needed.
 */
class DriveAuth(context: Context) {

    private val client: AuthorizationClient = Identity.getAuthorizationClient(context)

    /** Returns a result that either holds an access token or needs [AuthorizationResult.getPendingIntent] shown. */
    suspend fun authorize(account: String?): AuthorizationResult {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .apply { if (account != null) setAccount(Account(account, ACCOUNT_TYPE)) }
            .build()
        return client.authorize(request).await()
    }

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    /** A token for background work, or null if the user must grant access again. */
    suspend fun tokenWithoutUi(account: String?): String? {
        val result = authorize(account)
        return if (result.hasResolution()) null else result.accessToken
    }

    suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    suspend fun revoke(account: String) {
        client.revokeAccess(
            RevokeAccessRequest.builder()
                .setAccount(Account(account, ACCOUNT_TYPE))
                .setScopes(listOf(Scope(DRIVE_APPDATA)))
                .build()
        ).await()
    }

    private companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val ACCOUNT_TYPE = "com.google"
    }
}
