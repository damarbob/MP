package id.monpres.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.postOnAnimationDelayed
import androidx.core.view.updatePadding
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.transition.TransitionManager
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.SkeletonConfig
import com.faltenreich.skeletonlayout.createSkeleton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFade
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.MainApplication.Companion.APP_REGION
import id.monpres.app.MainApplication.Companion.userRegion
import id.monpres.app.databinding.ActivityLoginBinding
import id.monpres.app.enums.Language
import id.monpres.app.enums.ThemeMode
import id.monpres.app.libraries.ErrorLocalizer
import id.monpres.app.module.CoroutineModule
import id.monpres.app.repository.AppPreferences
import id.monpres.app.usecase.GetUserVerificationStatusUseCase
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class LoginActivity : AppCompatActivity(R.layout.activity_login) {

    companion object {
        private val TAG = LoginActivity::class.java.simpleName
    }

    private var loginJob: Job? = null
    private val viewModel: AuthViewModel by viewModels()

    /* Variables */
    @Inject
    lateinit var credentialManager: CredentialManager

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    @CoroutineModule.ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var getUserVerificationStatusUseCase: GetUserVerificationStatusUseCase

    /* Views */
    private val binding by viewBinding(ActivityLoginBinding::bind)
    private lateinit var navController: NavController
    private lateinit var skeleton: Skeleton
    private var isNavigatedToMainActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupWindowInsets()

        applyInitialSettings()

        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentActivityLogin.id) as NavHostFragment
        navController = navHostFragment.navController

        skeleton = binding.navHostFragmentActivityLogin.createSkeleton(
            SkeletonConfig.default(
                this, R.layout.skeleton_mask
            )
        )

        checkRegionAndInitialize()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            // Apply padding to the root for navigation drawer cutouts
            v.setPadding(insets.left, 0, insets.right, 0)

            // NavHostFragment (main content)
            binding.navHostFragmentActivityLogin.updatePadding(
                top = insets.top,
                left = insets.left,
                right = insets.right
            )

            binding.activityLoginContainerLoading.updatePadding(
                top = insets.top,
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right
            )

            WindowInsetsCompat.Builder(windowInsets).setInsets(
                WindowInsetsCompat.Type.systemBars(),
                Insets.of(0, insets.top, 0, insets.bottom)
            ).build()
        }
    }

    private fun checkInitialUserState() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // We have a logged-in user, check verification status
            if (currentUser.isEmailVerified) {
                lifecycleScope.launch {
                    Log.d(
                        TAG,
                        "User is verified - checking verification status: ${System.currentTimeMillis()}"
                    )
                    viewModel.checkInitialAdminVerificationStatus(
                        onVerified = { navigateToMainActivity() },
                        onPendingOrRejected = {
                            navController.navigate(NavLoginDirections.actionGlobalAdminVerificationFragment())
                        })
                }
            } else {
                // User is not verified - navigate to email verification immediately
                if (navController.currentDestination?.id != R.id.emailVerificationFragment) {
                    Log.d(
                        TAG,
                        "User is not verified - navigating to email verification (called by checkInitialUserState)"
                    )
                    navController.navigate(NavLoginDirections.actionGlobalEmailVerificationFragment())
                }
            }
        } else {
            // No user - ensure we're on login fragment
            if (navController.currentDestination?.id != R.id.loginFragment && navController.currentDestination?.id != R.id.registerFragment) {
                navController.navigate(NavLoginDirections.actionGlobalLoginFragment())
            }
        }
    }

    private fun setupObservers() {
        // Observe authentication state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collectLatest { authState ->
                    Log.d(
                        TAG,
                        "Auth state changed: $authState, current time: ${System.currentTimeMillis()}"
                    )
                    when (authState) {
                        is AuthViewModel.AuthState.FullyVerified -> {
                            // Email is verified, navigate to MainActivity
                            navigateToMainActivity()
                        }

                        is AuthViewModel.AuthState.EmailNotVerified -> {
                            // Email not verified, show verification fragment
                            if (navController.currentDestination?.id != R.id.emailVerificationFragment) {
                                Log.d(
                                    TAG,
                                    "Email not verified - navigating to email verification (called by setupObservers)"
                                )
                                navController.navigate(NavLoginDirections.actionGlobalEmailVerificationFragment())
                            }
                            showLoadingContainer(false)
                        }

                        is AuthViewModel.AuthState.Unauthenticated -> {
                            // User signed out or not logged in, ensure we're on login fragment
                            if (navController.currentDestination?.id != R.id.loginFragment && navController.currentDestination?.id != R.id.registerFragment) {
                                navController.navigate(NavLoginDirections.actionGlobalLoginFragment())
                            }
                            showLoadingContainer(false)
                        }

                        is AuthViewModel.AuthState.Error -> {
                            // Handle error
                            // Error message handled by errorEvent
                            showLoadingContainer(false)
                        }

                        is AuthViewModel.AuthState.AdminVerificationPending, is AuthViewModel.AuthState.AdminVerificationRejected -> {
                            if (navController.currentDestination?.id != R.id.adminVerificationFragment) {
                                navController.navigate(NavLoginDirections.actionGlobalAdminVerificationFragment())
                            }
                            showLoadingContainer(false)
                        }

                        is AuthViewModel.AuthState.Loading -> {
                            if (viewModel.loginState.value != AuthViewModel.LoginState.Loading || viewModel.registerState.value != AuthViewModel.RegisterState.Loading) {
                                showLoadingContainer(true)
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect { exception ->
                    Toast.makeText(
                        this@LoginActivity,
                        ErrorLocalizer.getLocalizedError(this@LoginActivity, exception),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showLoadingContainer(isLoading: Boolean) {
//        TransitionManager.beginDelayedTransition(
//            binding.root, MaterialFade()
//        )
//        skeleton.showOriginal()
        if (!isLoading) {
            binding.activityLoginContainerLoading.postOnAnimationDelayed(150L) {
                TransitionManager.beginDelayedTransition(
                    binding.root, MaterialFade()
                )
                binding.activityLoginContainerLoading.visibility = View.GONE
            }
        } else {
            TransitionManager.beginDelayedTransition(
                binding.root, MaterialFade()
            )
//            skeleton.showSkeleton()
            binding.activityLoginContainerLoading.visibility = View.VISIBLE
        }
    }

    private fun checkRegionAndInitialize() {
        if (userRegion != APP_REGION) {
            showRegionWarningDialog()
        } else {
            checkInitialUserState()
            setupObservers()
        }
    }

    private fun showRegionWarningDialog() {
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.unsupported_region))
            .setMessage(
                getString(
                    R.string.this_app_version_is_only_supported_in_s_your_sim_card_region_is_s,
                    APP_REGION,
                    userRegion
                )
            ).setPositiveButton(R.string.continue_) { _, _ ->
                checkInitialUserState()
                setupObservers()
            }.setNegativeButton(R.string.cancel) { _, _ -> exitProcess(0) }.setCancelable(false)
            .show()
    }

    private fun navigateToMainActivity() {
        if (isNavigatedToMainActivity) return // Avoid navigating multiple times
        isNavigatedToMainActivity = true
        val intent = Intent(this, MainActivity::class.java)
        this.intent.extras?.let { intent.putExtras(it) }
        startActivity(intent)
        this.finish()
    }

    // Google Sign-In method - called from UI
    fun signInWithGoogle() {
        Log.d(TAG, "Google server client id ${BuildConfig.GOOGLE_SERVER_CLIENT_ID}")
        val nonce = generateSecureNonce()
        val request = GetCredentialRequest.Builder().addCredentialOption(
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                .build()
        ).build()

        applicationScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Error getting credential", e)
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)
                        viewModel.signInWithGoogle(googleIdTokenCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid google id token response", e)
                        Toast.makeText(
                            this, getString(
                                R.string.google_sign_in_failed,
                                ErrorLocalizer.getLocalizedError(this, e)
                            ), Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e(TAG, "Unexpected type of credential")
                }
            }

            else -> {
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Get the current language setting from the Android system for this app.
        val currentSystemLangTag = if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            Language.SYSTEM.code
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }

        Log.d(
            MainActivity.Companion.TAG,
            "onStart: Current system language for app is '$currentSystemLangTag'"
        )

        // Tell the ViewModel to sync this value with DataStore.
        // The ViewModel will handle the logic of checking if an update is needed.
        viewModel.syncLanguageWithSystem(currentSystemLangTag)
    }

    private fun applyInitialSettings() {
        // This is one of the few acceptable places to use runBlocking, because it's
        // essential for the initial UI state and only runs once on activity creation.
        lifecycleScope.launch {
            val language = viewModel.language.first()
            AppCompatDelegate.setApplicationLocales(
                AppPreferences.decideLanguage(
                    enumByNameIgnoreCaseOrNull<Language>(language)
                )
            )

            val isDynamic = viewModel.isDynamicColorApplied.first()
            setDynamicColors(isDynamic)

            val theme = viewModel.themeMode.first()
            val modeInt = AppPreferences.decideThemeMode(
                enumByNameIgnoreCaseOrNull<ThemeMode>(
                    theme, ThemeMode.SYSTEM
                )!!
            )
            AppCompatDelegate.setDefaultNightMode(modeInt)
        }
    }

    private fun setDynamicColors(isApplied: Boolean) {
        val precondition = DynamicColors.Precondition { activity, _ ->
            isApplied
        }
        val options = DynamicColorsOptions.Builder().setPrecondition(precondition).build()
        DynamicColors.applyToActivitiesIfAvailable(
            application, options
        )
    }

    override fun onDestroy() {
        loginJob?.cancel()
        super.onDestroy()
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.checkUser()
    }

    /**
     * Generates a cryptographically secure, URL-safe, random string to be used as a nonce.
     * This is crucial for preventing replay attacks.
     * @return A random, URL-safe string.
     */
    private fun generateSecureNonce(): String {
        val secureRandom = SecureRandom()
        // A 16-byte random number is a good balance of security and length.
        val nonceBytes = ByteArray(16)
        secureRandom.nextBytes(nonceBytes)
        // Encode the random bytes into a URL-safe Base64 string without padding.
        return android.util.Base64.encodeToString(
            nonceBytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }
}