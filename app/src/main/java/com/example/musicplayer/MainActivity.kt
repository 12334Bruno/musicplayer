package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.musicplayer.Managers.Album
import com.example.musicplayer.Managers.Artist
import com.example.musicplayer.Managers.Playlist
import com.example.musicplayer.Managers.Song
import com.example.musicplayer.Managers.SongQueue
import com.example.musicplayer.Managers.setUpAlbums
import com.example.musicplayer.Managers.setUpArtits
import com.example.musicplayer.Managers.setUpPlaylists
import com.example.musicplayer.Managers.setUpSongs
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.io.File


class ReactiveState : ViewModel() {
    lateinit var sharedPrefs: SharedPreferences
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(true)
    var progress by mutableFloatStateOf(0f)
    var currentSong by mutableStateOf(Song("none", "none", "none", null, null, 0))
    var barOn by mutableStateOf(false)
    var songsArray =  mutableStateOf(mutableListOf<Song>())
    var playlistsArray =  mutableStateOf(mutableListOf<Playlist>())
    var artistsArray =  mutableStateOf(mutableListOf<Artist>())
    var albumsArray =  mutableStateOf(mutableListOf<Album>())
}

var songsDirectory: File? = null

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        songsDirectory = File(getExternalFilesDir(null), "songs")

        if (!songsDirectory!!.exists()) {
            songsDirectory!!.mkdirs()
        }

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        super.onCreate(savedInstanceState)
        setContent {
            MusicPlayerTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigator(sharedPreferences)
                }
            }
        }

    }
}

sealed class NavGraph(val route: String) {
    object Login : NavGraph("login")
    object Home : NavGraph("home")
    object Song : NavGraph("song")
    object Playlists : NavGraph("playlists")
    object Playlist : NavGraph("playlist")
    object Albums : NavGraph("albums")
    object Album : NavGraph("album")
    object Artists : NavGraph("artists")
    object Artist : NavGraph("artist")
}


@Composable
fun AppNavigator(sharedPreferences: SharedPreferences) {
    val navController = rememberNavController()
    val reactiveState = viewModel<ReactiveState>()
    val scope = CoroutineScope(Dispatchers.Default)
    val localContext: Context = LocalContext.current
    initiateAppColors(localContext)

    reactiveState.sharedPrefs = sharedPreferences
    var startDestination: String = NavGraph.Login.route

    //reactiveState.sharedPrefs.edit().putBoolean("loggedIn", false).apply()

    reactiveState.songsArray = rememberSaveable { mutableStateOf(mutableListOf()) }
    reactiveState.albumsArray = rememberSaveable { mutableStateOf(mutableListOf()) }
    reactiveState.playlistsArray = rememberSaveable { mutableStateOf(mutableListOf()) }
    reactiveState.artistsArray = rememberSaveable { mutableStateOf(mutableListOf()) }

    // Check whether the user is logged in
    if (reactiveState.sharedPrefs.getBoolean("loggedIn", false)) {
        // Initialize Song Manager and get songs
        if (!doneSetUpDB) {
            queue = SongQueue()
            reactiveState.repeat = true

            // Launch a new coroutine in the scope
            scope.launch {
                val db = Room.databaseBuilder(
                    localContext,
                    AppDatabase::class.java, "database-"
                ).fallbackToDestructiveMigration().build()
                songDao = db.songDao()
                playlistDao = db.playlistDao()
                songPlaylistDao = db.songPlaylistDao()
                albumDao = db.albumDao()
                artistDao = db.artistDao()
            }
            doneSetUpDB = true


            scope.launch {

                ip = reactiveState.sharedPrefs.getString("ip", "http://localhost:8096")!!
                apiKey = reactiveState.sharedPrefs.getString("accessToken", "")!!
                var systemApi: SystemApi? = null
                var systemInfo: Response<PublicSystemInfo>? = null

                try {
                    val jellyfin = createJellyfin {
                        clientInfo = ClientInfo(name = "My awesome client!", version = "1.33.7",)
                        context = localContext
                    }

                    api = jellyfin.createApi(
                        baseUrl = ip,
                        accessToken = reactiveState.sharedPrefs.getString("accessToken", "")
                    )

                    val sessionInfo =
                        api!!.sessionApi.getSessions(deviceId = api!!.deviceInfo.id).content.firstOrNull()
                    if (sessionInfo == null) println("Unknown session")
                    api!!.userId = sessionInfo?.userId
                    userId = api!!.userId.toString()

                    systemApi = SystemApi(api!!)
                    systemInfo = systemApi.getPublicSystemInfo()

                } catch (e: Exception) { // Something went wrong
                    println("Error creating apiClient: ${e}")
                }
                if (systemInfo != null) {

                    songLibraryId?.let { api!!.audioApi.getAudioStream(it) }
                    // Get song Library ID
                    val libraries by api!!.userViewsApi.getUserViews(includeHidden = false)
                    // Two names are hard-coded right now
                    libraries.items?.forEach { library ->
                        if (library.name == "Musik" || library.name == "Music") {
                            songLibraryId = library.id
                        } else if (library.name == "Playlists") {
                            playlistLibraryId = library.id
                        }
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    setUpSongs(reactiveState)
                    setUpAlbums(reactiveState)
                    setUpPlaylists(reactiveState)
                    setUpArtits(reactiveState)
                }
            }
            startDestination = NavGraph.Home.route
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavGraph.Login.route) { LoginScreen(navController, reactiveState) }
        composable(NavGraph.Home.route) { HomeScreen(navController,reactiveState) }
        composable(NavGraph.Song.route) { SongScreen(navController, reactiveState) }
        composable(NavGraph.Playlists.route) { PlaylistsPage(navController, reactiveState) }
        composable(NavGraph.Playlist.route) { PlaylistScreen(navController, reactiveState) }
        composable(NavGraph.Albums.route) { AlbumsPage(navController, reactiveState) }
        composable(NavGraph.Album.route) { AlbumScreen(navController, reactiveState) }
        composable(NavGraph.Artists.route) { ArtistsPage(navController, reactiveState)}
        composable(NavGraph.Artist.route) { ArtistScreen(navController, reactiveState) }
    }
}
