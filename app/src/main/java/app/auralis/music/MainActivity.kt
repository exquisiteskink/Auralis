package app.auralis.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.auralis.music.ui.album.AlbumScreen
import app.auralis.music.ui.artist.ArtistAlbumsScreen
import app.auralis.music.ui.artist.ArtistScreen
import app.auralis.music.ui.artist.PopularSongsScreen
import app.auralis.music.ui.artists.ArtistsScreen
import app.auralis.music.ui.home.HomeScreen
import app.auralis.music.ui.login.LoginScreen
import app.auralis.music.ui.player.NowPlayingHost
import app.auralis.music.ui.playlist.PlaylistScreen
import app.auralis.music.ui.search.SearchScreen
import app.auralis.music.ui.settings.AppearancePrefs
import app.auralis.music.ui.settings.SettingsScreen
import app.auralis.music.ui.theme.AuralisTheme
import app.auralis.music.ui.theme.LocalClient
import app.auralis.music.ui.theme.LocalContainer
import app.auralis.music.ui.theme.LocalPalette
import app.auralis.music.ui.theme.LocalPlayer
import app.auralis.music.data.player.AuralisPalette
import app.auralis.music.ui.theme.ThemeMode
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AuralisApp
        val container = app.container
        setContent {
            val prefs = remember { AppearancePrefs(this) }
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var transcode by remember { mutableIntStateOf(prefs.transcode) }
            val playerState by container.player.state.collectAsState()
            val loggedIn by container.loggedIn.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            container.player.setPreferDark(dark)
            container.player.transcodeBitrate = transcode

            LaunchedEffect(Unit) {
                val stored = container.credentials.load() ?: return@LaunchedEffect
                runCatching {
                    container.client.login(stored)
                    container.player.transcodeBitrate = stored.transcodeBitrate
                    container.setLoggedIn(true)
                }.onFailure {
                    container.credentials.clear()
                    container.setLoggedIn(false)
                }
            }

            val palette = playerState.palette.let { pal ->
                if (pal.isDark == dark) pal
                else if (dark) AuralisPalette.darkDefault()
                else AuralisPalette.lightDefault()
            }

            CompositionLocalProvider(
                LocalContainer provides container,
                LocalClient provides container.client,
                LocalPlayer provides container.player,
            ) {
                AuralisTheme(palette = palette, themeMode = themeMode) {
                    val p = LocalPalette.current
                    SideEffect {
                        WindowCompat.getInsetsController(window, window.decorView)
                            .isAppearanceLightStatusBars = !p.isDark
                    }
                    AuralisRoot(
                        loggedIn = loggedIn,
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it; prefs.themeMode = it },
                        transcode = transcode,
                        onTranscode = {
                            transcode = it
                            prefs.transcode = it
                            container.player.transcodeBitrate = it
                            val creds = container.credentials.load()
                            if (creds != null) container.credentials.save(creds.copy(transcodeBitrate = it))
                        },
                    )
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val filled: androidx.compose.ui.graphics.vector.ImageVector, val outline: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("artists", "Artists", Icons.Rounded.Person, Icons.Outlined.Person),
    Tab("search", "Search", Icons.Rounded.Search, Icons.Outlined.Search),
    Tab("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
private fun AuralisRoot(
    loggedIn: Boolean,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    transcode: Int,
    onTranscode: (Int) -> Unit,
) {
    val nav = rememberNavController()
    val p = LocalPalette.current
    val container = LocalContainer.current
    val playerState by container.player.state.collectAsState()
    val sheet = remember { mutableFloatStateOf(0f) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTabs = route in tabs.map { it.route }
    val showNav = loggedIn && onTabs && sheet.floatValue < 0.45f

    LaunchedEffect(loggedIn) {
        val current = nav.currentDestination?.route
        if (loggedIn && (current == "login" || current == null)) {
            nav.navigate("home") { popUpTo("login") { inclusive = true } }
        } else if (!loggedIn && current != null && current != "login") {
            nav.navigate("login") { popUpTo(0) { inclusive = true } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showNav) {
                    NavigationBar(
                        containerColor = p.surface.copy(alpha = 0.55f),
                        contentColor = p.onBackground,
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) tab.filled else tab.outline, tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = p.primary,
                                    selectedTextColor = p.primary,
                                    indicatorColor = p.primary.copy(alpha = 0.18f),
                                    unselectedIconColor = p.onBackground.copy(alpha = 0.55f),
                                    unselectedTextColor = p.onBackground.copy(alpha = 0.55f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { _ ->
            NavHost(
                navController = nav,
                startDestination = if (loggedIn) "home" else "login",
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable("login") { LoginScreen() }
                composable("home") {
                    HomeScreen(
                        onPlaylist = { nav.navigate("playlist/$it") },
                        onAlbum = { nav.navigate("album/$it") },
                    )
                }
                composable("artists") { ArtistsScreen(onArtist = { nav.navigate("artist/$it") }) }
                composable("search") {
                    SearchScreen(
                        onArtist = { nav.navigate("artist/$it") },
                        onAlbum = { nav.navigate("album/$it") },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeMode = onThemeMode,
                        transcode = transcode,
                        onTranscode = onTranscode,
                        onLoggedOut = { container.setLoggedIn(false) },
                    )
                }
                composable(
                    "artist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    ArtistScreen(
                        artistId = id,
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/$it") },
                        onPopular = { artistId, name -> nav.navigate("artist/$artistId/popular/${encode(name)}") },
                        onAlbums = { artistId, name -> nav.navigate("artist/$artistId/albums/${encode(name)}") },
                        onArtist = { nav.navigate("artist/$it") },
                    )
                }
                composable(
                    "artist/{id}/popular/{name}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                    ),
                ) { entry ->
                    PopularSongsScreen(
                        artistId = entry.arguments?.getString("id") ?: return@composable,
                        artistName = decode(entry.arguments?.getString("name").orEmpty()),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "artist/{id}/albums/{name}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                    ),
                ) { entry ->
                    ArtistAlbumsScreen(
                        artistId = entry.arguments?.getString("id") ?: return@composable,
                        artistName = decode(entry.arguments?.getString("name").orEmpty()),
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/$it") },
                    )
                }
                composable(
                    "album/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    AlbumScreen(
                        albumId = entry.arguments?.getString("id") ?: return@composable,
                        onBack = { nav.popBackStack() },
                        onArtist = { nav.navigate("artist/$it") },
                    )
                }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    PlaylistScreen(
                        playlistId = entry.arguments?.getString("id") ?: return@composable,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }

        if (loggedIn && playerState.current != null) {
            NowPlayingHost(
                sheet = sheet,
                onArtist = { nav.navigate("artist/$it") },
                bottomNavVisible = onTabs,
            )
        }
    }
}

private fun encode(value: String): String = android.net.Uri.encode(value)
private fun decode(value: String): String = android.net.Uri.decode(value)
