package com.chargesense.app.ui

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.annotation.DrawableRes
import com.chargesense.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.chargesense.app.data.Prefs
import com.chargesense.app.service.MonitorService
import com.chargesense.app.ui.screens.DashboardScreen
import com.chargesense.app.ui.screens.DetailKey
import com.chargesense.app.ui.screens.DetailScreen
import com.chargesense.app.ui.screens.HealthScreen
import com.chargesense.app.ui.screens.HistoryScreen
import com.chargesense.app.ui.screens.SettingsScreen
import com.chargesense.app.ui.theme.ChargeSenseTheme
import com.chargesense.app.ui.components.MotoNavBar
import androidx.compose.ui.unit.dp
import com.chargesense.app.ui.components.NavItem
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith

private enum class Tab(val label: String, val title: String, @DrawableRes val iconRes: Int, @DrawableRes val selectedRes: Int) {
    Dashboard("Home", "Moto Battery", R.drawable.nav_battery, R.drawable.nav_battery_filled),
    Health("Health", "Battery health", R.drawable.nav_health, R.drawable.nav_health_filled),
    History("Sessions", "Sessions", R.drawable.nav_history, R.drawable.nav_history_filled),
    Settings("Settings", "Settings", R.drawable.nav_settings, R.drawable.nav_settings_filled),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Optional extras let shortcuts (and adb) open a tab directly: tab=History, section=Idle.
        val startTab = intent?.getStringExtra("tab")
        val startSection = intent?.getStringExtra("section")
        setContent { ChargeSenseTheme { App(startTab, startSection) } }
    }

    override fun onResume() {
        super.onResume()
        // Restart the service if the system or an OEM task killer stopped it.
        if (Prefs.get(this).monitoringEnabled) runCatching { MonitorService.start(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(startTab: String? = null, startSection: String? = null) {
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.name.equals(startTab, true) } ?: Tab.Dashboard) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = tab != Tab.Dashboard && detail == null) { tab = Tab.Dashboard }
    BackHandler(enabled = detail != null) { detail = null }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    detail?.let { key ->
        DetailScaffold(key, onBack = { detail = null })
        return
    }

    // A fresh app-bar state per tab, so switching tabs doesn't inherit a half-collapsed title.
    val barState = key(tab) { rememberTopAppBarState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
    run {
        Row(Modifier.fillMaxSize()) {
            if (landscape) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(ImageVector.vectorResource(if (tab == t) t.selectedRes else t.iconRes), null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f).nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text(tab.title, modifier = Modifier.padding(start = Space.Gutter - 16.dp)) },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
                bottomBar = {
                    if (!landscape) {
                        MotoNavBar(
                            items = Tab.entries.map { NavItem(it.label, ImageVector.vectorResource(it.iconRes), ImageVector.vectorResource(it.selectedRes)) },
                            selected = tab.ordinal,
                            onSelect = { tab = Tab.entries[it] },
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    // Gentle fade-and-rise between tabs.
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(240, delayMillis = 60)) + slideInVertically(tween(240)) { it / 24 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            Tab.Dashboard -> DashboardScreen(
                                onOpenHealth = { tab = Tab.Health },
                                onOpenDetail = { detail = it },
                                onOpenHistory = { tab = Tab.History },
                                onOpenSettings = { tab = Tab.Settings },
                            )
                            Tab.Health -> HealthScreen()
                            Tab.History -> HistoryScreen(onOpenSession = { detail = DetailKey.session(it) }, startSection = startSection)
                            Tab.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen detail page with a back arrow and a collapsing title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(key: String, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(DetailKey.title(key)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Ph.ArrowLeft, "Back") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(Modifier.padding(padding)) { DetailScreen(key) }
    }
}
