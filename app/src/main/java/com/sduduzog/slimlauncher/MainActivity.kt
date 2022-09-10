package com.sduduzog.slimlauncher

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
import android.content.*
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import com.sduduzog.slimlauncher.di.MainFragmentFactoryEntryPoint
import com.sduduzog.slimlauncher.utils.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.lang.reflect.Method
import kotlin.math.absoluteValue


@AndroidEntryPoint
class MainActivity : AppCompatActivity(),
        SharedPreferences.OnSharedPreferenceChangeListener,
        HomeWatcher.OnHomePressedListener, IPublisher {

    private lateinit var settings: SharedPreferences
    private lateinit var navigator: NavController
    private lateinit var homeWatcher: HomeWatcher
    private lateinit var deviceManager: DevicePolicyManager
    private val subscribers: MutableSet<BaseFragment> = mutableSetOf()

    override fun attachSubscriber(s: ISubscriber) {
        subscribers.add(s as BaseFragment)
    }

    override fun detachSubscriber(s: ISubscriber) {
        subscribers.remove(s as BaseFragment)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun dispatchBack() {
        for (s in subscribers) if (s.onBack()) return
        completeBackAction()
    }

    private fun dispatchHome() {
        for (s in subscribers) s.onHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val entryPoint = EntryPointAccessors.fromActivity(this, MainFragmentFactoryEntryPoint::class.java)
        supportFragmentManager.fragmentFactory = entryPoint.getMainFragmentFactory()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        settings = getSharedPreferences(getString(R.string.prefs_settings), MODE_PRIVATE)
        settings.registerOnSharedPreferenceChangeListener(this)
        navigator = findNavController(this, R.id.nav_host_fragment)
        homeWatcher = HomeWatcher(this)
        homeWatcher.setOnHomePressedListener(this)
        deviceManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    override fun onResume() {
        super.onResume()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        toggleStatusBar()
    }

    override fun onStart() {
        super.onStart()
        homeWatcher.startWatch()
    }

    override fun onStop() {
        super.onStop()
        homeWatcher.stopWatch()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) toggleStatusBar()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        if (s.equals(getString(R.string.prefs_settings_key_theme), true)) {
            recreate()
        }
        if (s.equals(getString(R.string.prefs_settings_key_toggle_status_bar), true)) {
            toggleStatusBar()
        }
    }

    override fun getTheme(): Resources.Theme {
        val theme = super.getTheme()
        settings = getSharedPreferences(getString(R.string.prefs_settings), MODE_PRIVATE)
        val active = settings.getInt(getString(R.string.prefs_settings_key_theme), 0)
        theme.applyStyle(resolveTheme(active), true)
        return theme
    }

    override fun onBackPressed() {
        dispatchBack()
    }

    override fun onHomePressed() {
        dispatchHome()
        navigator.popBackStack(R.id.homeFragment, false)
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun toggleStatusBar() {
        val isHidden = settings.getBoolean(getString(R.string.prefs_settings_key_toggle_status_bar), false)
        if (isHidden) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    companion object {

        fun resolveTheme(i: Int): Int {
            return when (i) {
                1 -> R.style.AppThemeDark
                2 -> R.style.AppGreyTheme
                3 -> R.style.AppTealTheme
                4 -> R.style.AppCandyTheme
                5 -> R.style.AppPinkTheme
                6 -> R.style.AppThemeLight
                7 -> R.style.AppThemeWallpaperDark
                8 -> R.style.AppThemeWallpaperLight
                else -> R.style.AppTheme
            }
        }
    }

    private fun completeBackAction() {
        super.onBackPressed()
    }

    private fun isAccessServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        if (enabled == 1) {
            val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val res = prefString.contains(packageName + "/" + LockDeviceAccessibilityService::class.java.name)
            if(res && settings.getBoolean(getString(R.string.disable_double_tap), true))
            {
                val edit = settings.edit()
                edit.apply { putBoolean(getString(R.string.disable_double_tap), false) }.apply()
            }

            return res
        }
        return false
    }

    private fun obtainPermissionAlertDialog(str : String) {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setMessage(R.string.enable_double_tap_to_lock)

        builder.setPositiveButton(android.R.string.ok)
        { dialog: DialogInterface?, which: Int ->
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        builder.setNegativeButton(R.string.disable_feature)
        { dialog: DialogInterface, which: Int -> dialog.dismiss()
            val edit = settings.edit()
            edit.apply { putBoolean(getString(R.string.disable_double_tap), true) }.apply()

            Toast.makeText(this@MainActivity,
                "To enable this feature, go to $str settings on your device and turn on for Unlauncher", Toast.LENGTH_LONG).show()
        }

        val alert = builder.create()
        alert.show()

    }

    private val gestureDetector = GestureDetector(baseContext, object : SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            // Open Options
            val homeView = findViewById<View>(R.id.home_fragment)
            if(homeView != null) {
                findNavController(homeView).navigate(R.id.action_homeFragment_to_optionsFragment, null)
            }
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            val accessEnabled = isAccessServiceEnabled()

            if(settings.getBoolean(getString(R.string.disable_double_tap), false))
            {
                Log.d(MainActivity::class.java.name, "Double Tap Disabled!")
                return super.onDoubleTap(e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Taken from KISS Launcher
                if (accessEnabled) {
                    Log.d(MainActivity::class.java.name, "Lock!")
                    val intent = Intent (baseContext, LockDeviceAccessibilityService::class.java)
                    intent.setPackage(packageName)
                    intent.action = ACTION_LOCK
                    startService(intent)

                } else {
                    Log.d(MainActivity::class.java.name, "Not enabled!")
                    obtainPermissionAlertDialog("Accessibility")
                }
            }
            else {
                val adminComp = ComponentName(this@MainActivity, DeviceAdmin::class.java)

                if (deviceManager.isAdminActive(adminComp)) {
                    deviceManager.lockNow()
                    Log.d(MainActivity::class.java.name, "Lock! Device Admin")
                } else {
                    Log.d(MainActivity::class.java.name, "Not enabled! Device Admin")
                    obtainPermissionAlertDialog("Device Admin")
                }
            }

            return super.onDoubleTap(e)
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val homeView = findViewById<MotionLayout>(R.id.home_fragment)
            if (homeView != null) {
                val homeScreen = homeView.constraintSetIds[0]
                val isFlingFromHomeScreen = homeView.currentState == homeScreen
                val isFlingDown = velocityY > 0 && velocityY > velocityX.absoluteValue
                if (isFlingDown && isFlingFromHomeScreen) {
                    expandStatusBar()
                }
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }
    })

    @SuppressLint("WrongConstant")  // statusbar is an internal API
    private fun expandStatusBar() {
        try {
            getSystemService("statusbar")?.let { service ->
                val statusbarManager = Class.forName("android.app.StatusBarManager")
                val expand: Method = statusbarManager.getMethod("expandNotificationsPanel")
                expand.invoke(service)
            }
        } catch (e: Exception) {
            // Do nothing. There does not seem to be any official way with the Android SKD to open the status bar.
            // https://stackoverflow.com/questions/5029354/how-can-i-programmatically-open-close-notifications-in-android
            // This hack may break on future versions of Android (or even just not work for specific manufacturer variants).
            // So, if anything goes wrong, we will just do nothing.
            Log.e(
                "MainActivity",
                "Error trying to expand the notifications panel.",
                e
            )
        }
    }
}
