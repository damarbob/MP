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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.gson.Gson
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import dagger.hilt.android.AndroidEntryPoint
import id.monpres.app.databinding.ActivityMainBinding
import id.monpres.app.enums.UserRole
import id.monpres.app.libraries.ActivityRestartable
import id.monpres.app.model.OrderService
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.repository.UserIdentityRepository
import id.monpres.app.repository.UserRepository
import id.monpres.app.ui.serviceprocess.ServiceProcessFragment
import id.monpres.app.usecase.CheckEmailVerificationUseCase
import id.monpres.app.usecase.GetColorFromAttrUseCase
import id.monpres.app.usecase.GetOrCreateUserIdentityUseCase
import id.monpres.app.usecase.GetOrCreateUserUseCase
import id.monpres.app.usecase.GetOrderServicesUseCase
import id.monpres.app.usecase.ResendVerificationEmailUseCase
import id.monpres.app.utils.UiState
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

    @Inject
    lateinit var userIdentityRepository: UserIdentityRepository

    /* Authentication */
    private val auth = Firebase.auth // Initialize Firebase Auth
    private var currentUser: FirebaseUser? = null

    /* View models */
    private val viewModel: MainViewModel by viewModels()
//    private val orderViewModel: OrderViewModel by viewModels()

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

    /* Variables */
    private lateinit var serviceOrders: List<OrderService>

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, 0, insets.right, 0)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainAppBarLayout) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                insets.left,
                insets.top,
                insets.right,
                0
            )
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

        // Start loading
        viewModel.setMainLoadingState(true)

        // Set lottie loading
        setupLottie()

        /* Auth */
        runAuthentication()

        lifecycleScope.launch {
            getUserData()

            /* Update navigation tree */
            // Important to do this after getting user data
            // to make sure the rest of the app is accessing the correct nav graph
            // in case of nav graph modification
            updateNavigationTree()

            checkUserEligibility()

            viewModel.observeDataByRole()

            viewModel.setMainLoadingState(false)

            /* Permission */
            if (!hasPostNotificationPermission()) checkPermissions(getNotificationPermissions().toList())
//            else showNotification()
        }

        /* UI */
        setupUIComponents()

        /* Observers */
        setupObservers()

        /* Listeners */
        setupUIListeners()
        setupNavControllerListeners()

        // Handle notification click from a cold start
        handleNotificationIntent(intent)

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
            .threads(8u) // Use 6 threads for rendering
//            .themeId("darkTheme") // Set initial theme
//            .layout(Fit.FIT, LayoutUtil.Alignment.Center) // Set layout configuration
            .build()
        binding.activityMainLoadingLottieView.load(config)
    }

    private val a by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location)
            .setMessage(getString(R.string.as_a_partner_you_must_set_a_primary_location))
            .setPositiveButton(R.string.profile) { _, _ ->
                navController.navigate(R.id.action_global_profileFragment)
            }
            .setCancelable(false)
            .create()
    }

    private val b by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.whatsapp_number)
            .setMessage(getString(R.string.you_have_to_set_a_phone_number_so_we_can_contact_you))
            .setPositiveButton(R.string.profile) { _, _ ->
                navController.navigate(R.id.action_global_profileFragment)
            }
            .setCancelable(false)
            .create()
    }

    private fun checkUserEligibility() {
        val user = userRepository.getCurrentUserRecord()
        Log.d(TAG, user.toString())

        if (user?.role == UserRole.PARTNER && (user.locationLat.isNullOrBlank() || user.locationLng.isNullOrBlank())) {
            // Prompt the partner to set location
            if (!a.isShowing && !b.isShowing) a.show()
        } else if (user?.role == UserRole.CUSTOMER && user.phoneNumber.isNullOrBlank()) {
            // Prompt the customer to set phone number
            if (!a.isShowing && !b.isShowing) b.show()
        }
    }

    private fun updateNavigationTree() {
        // Update navigation tree
        val currentUserProfile = userRepository.getCurrentUserRecord()
        if (currentUserProfile == null) {
            Log.e(TAG, "Current user profile is null")
            return
        }

        // If the user is a partner, update start destination to partner home
        // TODO (low priority): Handle admin role
        if (currentUserProfile.role == UserRole.PARTNER) {
            Log.d(TAG, "Current user is a partner")

            // Set initial graph
            navController.graph = navController.navInflater.inflate(R.navigation.nav_main)

            val navGraph = navController.navInflater.inflate(R.navigation.nav_main)
            navGraph.setStartDestination(R.id.partnerHomeFragment)
            navController.graph = navGraph
        }

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
                checkUserEligibility()
            }

            // Profile menu visibility
            if (destination.id != R.id.homeFragment && destination.id != R.id.partnerHomeFragment) {
                optionsMenu?.findItem(R.id.menu_profile)?.isVisible = false
            } else {
                optionsMenu?.findItem(R.id.menu_profile)?.isVisible = true
            }
        }
    }

    private fun setupObservers() {
        // Observe sign-out event
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.signOutEvent
                    .collect {
                        clearCredentialsAndNavigate()
                    }
            }
        }

        viewModel.mainLoadingState.observe(this) {
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply {
                duration = 300L
            })
            binding.apply {
                navHostFragmentActivityMain.visibility = if (it) View.GONE else View.VISIBLE
                activityMainLoadingStateLayout.visibility = if (it) View.VISIBLE else View.GONE
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
            },
            { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG)
                    .show()
            }
        )
    }

    private suspend fun getUserData() {
        /* Get user profile */
        getOrCreateUserUseCase(UserRole.CUSTOMER).onSuccess { user ->
            Log.d(TAG, "User: ${user.userId}")
            user.userId?.let {
                Log.d(
                    TAG,
                    "UserRepository record: ${userRepository.getRecordByUserId(it)}"
                )
            }
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

        /* Get user identity */
        getOrCreateUserIdentityUseCase().onSuccess { userIdentity ->
            Log.d(TAG, "User: ${userIdentity.userId}")
            userIdentity.userId?.let {
                Log.d(
                    TAG,
                    "UserIdentityRepository record: ${
                        userIdentityRepository.getRecordByUserId(
                            it
                        )
                    }"
                )
            }
        }.onFailure { exception ->
            // Handle generic errors
            Log.e(TAG, "Unexpected error: ${exception.message}")
        }
    }

    private fun showNotification() {
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

        if (::serviceOrders.isInitialized) {
            serviceOrders.forEach {
                OrderServiceNotification.cancelNotification(this, it.id!!)
            }
            serviceOrders = listOf()
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
        handleNotificationIntent(intent)
        if (launchedFromOrderId != null) {
            viewModel.setOpenedFromNotification(launchedFromOrderId)
        }
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
            if (launchedFromOrderId != null) {
                viewModel.setOpenedFromNotification(launchedFromOrderId)
                navController.navigate(R.id.action_global_serviceProcessFragment, Bundle().apply {
                    putString(ServiceProcessFragment.ARG_ORDER_SERVICE_ID, launchedFromOrderId)
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNavigationTree()
    }

}
