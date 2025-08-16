package id.monpres.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFade
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.databinding.ActivityMainBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.libraries.ActivityRestartable
import id.monpres.app.repository.UserRepository
import id.monpres.app.usecase.CheckEmailVerificationUseCase
import id.monpres.app.usecase.GetColorFromAttrUseCase
import id.monpres.app.usecase.GetOrCreateUserIdentityUseCase
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.GetOrderServicesUseCase
import id.monpres.app.usecase.ResendVerificationEmailUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ActivityRestartable {

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    /* Repositories */
    @Inject
    lateinit var userRepository: UserRepository

    /* Authentication */
    private val auth = Firebase.auth // Initialize Firebase Auth
    private var currentUser: FirebaseUser? = null

    /* View models */
    private val viewModel: MainViewModel by viewModels()

    /* Use cases */
    private val checkEmailVerificationUseCase = CheckEmailVerificationUseCase()
    private val resendVerificationEmailUseCase = ResendVerificationEmailUseCase()

    @Inject
    lateinit var getOrderServicesUseCase: GetOrderServicesUseCase
    private val getColorFromAttrUseCase = GetColorFromAttrUseCase()

    @Inject
    lateinit var getOrCreateUserUseCase: GetOrCreateUserUseCase
    @Inject
    lateinit var getOrCreateUserIdentityUseCase: GetOrCreateUserIdentityUseCase

    /* UI */
    lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var optionsMenu: Menu? = null
    val drawerLayout: DrawerLayout by lazy { binding.activityMainDrawerLayout }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, 0)
            windowInsets
        }
//        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainAppBarLayout) { v, windowInsets ->
//            val insets =
//                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
//            lifecycleScope.launch {
//                delay(150L)
//                v.setPadding(
//                    insets.left,
//                    if (supportActionBar?.isShowing == true) insets.top else 0,
//                    insets.right,
//                    0
//                )
//            }
//            windowInsets
//        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainNavigationView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, 0)
            windowInsets
        }
//        WindowCompat.setDecorFitsSystemWindows(window, false)

        /* Auth */
        runAuthentication()
        getUserData()

        /* UI */
        setupUIComponents()

        /* Observers */
        setupObservers()

        /* Listeners */
        setupUIListeners()
        setupNavControllerListeners()

        /* Testing. TODO: Remove on production */
        getOrderServicesUseCase("q0qvQRf8CoboX31463nS0nZVIqF3") { result ->
            result.onSuccess { orders ->
                for (order in orders) {
                    val orderJson = Gson().toJson(order.vehicle)
                    Log.d(TAG, "Order: $orderJson")
                }
            }
                .onFailure { t ->
                    Log.e(TAG, t.localizedMessage ?: t.message ?: "Unknown error")
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        optionsMenu = menu // Save menu for later use

        // Inflate the menu
        menuInflater.inflate(R.menu.activity_main_profile_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        prepareProfileIconMenu(menu) // Prepare profile menu
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected: ${item.itemId}")
        return when (item.itemId) {
            android.R.id.home -> {
                // Preserve navigation controller's behavior
                return super.onOptionsItemSelected(item)
            }
            // Add other menu items here (if any)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun setupNavControllerListeners() {
        navController.addOnDestinationChangedListener { controller, destination, arguments ->

            // Action bar visibility
            when (destination.id) {
                R.id.orderServiceDetailFragment, R.id.serviceProcessFragment -> {
                    val materialFade = MaterialFade().apply {
                        duration = 150L
                    }
                    TransitionManager.beginDelayedTransition(binding.root, materialFade)
                    supportActionBar?.hide()
                }

                else -> {
                    val materialFade = MaterialFade().apply {
                        duration = 150L
                    }
                    TransitionManager.beginDelayedTransition(binding.root, materialFade)
                    supportActionBar?.show()
                }
            }

            // Profile menu visibility
            if (destination.id != R.id.homeFragment) {
                optionsMenu?.findItem(R.id.menu_profile)?.isVisible = false
            } else {
                optionsMenu?.findItem(R.id.menu_profile)?.isVisible = true
            }
        }
    }

    private fun setupObservers() {
        // Observe sign-out event
        lifecycleScope.launch {
            viewModel.signOutEvent.collect {
                clearCredentialsAndNavigate()
            }
        }
    }

    private fun setupUIComponents() {
        // NavHost fragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentActivityMain.id) as NavHostFragment
        navController = navHostFragment.navController

        // App bar
        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        setSupportActionBar(binding.activityMainToolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Navigation view
        val navView = binding.activityMainNavigationView
        navView.setupWithNavController(navController)

        // Navigation view header user infos
        val navViewCardHeader = navView.inflateHeaderView(R.layout.header_navigation_activity_main)
        navViewCardHeader.findViewById<TextView>(R.id.headerNavigationActivityMainDisplayName).text =
            currentUser?.displayName
        navViewCardHeader.findViewById<TextView>(R.id.headerNavigationActivityMainEmail).text =
            currentUser?.email
    }

    private fun setupUIListeners() {
        binding.activityMainNavigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.activityMainDrawerMenuLogOut -> {
                    onLogoutClicked()
                }

                R.id.activityMainDrawerMenuProfile -> {
                    navController.navigate(R.id.profileFragment)
                }

                R.id.activityMainDrawerMenuOrder -> navController.navigate(R.id.action_global_orderServiceListFragment)
            }
            drawerLayout.close()
            return@setNavigationItemSelectedListener true
        }
    }

    /**
     * Prepare profile menu with avatar icon
     *
     * @param menu Menu to prepare
     */
    private fun prepareProfileIconMenu(menu: Menu) {
        // Handle avatar loading AFTER menu creation
        val profileItem = menu.findItem(R.id.menu_profile)
        val profileView = profileItem.actionView?.findViewById<ImageView>(R.id.profile_icon)

        profileView.let { view ->
            // Remove old listeners to prevent duplicates
            view?.setOnClickListener(null)

            // Set explicit click listener
            view?.setOnClickListener {
                // Handle profile navigation
                navController.navigate(R.id.action_global_profileFragment)
                // Optional: close drawer if open
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        }

        // Load avatar icon from UI avatars
        Glide.with(this)
            .load(
                "https://ui-avatars.com/api/?size=128&name=${
                    auth.currentUser?.displayName?.replace(
                        " ",
                        "-"
                    )
                }&rounded=true&" +
                        "background=${
                            getColorFromAttrUseCase.getColorHex(
                                com.google.android.material.R.attr.colorPrimarySurface,
                                this
                            )
                        }&" +
                        "color=${
                            getColorFromAttrUseCase.getColorHex(
                                com.google.android.material.R.attr.colorOnPrimarySurface,
                                this
                            )
                        }&bold=true"
            )
            .circleCrop() // Circular avatar
            .error(R.drawable.person_24px) // Error state
            .into(profileView!!)
    }

    private fun runAuthentication() {
        /* Auth */
        currentUser = auth.currentUser ?: return // Get current user

        Log.d(TAG, "Current user name: ${currentUser?.displayName}")

        checkEmailVerificationUseCase(
            { isVerified ->
                if (isVerified) {
                    Log.d(TAG, "Email is verified")
                } else {
                    // Show confirmation dialog
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.verification))
                        .setMessage(getString(R.string.please_verify_your_email))
                        .setNegativeButton(getString(R.string.refresh)) { _, _ ->

                            restartActivity() // Restart activity

                        }
                        .setPositiveButton(getString(R.string.send)) { _, _ ->

                            resendVerificationEmail() // Send verification email

                            // Show confirmation dialog
                            MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.verification))
                                .setMessage(getString(R.string.please_verify_your_email))
                                .setPositiveButton(getString(R.string.refresh)) { _, _ ->

                                    restartActivity() // Restart activity

                                }
                                .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                                    logoutAndNavigateToLogin() // Sign out
                                }
                                .setCancelable(false) // Prevent dismissing by back button
                                .create()
                                .apply {
                                    setCanceledOnTouchOutside(false) // Prevent dismissing by clicking outside
                                }
                                .show()

                        }
                        .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                            logoutAndNavigateToLogin() // Sign out
                        }
                        .setCancelable(false) // Prevent dismissing by back button
                        .create()
                        .apply {
                            setCanceledOnTouchOutside(false) // Prevent dismissing by clicking outside
                        }
                        .show()
                }
            },
            { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG)
                    .show()
            }
        )
    }

    private fun getUserData() {
        lifecycleScope.launch {
            getOrCreateUserUseCase(UserRole.CUSTOMER).onSuccess { user ->
                Log.d(TAG, "User: ${user.userId}")
                user.userId?.let { Log.d(TAG, "User: ${userRepository.getRecordByUserId(it)}") }
            }.onFailure { exception ->
                when (exception) {
                    is GetOrCreateUserUseCase.UserNotAuthenticatedException -> {
                        // Handle unauthenticated user
                        Log.e(TAG, "User not authenticated")
                    }

                    is GetOrCreateUserUseCase.UserDataParseException,
                    is GetOrCreateUserUseCase.FirestoreOperationException -> {
                        // Handle specific exceptions
                        Log.e(TAG, "Error: ${exception.message}")
                    }

                    else -> {
                        // Handle generic errors
                        Log.e(TAG, "Unexpected error: ${exception.message}")
                    }
                }
            }
            getOrCreateUserIdentityUseCase().onSuccess { userIdentity ->
                Log.d(TAG, "User: ${userIdentity.userId}")
                userIdentity.userId?.let {
                    Log.d(
                        TAG,
                        "User: ${userRepository.getRecordByUserId(it)}"
                    )
                }
            }.onFailure { exception ->
                // Handle generic errors
                Log.e(TAG, "Unexpected error: ${exception.message}")
            }
        }
    }

    private fun resendVerificationEmail() {
        resendVerificationEmailUseCase(
            { isSuccessful ->
                Toast.makeText(
                    this,
                    getString(R.string.verification_email_has_been_sent),
                    Toast.LENGTH_SHORT
                )
                    .show()
            },
            { errorMessage ->
                Toast.makeText(
                    this,
                    "${getString(R.string.failed_to_send_the_verification_email)}: ${errorMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Sign out and navigate to login
    private fun logoutAndNavigateToLogin() {

        Firebase.auth.signOut() // Sign out from firebase

        val cm = CredentialManager.create(this)
        CoroutineScope(Dispatchers.Main).launch {
            cm.clearCredentialState(ClearCredentialStateRequest())
        }

        // Navigate to LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Finish MainActivity to prevent the user from coming back to it
    }

    private fun clearCredentialsAndNavigate() {
        // 1. Clear credentials (context operation)
        val cm = CredentialManager.create(this)
        lifecycleScope.launch(Dispatchers.IO) {
            cm.clearCredentialState(ClearCredentialStateRequest())
        }

        // 2. Navigate immediately (don't wait for credential clearing)
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Sign out
    private fun onLogoutClicked() {
        viewModel.signOut()
    }
}