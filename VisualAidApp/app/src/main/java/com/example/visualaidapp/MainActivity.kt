package com.example.visualaidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables drawing behind system bars


        setContent {
            val navController = rememberNavController() // Navigation controller
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed) // State for drawer
            val scope = rememberCoroutineScope() // Coroutine scope for opening/closing drawer
            val mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
            val darkThemeEnabled by mainViewModel.darkThemeEnabled.observeAsState(false)
            var mainContentReady by remember { mutableStateOf(false) } // Track if HomePage is initialized

            MaterialTheme(colorScheme = if (darkThemeEnabled) darkColorScheme() else lightColorScheme()) {
                ModalNavigationDrawer(
                    drawerContent = {
                        ModalDrawerSheet {
                            // Settings header text
                            Text(
                                text = "Settings",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 35.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Dark mode toggle row
                            Row(modifier = Modifier.offset(x = 22.dp, y = 20.dp)) {
                                Text(
                                    text = "Dark Mode",
                                    modifier = Modifier.offset(x = -10.dp, y = 12.dp)
                                )
                                Switch(
                                    checked = darkThemeEnabled,
                                    onCheckedChange = { mainViewModel.setDarkTheme(it)},
                                    modifier = Modifier.semantics {
                                        contentDescription = "Toggle Dark Mode"
                                    }
                                )
                            }
                        }
                    },
                    drawerState = drawerState // Attach state to drawer
                ) {
                    if (mainContentReady) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                BottomAppBar {
                                    // Home icon button
                                    IconButton(
                                        onClick = { navController.navigate(HomePageScreen) },
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .focusable(enabled = mainContentReady)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Home"
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f)) // Push settings to the end

                                    // Settings icon button
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .focusable(enabled = mainContentReady)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "open settings"
                                        )
                                    }
                                }
                            }
                        ) { padding ->
                            Column(modifier = Modifier.padding(padding)) {
                                // Navigation host with routes
                                NavHost(
                                    navController = navController,
                                    startDestination = HomePageScreen
                                ) {
                                    composable<HomePageScreen> {
                                        HomePage(onReady = { mainContentReady = true })
                                    }
                                }
                            }
                        }
                    } else {
                        // Only show HomePage until it's ready
                        HomePage(onReady = { mainContentReady = true })
                    }
                }
            }
        }
    }
}
