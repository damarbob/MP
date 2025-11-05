package id.monpres.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import id.monpres.app.MainApplication.Companion.APP_REGION
import id.monpres.app.MainApplication.Companion.userRegion
import id.monpres.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class LoginActivity : AppCompatActivity() {

    companion object {
        private val TAG = LoginActivity::class.java.simpleName
    }

    /* Variables */
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    /* Views */
    private lateinit var binding: ActivityLoginBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentActivityLogin.id) as NavHostFragment
        navController = navHostFragment.navController

        /* Region validation */
        if (userRegion != APP_REGION) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.unsupported_region))
                .setMessage(
                    getString(
                        R.string.this_app_version_is_only_supported_in_s_your_sim_card_region_is_s,
                        APP_REGION,
                        userRegion
                    )
                )
                .setPositiveButton(R.string.continue_) { _, _ ->
                    // The user forces to continue

                    // Run auth
                    runAuthentication()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> exitProcess(0) }
                .setCancelable(false)
                .show()
        }
        // If the user region is the same as the application region
        else {
            // Run auth
            runAuthentication()
        }
    }

    private fun runAuthentication() {
        /* Auth */
        credentialManager = CredentialManager.create(this)
        auth = Firebase.auth // Initialize Firebase Auth
        val currentUser = auth.currentUser // Get current user

        if (currentUser != null) {
            Log.d(TAG, "Logged in. Navigating to user dashboard.")

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            this.finish()  // Finish the current activity so the user can't navigate back to the login screen
        }
    }

    fun signIn() {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                    .build()
            )
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Toast.makeText(this@LoginActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error getting credential", e)
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        // Handle the successfully returned credential.
        when (val credential = result.credential) {

            // GoogleIdToken credential
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Use googleIdTokenCredential and extract the ID to validate and
                        // authenticate on your server.
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)

                        firebaseAuthWithGoogle(googleIdTokenCredential.idToken)

                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e(TAG, "Unexpected type of credential")
                }
            }

            else -> {
                // Catch any unrecognized credential type here.
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Toast.makeText(
                        this,
                        getString(R.string.signed_in_as, user?.displayName), Toast.LENGTH_SHORT
                    ).show()
                    this.startActivity(Intent(this, MainActivity::class.java))
                    this.finish()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.authentication_failed, task.exception?.localizedMessage),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}