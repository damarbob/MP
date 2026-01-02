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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.SkeletonConfig
import com.faltenreich.skeletonlayout.createSkeleton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.data.local.AppPreferences
import id.monpres.app.data.network.NetworkMonitor
import id.monpres.app.databinding.ActivityMainBinding
import id.monpres.app.enums.Language
import id.monpres.app.enums.ThemeMode
import id.monpres.app.enums.UserRole
import id.monpres.app.notification.OrderServiceNotification
import id.monpres.app.repository.UserRepository
import id.monpres.app.service.OrderServiceLocationTrackingService
import id.monpres.app.ui.common.base.ActivityRestartable
import id.monpres.app.ui.common.mapper.ErrorMessageMapper
import id.monpres.app.ui.serviceprocess.ServiceProcessFragment
import id.monpres.app.usecase.GetColorFromAttrUseCase
import id.monpres.app.usecase.MigrateUsersSearchTokens
import id.monpres.app.usecase.OpenWhatsAppUseCase
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main), ActivityRestartable {

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    /* Repositories (Can be removed if only used by ViewModel) */
    @Inject
    lateinit var userRepository: UserRepository

    /* Authentication */
    private val auth = Firebase.auth // Initialize Firebase Auth

    /* View models */
    private val viewModel: MainViewModel by viewModels()
    private val mainGraphViewModel: MainGraphViewModel by viewModels()

    /* Use cases */
    private val getColorFromAttrUseCase = GetColorFromAttrUseCase()
    private val openWhatsAppUseCase = OpenWhatsAppUseCase()

    @Inject
    lateinit var migrateUsersSearchTokens: MigrateUsersSearchTokens

    /* UI */
    private val binding by viewBinding(ActivityMainBinding::bind)
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var optionsMenu: Menu? = null
    val drawerLayout: DrawerLayout by lazy { binding.activityMainDrawerLayout }
    private lateinit var skeleton: Skeleton
    private var currentDialog: AlertDialog? = null

    private var dialogStateJob: Job? = null

    /* Others */
    @Inject
    lateinit var NetworkMonitor: NetworkMonitor

    /* Permissions */
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle permissions results
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                // All permissions granted
                Log.d(TAG, "All permissions granted")
            } else {
                // At least one permission was denied.
                permissions.entries.forEach { (permission, isGranted) ->
                    if (!isGranted) {
                        if (!shouldShowRequestPermissionRationale(permission)) {
                            // This is the "Don't ask again" case
                            showPermissionSettingsDialog(permission)
                        } else {
                            // User denied, but we can ask again.
                            // You might show a softer rationale here.
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyInitialSettings()

        NetworkMonitor.registerNetworkCallback()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupWindowInsets()

        /* Permission */
        if (!hasPostNotificationPermission()) {
            checkPermissions(getNotificationPermissions().toList())
        }

        /* UI */
        setupUIComponents()

        /* Observers */
        setupObservers()
        observeDialog()

        /* Listeners */
        setupUIListeners()
        setupNavControllerListeners()

        // Pass intent to ViewModel for processing
        viewModel.setPendingIntent(intent)

        /* Testing (if any) */
        setupTesting()
    }

    private fun setupTesting() {
        // Place testing code here
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            // Apply padding to the root for navigation drawer cutouts
            v.setPadding(insets.left, 0, insets.right, 0)

            // NavHostFragment (main content)
            binding.navHostFragmentActivityMain.updatePadding(
                left = insets.left,
                right = insets.right
            )

            // NavigationView (drawer content) gets all insets
            binding.activityMainNavigationView.updatePadding(
                top = insets.top,
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right
            )

            // AppBar gets left/right
            binding.activityMainAppBarLayout.updatePadding(left = insets.left, right = insets.right)

            // Connection Error Bar gets top
            binding.activityMainLinearLayoutConnectionError.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right
            )

            WindowInsetsCompat.Builder(windowInsets).setInsets(
                WindowInsetsCompat.Type.systemBars(),
                Insets.of(0, insets.top, 0, insets.bottom)
            ).build()
        }
    }

    // Add permissions to the queue and start processing
    private fun checkPermissions(permissions: List<String>) {
        requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun updateNavigationTree(startDestination: Int) {
        if (navController.graph.startDestinationId == startDestination) return

        Log.d(TAG, "Updating nav graph, start destination: $startDestination")
        val navGraph = navController.navInflater.inflate(R.navigation.nav_main)
        navGraph.setStartDestination(startDestination)
        navController.graph = navGraph

        setupAppBar() // Update app bar is required to reflect navigation tree changes
    }

    private fun setupAppBar() {
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.partnerHomeFragment,
                R.id.adminHomeFragment
            ), drawerLayout
        )
        setSupportActionBar(binding.activityMainToolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        optionsMenu = menu
        menuInflater.inflate(R.menu.activity_main_profile_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        prepareProfileIconMenu(menu)

        // Programmatically hide the edit menu by default on start
        menu.findItem(R.id.menu_edit)?.isVisible = false

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> super.onOptionsItemSelected(item)

            R.id.menu_edit -> {
                // 1. Get the current fragment from the NavHost
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
                val currentFragment =
                    navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

                // 2. Check if it provides an OrderService
                if (currentFragment is id.monpres.app.ui.common.interfaces.IOrderServiceProvider) {
                    val orderService = currentFragment.getCurrentOrderService()

                    if (orderService != null) {
                        // 3. Navigate to Edit Fragment
                        // We use a global action or explicit ID navigation.
                        // Since both fragments share the same destination, we can use the ID directly with a Bundle or Directions.
                        // Using Directions is safer if you generated a global action,
                        // but standard navigate with Bundle works universally here since the ID is known.

                        navController.navigate(
                            R.id.action_global_orderServiceEditFragment, // Ensure you created this global action in nav_graph
                            Bundle().apply {
                                putParcelable("orderService", orderService)
                            }
                        )
                    } else {
                        Toast.makeText(
                            this,
                            "Data not ready yet",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupNavControllerListeners() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.orderServiceDetailFragment, R.id.serviceProcessFragment, R.id.orderItemEditorFragment -> {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }

                else -> {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }

            dialogStateJob?.cancel()
            if (destination.id != R.id.profileFragment) {
                viewModel.recheckEligibility()
                observeDialog()
            }

            optionsMenu?.findItem(R.id.menu_profile)?.isVisible =
                destination.id == R.id.homeFragment ||
                        destination.id == R.id.partnerHomeFragment ||
                        destination.id == R.id.adminHomeFragment

            // Show only on specific fragments AND if user is ADMIN
            val isEditablePage = destination.id == R.id.orderServiceDetailFragment ||
                    destination.id == R.id.serviceProcessFragment

            val isAdmin = viewModel.getCurrentUser()?.role == UserRole.ADMIN

            optionsMenu?.findItem(R.id.menu_edit)?.isVisible = isEditablePage && isAdmin
        }
    }

    private fun setupObservers() {
        // --- Connection Observer ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    Log.d(TAG, "isConnected: $isConnected")
                    TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply {
                        duration = 150L
                    })
                    binding.activityMainLinearLayoutConnectionError.visibility =
                        if (isConnected) View.GONE else View.VISIBLE
                    handleConnectionChange(isConnected)
                }
            }
        }

        // --- Error Event Observer ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect { exception ->
                    val message = ErrorMessageMapper.getLocalizedError(this@MainActivity, exception)
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // --- MainGraphViewModel Error Event Observer ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainGraphViewModel.errorEvent.collect { exception ->
                    val message = ErrorMessageMapper.getLocalizedError(this@MainActivity, exception)
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // --- Toast Event Observer ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toastEvent.collect { event ->
                    when (event) {
                        // CHANGED: Handle the new event and get the string here
                        is ToastEvent.VerificationEmailSent -> {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.verification_email_has_been_sent),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        // --- Navigation Event Observer ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is NavigationEvent.ToLogin -> navigateToLogin()
                        is NavigationEvent.ToServiceProcess -> {
                            navController.navigate(
                                R.id.action_global_serviceProcessFragment,
                                Bundle().apply {
                                    putString(
                                        ServiceProcessFragment.ARG_ORDER_SERVICE_ID,
                                        event.orderId
                                    )
                                })
                        }
                        is NavigationEvent.ToOther -> navController.navigate(event.destination)
                    }
                }
            }
        }

        // --- Loading State Observer ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mainLoadingState.collect { isLoading ->
                    Log.d(TAG, "mainLoadingState: $isLoading")
                    if (isLoading) skeleton.showSkeleton() else skeleton.showOriginal()
                }
            }
        }

        // --- Navigation Observer ---
        lifecycleScope.launch {
            // Use first() to get the initial graph and set it only once.
            // This is the key to preventing the reset on configuration change.
            val roleNavigation = viewModel.roleNavigation.filterNotNull().first()

            Log.d(TAG, "Setting initial navigation graph: ${roleNavigation.name}")

            val homeBackStackEntry = try {
                navController.getBackStackEntry(R.id.homeFragment)
            } catch (_: Exception) {
                null
            }

            when (roleNavigation) {
                UserRole.CUSTOMER -> {}
                UserRole.PARTNER -> {
                    if (homeBackStackEntry != null) {
                        navController.navigate(NavMainDirections.actionGlobalPartnerHomeFragment())
                    }
                }

                UserRole.ADMIN -> {
                    if (homeBackStackEntry != null) {
                        navController.navigate(NavMainDirections.actionGlobalAdminHomeFragment())
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isDynamicColorApplied.collect { isApplied ->
                    Log.d(TAG, "isDynamicColorApplied: $isApplied")
                    setDynamicColors(isApplied)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.themeMode.collect { mode ->
                    Log.d(TAG, "Theme mode: $mode")
                    val modeInt = AppPreferences.decideThemeMode(
                        enumByNameIgnoreCaseOrNull<ThemeMode>(
                            mode,
                            ThemeMode.SYSTEM
                        )!!
                    )
                    AppCompatDelegate.setDefaultNightMode(modeInt)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.language.collect { language ->
                    Log.d(TAG, "Language: $language")
                    setAppLanguage(enumByNameIgnoreCaseOrNull<Language>(language))
                }
            }
        }
    }

    private fun observeDialog() {
        // --- DIALOG STATE OBSERVER ---
        dialogStateJob?.cancel()
        dialogStateJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dialogState.collectLatest { state ->
                    currentDialog?.dismiss() // Dismiss any existing dialog
                    currentDialog = when (state) {
                        is DialogState.None -> null
                        is DialogState.EmailVerificationPending -> showEmailVerificationDialog()

                        // CHANGED: Handle the new states from the ViewModel
                        is DialogState.AdminVerificationPending -> showAdminVerificationDialog(
                            getString(R.string.your_account_is_awaiting_admin_approval)
                        )

                        is DialogState.AdminVerificationRejected -> showAdminVerificationDialog(
                            getString(R.string.your_account_has_been_rejected)
                        )

                        is DialogState.PartnerMissingLocation -> showEligibilityDialog(
                            R.string.location,
                            getString(R.string.as_a_partner_you_must_set_a_primary_location)
                        )

                        is DialogState.CustomerMissingPhoneNumber -> showEligibilityDialog(
                            R.string.whatsapp_number,
                            getString(R.string.you_have_to_set_a_phone_number_so_we_can_contact_you)
                        )

                        is DialogState.CustomerMissingSocialMedia -> showEligibilityDialog(
                            R.string.social_media,
                            getString(R.string.you_must_set_at_least_one_social_media_account)
                        )
                    }
                    currentDialog?.show()
                }
            }
        }
    }

    private fun setAppLanguage(language: Language? = null) {
        AppCompatDelegate.setApplicationLocales(AppPreferences.decideLanguage(language))
    }

    private fun setDynamicColors(isApplied: Boolean) {
        val precondition = DynamicColors.Precondition { activity, _ ->
            isApplied
        }
        val options = DynamicColorsOptions.Builder()
            .setPrecondition(precondition)
            .build()
        DynamicColors.applyToActivitiesIfAvailable(
            application,
            options
        )
    }

    // --- DIALOG BUILDER FUNCTIONS ---

    private fun showEmailVerificationDialog(): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verification))
            .setMessage(getString(R.string.please_verify_your_email))
            .setNegativeButton(getString(R.string.refresh)) { _, _ ->
                // TODO: Implement a better way to refresh email verification status
                restartActivity() // Restart activity
            }
            .setPositiveButton(getString(R.string.send)) { _, _ ->
                viewModel.resendVerificationEmail() // Send verification email

                // Show confirmation dialog
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.verification))
                    .setMessage(getString(R.string.please_verify_your_email))
                    .setPositiveButton(getString(R.string.refresh)) { _, _ ->
                        // TODO: Implement a better way to refresh email verification status
                        restartActivity() // Restart activity
                    }
                    .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                        viewModel.signOut()
                    }
                    .setCancelable(false) // Prevent dismissing by back button
                    .create()
                    .apply {
                        setCanceledOnTouchOutside(false) // Prevent dismissing by clicking outside
                    }
                    .show()
            }
            .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                viewModel.signOut()
            }
            .setCancelable(false)
            .create()
    }

    private fun showAdminVerificationDialog(message: String): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verification))
            .setMessage(message)
            .setNeutralButton(getString(R.string.sign_out)) { _, _ ->
                viewModel.signOut()
            }
            .setCancelable(false)
            .create()
    }

    private fun showEligibilityDialog(title: Int, message: String): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.profile) { _, _ ->
                navController.navigate(R.id.action_global_profileFragment)
            }
            .setCancelable(false)
            .create()
    }

    // --- END DIALOG BUILDERS ---


    private fun setupUIComponents() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragmentActivityMain.id) as NavHostFragment
        navController = navHostFragment.navController
        navController.graph = navController.navInflater.inflate(R.navigation.nav_main)

        setupAppBar()

        val navView = binding.activityMainNavigationView
        navView.setupWithNavController(navController)

        val navViewCardHeader = navView.inflateHeaderView(R.layout.header_navigation_activity_main)
        navViewCardHeader.findViewById<TextView>(R.id.headerNavigationActivityMainDisplayName).text =
            auth.currentUser?.displayName
        navViewCardHeader.findViewById<TextView>(R.id.headerNavigationActivityMainEmail).text =
            auth.currentUser?.email

        skeleton = binding.navHostFragmentActivityMain.createSkeleton(
            SkeletonConfig.default(
                this,
                R.layout.skeleton_mask
            )
        )
    }

    private fun setupUIListeners() {
        binding.activityMainNavigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.activityMainDrawerMenuLogOut -> viewModel.signOut()
                R.id.activityMainDrawerMenuProfile -> navController.navigate(R.id.action_global_profileFragment)
                R.id.activityMainDrawerMenuOrder -> navController.navigate(R.id.action_global_orderServiceListFragment)

                R.id.activityMainDrawerMenuSettings -> navController.navigate(NavMainDirections.actionGlobalMonpresSettingFragment())

                R.id.activityMainDrawerMenuChatAdmin -> openWhatsAppUseCase(this, MainApplication.adminWANumber)
            }
            drawerLayout.close()
            true
        }

        binding.activityMainNavigationView.menu.findItem(R.id.activityMainDrawerMenuChatAdmin).isVisible = userRepository.getCurrentUserRecord()?.role != UserRole.ADMIN
    }

    private fun prepareProfileIconMenu(menu: Menu) {
        val profileItem = menu.findItem(R.id.menu_profile)
        val profileView = profileItem.actionView?.findViewById<ImageView>(R.id.profile_icon)

        profileView?.setOnClickListener {
            navController.navigate(R.id.action_global_profileFragment)
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        Glide.with(this)
            .load(
                "https://ui-avatars.com/api/?size=128&name=${
                    auth.currentUser?.displayName?.replace(" ", "-")
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
            .circleCrop()
            .error(R.drawable.person_24px)
            .into(profileView!!)
    }

    private fun navigateToLogin() {
        OrderServiceNotification.cancelAll(this@MainActivity)
        stopAllTrackingOrder()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun getNotificationPermissions(): Array<String> {
        val permissions: MutableList<String> = mutableListOf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    private fun showPermissionSettingsDialog(permission: String) {
        val permissionName = extractPermissionName(permission)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.x_permission_is_not_granted, permissionName))
            .setMessage(getString(R.string.this_permission_is_disabled))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .create()
            .show()
    }

    private fun extractPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> getString(R.string.camera)
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE -> getString(
                R.string.gallery
            )

            Manifest.permission.POST_NOTIFICATIONS -> getString(R.string.notification)
            else -> ""
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
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        viewModel.setPendingIntent(intent) // Pass to ViewModel for processing
    }

    private fun stopAllTrackingOrder() {
        val intent = Intent(application, OrderServiceLocationTrackingService::class.java).apply {
            action = OrderServiceLocationTrackingService.ACTION_STOP
        }
        application.startService(intent)
        Log.d(TAG, "MainActivity requested to STOP all Location Services.")
    }

    private fun handleConnectionChange(isConnected: Boolean) {
        // We need to re-apply insets to the AppBar when connection changes
        // to toggle its top padding
        binding.activityMainAppBarLayout.apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val topPadding = if (isConnected) insets.top else 0
                v.setPadding(insets.left, topPadding, insets.right, 0)
                windowInsets
            }
        }
        ViewCompat.requestApplyInsets(binding.activityMainAppBarLayout)
    }

    override fun onStop() {
        super.onStop()
        NetworkMonitor.cleanup()
    }

    override fun onRestart() {
        super.onRestart()
        NetworkMonitor.registerNetworkCallback()
    }

    override fun onStart() {
        super.onStart()
        // Get the current language setting from the Android system for this app.
        val currentSystemLangTag = if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            Language.SYSTEM.code
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }

        Log.d(TAG, "onStart: Current system language for app is '$currentSystemLangTag'")

        // Tell the ViewModel to sync this value with DataStore.
        // The ViewModel will handle the logic of checking if an update is needed.
        viewModel.syncLanguageWithSystem(currentSystemLangTag)
    }

    private fun applyInitialSettings() {
        // This is one of the few acceptable places to use runBlocking, because it's
        // essential for the initial UI state and only runs once on activity creation.
        lifecycleScope.launch {
            val language = viewModel.language.first()
            setAppLanguage(enumByNameIgnoreCaseOrNull<Language>(language))

            val isDynamic = viewModel.isDynamicColorApplied.first()
            setDynamicColors(isDynamic)

            val theme = viewModel.themeMode.first()
            val modeInt = AppPreferences.decideThemeMode(
                enumByNameIgnoreCaseOrNull<ThemeMode>(
                    theme,
                    ThemeMode.SYSTEM
                )!!
            )
            AppCompatDelegate.setDefaultNightMode(modeInt)
        }
    }
}
