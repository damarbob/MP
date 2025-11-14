package id.monpres.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.dotlottie.dlplayer.Mode
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.createSkeleton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.databinding.ActivityMainBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import id.monpres.app.libraries.ActivityRestartable
import id.monpres.app.libraries.ErrorLocalizer
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.repository.UserIdentityRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.service.OrderServiceLocationTrackingService
import id.monpres.app.state.NavigationGraphState
import id.monpres.app.state.UserEligibilityState
import id.monpres.app.ui.serviceprocess.ServiceProcessFragment
import id.monpres.app.usecase.GetColorFromAttrUseCase
import id.monpres.app.usecase.GetOrCreateUserIdentityUseCase
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.GetOrderServicesUseCase
import id.monpres.app.utils.NetworkConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
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

    @Inject
    lateinit var userIdentityRepository: UserIdentityRepository

    /* Authentication */
    private val auth = Firebase.auth // Initialize Firebase Auth
    private var currentUser: FirebaseUser? = null

    /* View models */
    private val viewModel: MainViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by viewModels()

    /* Use cases */
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
    private lateinit var skeleton: Skeleton

    /* Others */
    @Inject
    lateinit var networkConnectivityObserver: NetworkConnectivityObserver

    /* Permissions */
    private val permissionQueue: MutableList<String> =
        mutableListOf() // Queue for individual permissions
    private lateinit var currentPermission: String
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when {
                isGranted -> {
                    processNextPermission()
//                    if (currentPermission == Manifest.permission.POST_NOTIFICATIONS) {
//                        showNotification()
//                    }
                }

                shouldShowRequestPermissionRationale(currentPermission) -> {
                    showPermissionRationale(currentPermission)
                }

                else -> {
                    if (currentPermission == Manifest.permission.READ_MEDIA_IMAGES && Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
                        showPermissionSettingsDialog(currentPermission)
                    } else if (currentPermission != Manifest.permission.READ_MEDIA_IMAGES) {
                        showPermissionSettingsDialog(currentPermission)
                    } else processNextPermission()
                }
            }
        }

    private var launchedFromOrderId: String? = null

    // Add permissions to the queue and start processing
    private fun checkPermissions(permissions: List<String>) {
        permissionQueue.clear()
        permissionQueue.addAll(permissions)
        processNextPermission()
    }

    // Process the next permission in the queue
    private fun processNextPermission() {
        if (permissionQueue.isNotEmpty()) {
            currentPermission = permissionQueue.removeAt(0)
            requestPermissionLauncher.launch(currentPermission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkConnectivityObserver.registerNetworkCallback()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, 0)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragmentActivityMain) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, 0)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainNavigationView) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set lottie loading
        setupLottie()

        /* Permission */
        if (!hasPostNotificationPermission()) checkPermissions(getNotificationPermissions().toList())
//            else showNotification()

        /* UI */
        setupUIComponents()

        /* Observers */
        setupObservers()

        /* Listeners */
        setupUIListeners()
        setupNavControllerListeners()

        /* Testing. TODO: Remove on production */
//        getOrderServicesUseCase("q0qvQRf8CoboX31463nS0nZVIqF3") { result ->
//            result.onSuccess { orders ->
//                for (order in orders) {
//                    val orderJson = Gson().toJson(order.vehicle)
//                    Log.d(TAG, "Order: $orderJson")
//                }
//            }
//                .onFailure { t ->
//                    Log.e(TAG, t.localizedMessage ?: t.message ?: "Unknown error")
//                }
//        }
    }

    private fun setupLottie() {
        val config = Config.Builder()
            .autoplay(true)
            .speed(1f)
            .loop(true)
//            .source(DotLottieSource.Url("https://lottie.host/2b3473b5-0f5f-40da-985a-9d111dda0530/Ae6j7lkUo7.lottie"))
//            .source(DotLottieSource.Asset("file.json")) // asset from the asset folder .json or .lottie
            .source(DotLottieSource.Res(R.raw.car)) // resource from raw resources .json or .lottie
            .useFrameInterpolation(true)
            .playMode(Mode.FORWARD)
            .threads(6u) // Use 6 threads for rendering
//            .themeId("darkTheme") // Set initial theme
//            .layout(Fit.FIT, LayoutUtil.Alignment.Center) // Set layout configuration
            .build()
//        binding.activityMainLoadingLottieView.load(config)
//        binding.activityMainLoadingLottieView.addEventListener(object : DotLottieEventListener {
//            override fun onLoad() {
//                super.onLoad()
//                binding.activityMainLoadingLottieView.visibility = View.VISIBLE
//                binding.activityMainLoadingLottieView.play()
//            }
//
//            override fun onLoadError() {
//                super.onLoadError()
//                binding.activityMainLoadingLottieView.visibility = View.GONE
//            }
//
//            override fun onError(error: Throwable) {
//                super.onError(error)
//                Log.e(TAG, "Lottie error: $error")
//                binding.activityMainLoadingLottieView.visibility = View.GONE
//            }
//
//            override fun onLoadError(error: Throwable) {
//                super.onLoadError(error)
//                Log.e(TAG, "Lottie error: $error")
//                binding.activityMainLoadingLottieView.visibility = View.GONE
//            }
//        })
    }

    private val accountVerificationDialog by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verification))
            .setNegativeButton(getString(R.string.refresh)) { _, _ ->
                restartActivity() // Restart activity to re-check
            }
            .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                onLogoutClicked() // Sign out
            }
            .setCancelable(false)
            .create()
            .apply {
                setCanceledOnTouchOutside(false) // Prevent dismissing
            }
    }

    private val locationDialog by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location)
            .setMessage(getString(R.string.as_a_partner_you_must_set_a_primary_location))
            .setPositiveButton(R.string.profile) { _, _ ->
                navController.navigate(R.id.action_global_profileFragment)
            }
            .setCancelable(false)
            .create()
    }

    private val whatsappDialog by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.whatsapp_number)
            .setMessage(getString(R.string.you_have_to_set_a_phone_number_so_we_can_contact_you))
            .setPositiveButton(R.string.profile) { _, _ ->
                navController.navigate(R.id.action_global_profileFragment)
            }
            .setCancelable(false)
            .create()
    }

    private val socialMediaDialog by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.social_media))
            .setMessage(getString(R.string.you_must_set_at_least_one_social_media_account))
            .setPositiveButton(R.string.profile) { _, _ ->
                navController.navigate(R.id.action_global_profileFragment)
            }
            .setCancelable(false)
            .create()
    }

    private fun updateNavigationTree(startDestination: Int) {
        // Update navigation tree
        // Prevent re-setting the graph if it's already correct
        if (navController.graph.startDestinationId == startDestination) {
            return
        }

        Log.d(TAG, "Updating nav graph, start destination: $startDestination")
        val navGraph = navController.navInflater.inflate(R.navigation.nav_main)
        navGraph.setStartDestination(startDestination)
        navController.graph = navGraph

        setupAppBar() // Update app bar is required to reflect navigation tree changes
    }

    private fun setupAppBar() {
        // App bar
        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        setSupportActionBar(binding.activityMainToolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
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

            when (destination.id) {
                R.id.orderServiceDetailFragment, R.id.serviceProcessFragment, R.id.orderItemEditorFragment -> {
                    drawerLayout.setDrawerLockMode(
                        DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                        GravityCompat.START
                    )
                }

                else -> {
                    drawerLayout.setDrawerLockMode(
                        DrawerLayout.LOCK_MODE_UNLOCKED,
                        GravityCompat.START
                    )
                }
            }

            // Check user eligibility anywhere except profile fragment
            if (destination.id != R.id.profileFragment) {
                viewModel.recheckEligibility()
            }

            // Profile menu visibility
            if (destination.id != R.id.homeFragment && destination.id != R.id.partnerHomeFragment && destination.id != R.id.adminHomeFragment) {
                optionsMenu?.findItem(R.id.menu_profile)?.isVisible = false
            } else {
                optionsMenu?.findItem(R.id.menu_profile)?.isVisible = true
            }
        }
    }

    private fun setupObservers() {
        // --- ACCOUNT VERIFICATION ---
        // Observe user ACCOUNT verification status (PENDING, APPROVED, REJECTED)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // We use filterNotNull to only react once the status is loaded
                viewModel.userVerificationStatus.filterNotNull().collect { status ->
                    Log.d(TAG, "UserVerificationStatus: $status")
                    handleUserVerificationStatus(status)
                }
            }
        }
        // --- NEW OBSERVER FOR ERROR EVENTS ---
        // Observer for network connection
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect {
                    Log.d(TAG, "isConnected: $it")
                    TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply {
                        duration = 150L
                    })
                    binding.activityMainLinearLayoutConnectionError.visibility = if (it) View.GONE else View.VISIBLE
                    handleConnectionChange(it)
                }
            }
        }

        // Observer for error events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // This collector will run whenever the activity is started
                // and stop when it's stopped, preventing background UI work.
                viewModel.errorEvent.collect { exception ->
                    val message = ErrorLocalizer.getLocalizedError(this@MainActivity, exception)
                    // Show the error message in a Toast or a Snackbar
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // This collector will run whenever the activity is started
                // and stop when it's stopped, preventing background UI work.
                mainGraphViewModel.errorEvent.collect { throwable ->
                    // Show a Toast, Snackbar, or Dialog
                    val errorMessage =
                        ErrorLocalizer.getLocalizedError(this@MainActivity, throwable)
                    Log.e(TAG, errorMessage, throwable)
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Observe is user verified
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isUserVerified.collect {
                    Log.d(TAG, "isUserVerified: $it")
                    if (it != null) {
                        handleVerificationStatus(it)
                    }
                }
            }
        }

        // Observe resend email verification success
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isResendEmailVerificationSuccess.collect {
                    Log.d(TAG, "isResendEmailVerificationSuccess: $it")
                    if (it != null) {
                        handleVerificationEmailResult(it)
                    }
                }
            }
        }

        // Observe sign-out event
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.signOutEvent
                    .collect {
                        OrderServiceNotification.cancelAll(this@MainActivity)
                        clearCredentialsAndNavigate()
                        stopAllTrackingOrder()
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mainLoadingState.collect {
                    Log.d(TAG, "mainLoadingState: $it")
                    TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply {
                        duration = 150L
                    })
//                    binding.apply {
//                        navHostFragmentActivityMain.visibility = if (it) View.GONE else View.VISIBLE
//                        activityMainLoadingStateLayout.visibility =
//                            if (it) View.VISIBLE else View.GONE
//                    }
                    if (it) skeleton.showSkeleton() else skeleton.showOriginal()
                    handleNotificationIntent(intent)
                }
            }
        }

        // --- NEW OBSERVERS FOR REFACTORED LOGIC ---

        // Observe the navigation graph state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationGraphState.collect { state ->
                    when (state) {
                        is NavigationGraphState.Loading -> { /* Handled by mainLoadingState */
                        }

                        is NavigationGraphState.Admin -> updateNavigationTree(startDestination = R.id.adminHomeFragment)
                        is NavigationGraphState.Partner -> updateNavigationTree(startDestination = R.id.partnerHomeFragment)
                        is NavigationGraphState.Customer -> updateNavigationTree(startDestination = R.id.homeFragment)
                    }
                }
            }
        }

        // Observe the user eligibility state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userEligibilityState.collect { state ->
                    // Dismiss any existing dialogs first
                    locationDialog.dismiss()
                    whatsappDialog.dismiss()
                    socialMediaDialog.dismiss()
                    // Show the relevant dialog if needed
                    when (state) {
                        is UserEligibilityState.PartnerMissingLocation -> locationDialog.show()
                        is UserEligibilityState.CustomerMissingPhoneNumber -> whatsappDialog.show()
                        is UserEligibilityState.CustomerMissingSocialMedia -> socialMediaDialog.show()
                        is UserEligibilityState.Eligible -> { /* Do nothing */
                        }

                    }
                }
            }
        }
    }

    private fun setupUIComponents() {
        // NavHost fragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentActivityMain.id) as NavHostFragment
        navController = navHostFragment.navController
        navController.graph = navController.navInflater.inflate(R.navigation.nav_main)

        setupAppBar()

        // Navigation view
        val navView = binding.activityMainNavigationView
        navView.setupWithNavController(navController)

        // Navigation view header user infos
        val navViewCardHeader = navView.inflateHeaderView(R.layout.header_navigation_activity_main)
        navViewCardHeader.findViewById<TextView>(R.id.headerNavigationActivityMainDisplayName).text =
            auth.currentUser?.displayName
        navViewCardHeader.findViewById<TextView>(R.id.headerNavigationActivityMainEmail).text =
            auth.currentUser?.email

        skeleton = binding.navHostFragmentActivityMain.createSkeleton()
    }

    private fun setupUIListeners() {
        binding.activityMainNavigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.activityMainDrawerMenuLogOut -> {
                    onLogoutClicked()
                }

                R.id.activityMainDrawerMenuProfile -> {
                    navController.navigate(R.id.action_global_profileFragment)
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

    private fun handleVerificationStatus(isVerified: Boolean) {
        if (isVerified) {
            Log.d(TAG, "Email is verified")

            // Once email is verified, dismiss any account verification dialog
            // to allow the userVerificationStatus flow to show the correct one.
            if (accountVerificationDialog.isShowing) {
                accountVerificationDialog.dismiss()
            }
        } else {
            // Show confirmation dialog
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.verification))
                .setMessage(getString(R.string.please_verify_your_email))
                .setNegativeButton(getString(R.string.refresh)) { _, _ ->

                    restartActivity() // Restart activity

                }
                .setPositiveButton(getString(R.string.send)) { _, _ ->

                    viewModel.resendVerificationEmail() // Send verification email

                    // Show confirmation dialog
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.verification))
                        .setMessage(getString(R.string.please_verify_your_email))
                        .setPositiveButton(getString(R.string.refresh)) { _, _ ->

                            restartActivity() // Restart activity

                        }
                        .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                            onLogoutClicked() // Sign out
                        }
                        .setCancelable(false) // Prevent dismissing by back button
                        .create()
                        .apply {
                            setCanceledOnTouchOutside(false) // Prevent dismissing by clicking outside
                        }
                        .show()

                }
                .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                    onLogoutClicked() // Sign out
                }
                .setCancelable(false) // Prevent dismissing by back button
                .create()
                .apply {
                    setCanceledOnTouchOutside(false) // Prevent dismissing by clicking outside
                }
                .show()
        }
    }

    // --- HANDLE ACCOUNT VERIFICATION (BY ADMIN) ---
    private fun handleUserVerificationStatus(status: UserVerificationStatus) {
        // This function handles ACCOUNT (admin) verification
        // It should only run AFTER email verification is successful.

        // Ensure email verification dialog isn't showing
        // (Though ViewModel logic should prevent this, it's a safe check)
        if (viewModel.isUserVerified.value != true) {
            return
        }

        // --- Get the current user's role ---
        val userRole = viewModel.getCurrentUser()?.role

        // Dismiss any existing dialog first to avoid duplicates
        if (accountVerificationDialog.isShowing) {
            accountVerificationDialog.dismiss()
        }

        when (status) {
            UserVerificationStatus.VERIFIED -> {
                // User is approved, do nothing. The app should function normally.
                Log.d(TAG, "User account is VERIFIED.")
            }

            UserVerificationStatus.PENDING -> {
                // --- Only block if user is a CUSTOMER ---
                if (userRole == UserRole.CUSTOMER) {
                    // User is pending, show a waiting dialog.
                    accountVerificationDialog.setMessage(getString(R.string.your_account_is_awaiting_admin_approval))
                    accountVerificationDialog.show()
                } else {
                    // User is a PARTNER/ADMIN, don't block them for PENDING status
                    Log.d(
                        TAG,
                        "User is $userRole and PENDING. Allowing access (blocking is for CUSTOMERs only)."
                    )
                }
            }

            UserVerificationStatus.REJECTED -> {
                // --- Only block if user is a CUSTOMER ---
                if (userRole == UserRole.CUSTOMER) {
                    // User is rejected, show a rejection dialog.
                    accountVerificationDialog.setMessage(getString(R.string.your_account_has_been_rejected))
                    accountVerificationDialog.show()
                } else {
                    // User is a PARTNER/ADMIN, don't block them for REJECTED status
                    Log.d(
                        TAG,
                        "User is $userRole and REJECTED. Allowing access (blocking is for CUSTOMERs only)."
                    )
                }
            }
        }
    }

    /*private fun showNotification() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                when (userRepository.getCurrentUserRecord()?.role) {
                    UserRole.PARTNER -> {
                        viewModel.partnerOrderServicesState.collect { state ->
                            when (state) {
                                is UiState.Success -> {
                                    Log.d(TAG, "Partner order services: ${state.data}")
                                    serviceOrders = state.data
//                                    processNotification(serviceOrders)
                                }

                                else -> {}
                            }
                        }
                    }

                    UserRole.CUSTOMER -> {
                        viewModel.userOrderServicesState.collect { state ->
                            when (state) {
                                is UiState.Success -> {
                                    Log.d(TAG, "User order services: ${state.data}")
                                    serviceOrders = state.data
//                                    processNotification(serviceOrders)
                                }

                                else -> {}
                            }
                        }
                    }

                    else -> {
                        Log.e(
                            TAG,
                            "Unexpected user role: ${userRepository.getCurrentUserRecord()?.role}"
                        )
                    }
                }
            }
        }
    }

    private fun processNotification(currentOrders: List<OrderService>) {
        val currentlyOpenedOrderId =
            viewModel.getJustOpenedFromNotificationOrderId() // Cache it for this run
        // Clear it after use so subsequent non-notification updates are processed normally
        viewModel.clearJustOpenedFromNotificationOrderId()
        val notifiedOrderStatusMap = viewModel.getNotifiedOrderStatusMap()

        // Identify new or updated orders
        currentOrders.forEach { order ->
            val lastNotifiedStatus = notifiedOrderStatusMap[order.id]
            val currentStatus = order.status

            if (order.id == currentlyOpenedOrderId && lastNotifiedStatus == currentStatus) {
                // This specific order was just clicked to open the app,
                // and its status hasn't changed since the notification that was clicked.
                // Let's assume the notification for this state is already visible.
                // We *do* want to ensure it's in our notifiedOrderStatusMap so it's tracked.
                if (notifiedOrderStatusMap[order.id] == null) { // Ensure it's tracked even if we skip re-notifying
                    viewModel.setNotifiedOrderStatusMap(mutableMapOf(order.id!! to currentStatus!!))
                }
                Log.d(TAG, "Skipping re-notification for order ${order.id} as it was just opened.")
                return@forEach // Skip to the next order for notification processing
            }

            if (lastNotifiedStatus == null || lastNotifiedStatus != currentStatus) {
                // New order or status has changed since last notification
                Log.d(
                    TAG,
                    "Order ${order.id} needs notification. Current: $currentStatus, Last Notified: $lastNotifiedStatus"
                )

//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                OrderServiceNotification.showOrUpdateNotification(
                    this,
                    order,
                    userRepository.getCurrentUserRecord()?.role!!
                )
//                }
                notifiedOrderStatusMap[order.id!!] = currentStatus!!
            }
        }

        // Identify orders that were completed/cancelled and remove their notifications
        // (and remove from notifiedOrderStatusMap)
        val currentOrderIds = currentOrders.map { it.id }.toSet()
        val ordersToRemoveNotification =
            notifiedOrderStatusMap.filterKeys { !currentOrderIds.contains(it) }

        ordersToRemoveNotification.forEach { (orderId, _) ->
            Log.d(
                TAG,
                "Order $orderId no longer ongoing or status implies removal. Cancelling notification."
            )
            OrderServiceNotification.cancelNotification(this, orderId)
            viewModel.removeNotifiedOrderStatus(orderId)
        }
    }*/

    private fun handleVerificationEmailResult(isSuccessful: Boolean) {
        if (isSuccessful) {
            Toast.makeText(
                this,
                getString(R.string.verification_email_has_been_sent),
                Toast.LENGTH_SHORT
            )
                .show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.failed_to_send_the_verification_email),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun clearCredentialsAndNavigate() {
        // 1. Clear credentials (context operation)
        val cm = CredentialManager.create(this)
        lifecycleScope.launch(Dispatchers.IO) {
            cm.clearCredentialState(ClearCredentialStateRequest())
        }

        // 2. Navigate immediately (don't wait for credential clearing)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Sign out
    private fun onLogoutClicked() {
        viewModel.signOut()
    }

    private fun getNotificationPermissions(): Array<String> {
        val permissions: MutableList<String> = mutableListOf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
//            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
//        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM)
        return permissions.toTypedArray()
    }

    private fun showPermissionRationale(permission: String) {
        val permissionName = extractPermissionName(permission)
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.x_permission_required, permissionName))
            .setMessage(getString(R.string.please_grant_permission))
            .setPositiveButton(resources.getString(R.string.okay)) { _, _ ->
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                processNextPermission()
            }
            .create()
            .show()
    }

    private fun showPermissionSettingsDialog(permission: String) {
        val permissionName = extractPermissionName(permission)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.x_permission_is_not_granted, permissionName))
            .setMessage(getString(R.string.this_permission_is_disabled))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->

            }
            .create()
            .show()
    }

    private fun extractPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> {
                getString(R.string.camera)
            }

            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE -> {
                getString(R.string.gallery)
            }

            Manifest.permission.POST_NOTIFICATIONS -> {
                getString(R.string.notification)
            }

            else -> {
                ""
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", this.packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun hasPostNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // POST_NOTIFICATIONS is not required before Android 13
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        if (viewModel.mainLoadingState.value == false) handleNotificationIntent(intent)
        else setupObservers()
//        if (launchedFromOrderId != null) {
//            viewModel.setOpenedFromNotification(launchedFromOrderId)
//        }
    }

    fun handleNotificationIntent(intent: Intent) {
        launchedFromOrderId = intent.getStringExtra(OrderServiceNotification.ORDER_ID_KEY)
        if (launchedFromOrderId != null) {
            Log.d(
                TAG,
                "Launched/Resumed from notification for order: $launchedFromOrderId"
            )
            // You might want to clear it after processing to avoid re-triggering this logic
            // on normal app opens, or pass it to the ViewModel to handle.

//                viewModel.setOpenedFromNotification(launchedFromOrderId)
            navController.navigate(R.id.action_global_serviceProcessFragment, Bundle().apply {
                putString(ServiceProcessFragment.ARG_ORDER_SERVICE_ID, launchedFromOrderId)
            })

        }
    }

    private fun stopAllTrackingOrder() {
        val intent = Intent(application, OrderServiceLocationTrackingService::class.java).apply {
            action = OrderServiceLocationTrackingService.ACTION_STOP
        }
        application.startService(intent)
        Log.d(TAG, "MainActivity requested to STOP all Location Services.")
    }

    private fun handleConnectionChange(isConnected: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainAppBarLayout) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                insets.left,
                if (isConnected) insets.top else 0,
                insets.right,
                0
            )
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainLinearLayoutConnectionError) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, 0)
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.activityMainAppBarLayout)
    }

    override fun onStop() {
        super.onStop()
        networkConnectivityObserver.cleanup()
    }

    override fun onRestart() {
        super.onRestart()
        networkConnectivityObserver.registerNetworkCallback()
    }
}
