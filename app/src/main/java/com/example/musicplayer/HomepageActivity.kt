// HomepageActivity.kt
package com.example.musicplayer

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.UUID
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.example.musicplayer.Managers.AlbumDao
import com.example.musicplayer.Managers.ArtistDao
import com.example.musicplayer.Managers.PlaylistDao
import com.example.musicplayer.Managers.ShowPlaylistIcon
import com.example.musicplayer.Managers.Song
import com.example.musicplayer.Managers.SongDao
import com.example.musicplayer.Managers.SongPlaylistDao
import com.example.musicplayer.Managers.SongQueue
import com.example.musicplayer.Managers.downloadFile
import com.example.musicplayer.Managers.initiateSong
import com.example.musicplayer.Managers.onPlaylistPage
import com.example.musicplayer.Managers.sanitizeFileName
import com.example.musicplayer.Managers.searchSongs
import com.example.musicplayer.Managers.SongOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.PlaylistsApi
import java.io.File
import java.io.IOException

// Database
var songDao: SongDao? = null
var playlistDao: PlaylistDao? = null
var songPlaylistDao: SongPlaylistDao? = null
var albumDao: AlbumDao? = null
var artistDao: ArtistDao? = null


// User data
var apiKey: String = ""
var userId: String = ""

// Song playing capabilities
var mediaPlayer: MediaPlayer? = null
var pausedPosition: Int = 0
var queue: SongQueue? = null
var songsToPlay: MutableList<Song> = mutableListOf()
var originalSongsList: List<Song> = emptyList()
var progress = 0f
var clickCounter = 0
var finishedDownload = false
var doneSetUpSongs = false

// Setup
var doneSetUpDB = false
var doneSetUpHome = false
var navBarHeight = 72

var songLibraryId: UUID? = null
var playlistLibraryId: UUID? = null

// Initialize global colors for the whole file
var white: Int = 0xFFFFFF
var darkGrey: Int = 0xFFFFFF
var grey: Int = 0xFFFFFF
var purpleGrey: Int = 0xFFFFFF
var lightPurple: Int = 0xFFFFFF
var darkPurple: Int = 0xFFFFFF

var api: ApiClient? = null
var ip: String = ""

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, reactiveState: ReactiveState) {
    // Scope and context
    val applicationContext = LocalContext.current
    val scope = CoroutineScope(Dispatchers.Default)

    val interactionSource = remember { MutableInteractionSource() }
    var searchQuery by remember { mutableStateOf("") }

    var clickedSong: Song? = null // for options bar
    val addToPlaylistDialog = remember { mutableStateOf(false) }

    // User and Api variables
    val sharedPrefs = reactiveState.sharedPrefs

    LaunchedEffect(api != null && !doneSetUpHome) {
        doneSetUpHome = true

        if (songDao != null) {
            scope.launch {
                // Only update the songs list if the user isn't querying songs
                val songsArrayTemp = songDao!!.getAll().sortedBy { it.name }
                withContext(Dispatchers.Main) {
                    if (searchQuery == "") {
                        reactiveState.songsArray.value = songsArrayTemp.toMutableList()
                    }
                }
            }
        }

    }

    scope.launch {

        // Get songs array sorted by name
        if (songDao != null) {
            var songsArray2 = songDao!!.getAll().sortedBy { it.name }
            withContext(Dispatchers.Main) {
                // Only update the songs array if the gotton songs aren't empty and the user isn't querying
                if (songsArray2.size != 0 && searchQuery == "") {
                    reactiveState.songsArray.value = songsArray2.toMutableList()
                    originalSongsList = reactiveState.songsArray.value
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(darkGrey)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            var disabledNavBar = false
            if (reactiveState.songsArray.value.size == 0) {disabledNavBar = true}
            navBar("Songs", navController, sharedPrefs, navBarHeight, disabledNavBar)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = navBarHeight.dp) // Adjust top padding to match navbar height
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val focusManager = LocalFocusManager.current

                    // Query box
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            scope.launch {
                                searchQuery = it
                                reactiveState.songsArray.value = searchSongs("%$searchQuery%").sortedBy { it.name }.toMutableList()
                            }
                        },
                        label = {Text(text="Search for songs")},
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                        textStyle = TextStyle(
                            color = Color(white),
                            fontSize = 16.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .height(52.dp)
                            .fillMaxWidth()
                            .background(Color(grey), RectangleShape)
                            ,
                        trailingIcon = {
                            IconButton(onClick = {
                                // Remove focus and update songsArray to have all songs
                                searchQuery = ""
                                scope.launch {
                                    reactiveState.songsArray.value =
                                        searchSongs("%$searchQuery%").sortedBy { it.name }.toMutableList()
                                }
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(lightPurple))
                            }
                        },
                        colors = TextFieldDefaults.textFieldColors(
                            textColor = Color(white),
                            containerColor = Color(grey),
                            focusedIndicatorColor = Color(lightPurple),
                            focusedLabelColor = Color(lightPurple),
                            unfocusedIndicatorColor = Color(lightPurple),
                            cursorColor = Color(white),
                            disabledLabelColor = Color(lightPurple),
                            unfocusedLabelColor = Color(lightPurple),
                        )
                    )
                }
                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .background(Color(darkGrey))
                ) {
                    item { waitNotice(reactiveState.songsArray.value, doneSetUpSongs)}
                    // Create a clickable song row for each song from the song data
                    items(reactiveState.songsArray.value.size) { index ->
                        val song = reactiveState.songsArray.value[index]
                        val interactionSource = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(darkGrey))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = rememberRipple(
                                        bounded = true,
                                        radius = Dp.Unspecified
                                    )
                                ) {
                                    initiateSong(reactiveState, song)
                                }
                                .padding(16.dp, 8.dp, 16.dp, 8.dp)
                                .clipToBounds(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (song.imageData == null) {
                                Image(
                                    painter = painterResource(id = R.drawable.song_icon),
                                    contentDescription = "Song Image",
                                    modifier = Modifier
                                        .size(48.dp)
                                )
                            } else {
                                Image(
                                    bitmap = song.getBitMap(),
                                    contentDescription = "Song Image",
                                    modifier = Modifier
                                        .size(48.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = song.name!!,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 16.sp,
                                        color = Color(white)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(270.dp)
                                )
                                Text(
                                    text = song.artist!!,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        color = Color(white)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(250.dp)
                                )
                            }
                            SongOptions(song, addToPlaylistDialog, onClickedSongChange = { clickedSong = song }, false, navController, ip)
                        }

                    }
                    item { Spacer(modifier = Modifier.height(112.dp)) }
                }
            }
            when {
                addToPlaylistDialog.value -> {
                    AddToPlaylistDialog(
                        onDismissRequest = {
                            addToPlaylistDialog.value = false
                            clickedSong = null
                        },
                        onConfirmation = {
                            addToPlaylistDialog.value = false
                            clickedSong = null
                        },
                        clickedSong!!,
                        scope,
                        api,
                        reactiveState
                    )
                }
            }
            ButtonWithIcon(
                onClick = {
                    reactiveState.shuffle = true
                    initiateSong(reactiveState, reactiveState.currentSong, false)
                    queue!!.dequeue()
                },

                Icons.Default.Shuffle,
                reactiveState,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
            reactiveState.progress = mediaPlayer?.currentPosition?.div(mediaPlayer?.duration?.toFloat() ?: 1f) ?: 0f
            // Music playing bar
            if (reactiveState.barOn || clickCounter>0) {
                PlayingBar(
                    interactionSource = interactionSource,
                    navController = navController,
                    reactiveState = reactiveState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
    // Download songs
    if (doneSetUpSongs && !finishedDownload) {
        for (song in reactiveState.songsArray.value) {
            val fileName = "${song.songId}_${sanitizeFileName(song.name!!)}.mp3"
            val outputFilePath = File(songsDirectory, fileName).absolutePath
            val songStreamUrl = "${ip}/Audio/${song.songId}/stream?api_key=$apiKey&UserId=$userId"
            scope.launch() {
                downloadFile(songStreamUrl, outputFilePath)
            }
        }
        finishedDownload = true
    }

}

fun playSong(song: Song,
             startOver: Boolean,
             play: Boolean,
             progress2: Float = 0f,
             reactiveState: ReactiveState
): Boolean {
    var isPlaying = false
    progress = progress2

    // If play is false, stop the song and return
    if (!play && song == reactiveState.currentSong) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                pausedPosition = it.currentPosition
            }
        }

        return isPlaying
    }

    // Initiate song file path
    val outputFilePath = song.songFilePath

    // Check if the song file exists locally
    val file = File(outputFilePath)
    val songStreamUrl = if (file.exists()) {
        outputFilePath
    } else {
        // If the song file doesn't exist locally, stream it from the server
        "${ip}/Audio/${song.songId}/stream?api_key=$apiKey&UserId=${userId}"
    }

    // If a song is already playing and startOver is false, continue playing
    if (mediaPlayer != null && reactiveState.currentSong == song && !startOver) {
        mediaPlayer!!.seekTo(pausedPosition)
        mediaPlayer!!.start()
        return mediaPlayer!!.isPlaying
    }

    // If a song is already playing, stop it
    mediaPlayer?.let {
        if (it.isPlaying) {
            it.stop()
            it.release()
        }
    }

    // Start the song
    mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        try {
            setDataSource(songStreamUrl)
            prepare() // might take long! (for buffering, etc)
            if (play) {
                start()
            }
            isPlaying = true
        } catch (e: IOException) {
            println("Error playing song: $e")
            isPlaying = false
        }
        setOnCompletionListener {
            isPlaying = false
        }

        setOnPreparedListener {
            val duration = it.duration
            setOnSeekCompleteListener { mp ->
                progress = mp.currentPosition / duration.toFloat()
            }
        }
    }

    reactiveState.currentSong = song
    return isPlaying
}

fun onPlayNextSong(reactiveState: ReactiveState) {
    reactiveState.playing = true
    progress = 0f

    if (queue!!.getSize() == 0 && reactiveState.repeat)
    {
        // End of queue repeat queue
        queue?.setQueue(songsToPlay[0], reactiveState.shuffle)
        playSong(queue!!.dequeue(),true,true,0f, reactiveState)
    } else if (queue!!.getSize() > 0)
    {
        // Go forward with next song in queue
        playSong(queue?.dequeue()!!,true,true,0f, reactiveState)

    } else {
        // Wind back to start of queue but stop song
        reactiveState.playing = false
        queue?.setQueue(songsToPlay[0], reactiveState.shuffle)
        playSong(queue!!.dequeue(), true, false, 0f, reactiveState)
    }

}

// Set up the playing of the previous song
fun onPlayPreviousSong(reactiveState: ReactiveState) {
    reactiveState.playing = true
    progress = 0f
    val previousSong = queue?.getPreviousSong(reactiveState)!!

    queue!!.setQueue(previousSong, reactiveState.shuffle)
    playSong(previousSong,true,true,0f, reactiveState)
    queue!!.dequeue() // Queue.front() has has current song so remove it
}

// Button with passable icon
@Composable
fun ButtonWithIcon(onClick: () -> Unit, icon: ImageVector, reactiveState: ReactiveState, modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .zIndex(1f)
            .offset(
                y = if (reactiveState.barOn || clickCounter > 0) { (-76).dp } else { 0.dp }
            )
            .size(56.dp)
            .clip(RoundedCornerShape(15))
            .background(Color(darkPurple))
            .clickable { onClick() }

    ) {
        Icon(
            icon,
            contentDescription = "Button",
            tint = Color.White,
            modifier = Modifier.padding(16.dp)
        )
    }
}

//Playing bar at the bottom
@Composable
fun PlayingBar(
    interactionSource: MutableInteractionSource,
    navController: NavHostController,
    reactiveState: ReactiveState,
    modifier: Modifier
) {
    // Container for Bar
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(purpleGrey))
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = true, radius = Dp.Unspecified)
                ) { // Open SongActivity
                    navController.navigate(NavGraph.Song.route)
                }
                .padding(8.dp)
                .clipToBounds(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // At the top row show Image...
            Image(
                bitmap = reactiveState.currentSong.getBitMap(),
                contentDescription = "Song Image",
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 8.dp) // Added padding to press the song and artist name to the image's right
            ) {
                // ...and the song name
                Text(
                    text = reactiveState.currentSong?.name.toString(),
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(270.dp)
                )

                // and the artist name
                Text(
                    text = reactiveState.currentSong?.artist.toString(),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(270.dp)
                )
            }

            Row(
                modifier = Modifier.width(150.dp), // Added to push the icons to the right
                horizontalArrangement = Arrangement.End
            ) {
                // Play pause button
                IconButton(onClick = { // onPlayPauseClicked
                    reactiveState.playing = !reactiveState.playing
                    playSong(reactiveState.currentSong!!, false, reactiveState.playing, progress, reactiveState)
                }) {
                    // Icon for play pause button
                    Icon(
                        imageVector = if (reactiveState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (reactiveState.playing) "Pause" else "Play",
                        tint = Color(white),
                    )
                }

                IconButton(onClick = { // onPlayNextSong
                    onPlayNextSong(reactiveState)
                }) {
                    // Skip song icon
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Skip",
                        tint = Color(white),
                        modifier = Modifier.width(110.dp)
                    )
                }
            }
        }
        // Playing bar progress bar for song
        LinearProgressIndicator(
            progress = reactiveState.progress,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(purpleGrey))
                .padding(8.dp, 0.dp, 8.dp, 8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(lightPurple),
            backgroundColor = Color(grey)
        )
    }
    updateProgress(reactiveState)
}

// Function to keep updating the progress bar based on the played part of the song
// We have to do this since there aren't composable functions to do this normally
@Composable
fun updateProgress(reactiveState: ReactiveState) {

    // Only update when the music player is playing
    LaunchedEffect(key1 = mediaPlayer?.isPlaying) {
        var isEnd = false
        var lastProgress = -1f


        while (!isEnd) { // Keeps refreshing for a song

            // Get the progress of the song to use elsewhere in the app
            if (reactiveState.playing) {
                reactiveState.progress =
                    mediaPlayer?.currentPosition?.div(mediaPlayer?.duration?.toFloat() ?: 1f) ?: 0f
            }

            // Check whether the song is playing, if not stop updating
            if (lastProgress == reactiveState.progress && reactiveState.playing) {
                isEnd = true
            }

            lastProgress = reactiveState.progress

            // If we're at the end play the next song
            if (isEnd) {
                isEnd = false
                progress = 0f
                onPlayNextSong(reactiveState)
            }
            delay(1000) // refresh each second
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    song: Song,
    scope: CoroutineScope,
    api: ApiClient?,
    reactiveState: ReactiveState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        AlertDialog(
            containerColor = Color(darkGrey),
            text = {
                Card(
                    shape = RoundedCornerShape(0.dp),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color(darkGrey))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(text = "Playlists", color = Color(white),
                            style = TextStyle(fontSize = 16.sp),
                            modifier = Modifier
                                .padding(8.dp)
                        )
                        // Get all current playlists sorted by name
                        if (playlistDao != null ) {
                            scope.launch {
                                reactiveState.playlistsArray.value = playlistDao?.getAll()!!.sortedBy { it.name }.toMutableList()
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .background(Color(darkGrey))
                        ) {
                            // Create an item row for each playlist
                            items(reactiveState.playlistsArray.value.count()){ index ->
                                var playlist = reactiveState.playlistsArray.value[index]
                                val interactionSource = remember { MutableInteractionSource() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(darkGrey))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = rememberRipple(
                                                bounded = true,
                                                radius = Dp.Unspecified
                                            )
                                        ) {
                                            // If the playlist is played add the song to the playlist
                                            CoroutineScope(Dispatchers.IO).launch {
                                                try {
                                                    val playlistsApi = PlaylistsApi(api!!)

                                                    playlistsApi.addToPlaylist(
                                                        formatUUIDFromString(playlist.playlistId)!!,
                                                        listOf(formatUUIDFromString(song.songId)!!)
                                                    )
                                                    // add the song-playlist relationship cross-ref
                                                    songPlaylistDao?.insertAll(
                                                        SongPlaylist(
                                                            song.songId,
                                                            playlist.playlistId
                                                        )
                                                    )

                                                    playlist.length += song.length
                                                    playlistDao!!.insertAll(playlist)

                                                } catch (e: Exception) {
                                                    println("Couldn't add song to playlist - no internet: ${e}")
                                                }

                                            }

                                            onDismissRequest()

                                        }
                                        .padding(16.dp, 8.dp, 16.dp, 8.dp)
                                        .clipToBounds(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {

                                    if (!onPlaylistPage) {
                                        ShowPlaylistIcon(playlist)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .align(Alignment.CenterVertically)
                                            .weight(1f)
                                    ) {
                                        Text(
                                            text = playlist.name,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontSize = 16.sp,
                                                color = Color(white)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            },
            onDismissRequest = {},
            confirmButton = {},
            dismissButton = {
                Text(
                    text = "Cancel",
                    color = Color(lightPurple),
                    style = TextStyle(fontSize = 16.sp),
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable {
                            onDismissRequest()
                        }
                )
            }
        )
    }
}
@Composable
fun waitNotice(songsArray: List<Any>, doneSetUp: Boolean) {
    if (songsArray.size == 0 && !doneSetUp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(darkGrey))
                .padding(16.dp, 8.dp, 16.dp, 8.dp)
                .clipToBounds(),
        ) {
            Text(
                text = "Initiating page, this could take a minute...",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color(white)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

fun formatUUIDFromString(stringID: String): java.util.UUID? {
    val IdString = stringID
    val withHyphens = "${IdString.substring(0, 8)}-${IdString.substring(8, 12)}-${IdString.substring(12, 16)}-${IdString.substring(16, 20)}-${IdString.substring(20)}"
    return UUID.fromString(withHyphens)
}