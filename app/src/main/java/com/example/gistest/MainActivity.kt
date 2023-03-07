package com.example.gistest


import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.gistest.ui.theme.GISTestTheme
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

class MainActivity : ComponentActivity() {

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    private var resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
       result.handleLaunch()
    }

    private fun ActivityResult.handleLaunch() {
        when (resultCode) {
            // From  https://stackoverflow.com/questions/70850989/one-tap-sign-in-activity-result-with-jetpack-compose

            // If user clicked the X or outside the dialog
            RESULT_CANCELED -> {
            }

            // User clicked the sign in
            RESULT_OK -> {
                try {
                    val credential = oneTapClient.getSignInCredentialFromIntent(data)
                    val idToken = credential.googleIdToken
                    val userName = credential.id    // is actually email address?
                    val password = credential.password
                    when {
                        idToken != null -> {
                            // Got an ID token from Google. Use it to authenticate
                            // with your backend.
                            Log.d(TAG, "Got ID token: $idToken")
                        }
                        password != null -> {
                            // Got a saved username and password. Use them to authenticate
                            // with your backend.
                            Log.d(TAG, "Got password: $password")
                        }
                        else -> {
                            // Shouldn't happen.
                            Log.d(TAG, "No ID token or password!")
                        }
                    }

                } catch (e: ApiException) {
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> {
                            Log.d(TAG, "One-tap dialog was closed.")
                            // Don't re-prompt the user.
                            // showOneTapUI = false
                        }
                        CommonStatusCodes.NETWORK_ERROR -> {
                            Log.d(TAG, "One-tap encountered a network error.")
                            // Try again or just ignore.
                        }
                        else -> {
                            Log.d(TAG, "Couldn't get credential from result." +
                                    " (${e.localizedMessage})"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        oneTapClient = Identity.getSignInClient(this)

        setContent {
            GISTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {

                    Row {
                        GoogleButtonSignIn(
                            initializeSignInRequest = {
                                signInRequest = signInRequestBuildFromDocs(
                                    googleAuthWebClient = getString(R.string.google_auth_web_client_id)
                                )
                                oneTapClient.beginSignIn(
                                    signInRequest = signInRequest,
                                    resultLauncher = resultLauncher
                                )
                            }
                        )
                        GoogleButtonSignOut(oneTapClient)
                    }
                }
            }
        }
    }

    private fun signInRequestBuildFromDocs(googleAuthWebClient: String) =
        BeginSignInRequest.builder()
            .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                .setSupported(true)
                .build())
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    //
                    .setServerClientId(googleAuthWebClient)
                    // this set to True led to an error // now it seems ok?
                    .setFilterByAuthorizedAccounts(false)
                    .build())
            .setAutoSelectEnabled(false)
            .build()

    private fun SignInClient.beginSignIn(
        signInRequest: BeginSignInRequest,
        resultLauncher: ActivityResultLauncher<IntentSenderRequest>
    ) =
        beginSignIn(signInRequest)
            .addOnSuccessListener(this@MainActivity) { result ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent).build()
                resultLauncher.launch(intentSenderRequest)

                // From docs - deprecated
                /*startIntentSenderForResult(
                    result.pendingIntent.intentSender, REQ_ONE_TAP,
                    null, 0, 0, 0, null
                )*/
            } catch (e: IntentSender.SendIntentException) {
                // Log / Timber / Something else
                Log.d(TAG, "$e")
            }
        }
            .addOnFailureListener(this@MainActivity) { e ->
                /// (One Tap sign up does not show up if entering this block,
                /// I'm not sure what the docs are talking about)

                // Also, if exponential cool-down is happening, then this block
                // will be entered. See (google doc) for more

                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
                // Log.d(TAG, e.localizedMessage)
                Log.d(TAG, "$e")
            }


    companion object {
        // from FOXNow app ->  const val GOOGLE_RC_ONE_TAP_SIGN_IN = 9002
        const val TAG = "GoogleSignInTag"
        private const val REQ_ONE_TAP = 9002
    }
}

@Composable
fun GoogleButtonSignIn(
    initializeSignInRequest: () -> Unit
) {
    TextButton(
        onClick = {
            initializeSignInRequest()
        }
    ) {
        Text(text = "Sign In")
    }
}

@Composable
fun GoogleButtonSignOut(oneTapClient: SignInClient) {
    TextButton(
        onClick = {
            oneTapClient.signOut()
        }
    ) {
        Text(text = "Sign Out")
    }
}
