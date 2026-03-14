package com.example.musicplayer

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musicplayer.Managers.PlaylistOptions
import com.example.musicplayer.Managers.RenamePlaylistDialog
import com.example.musicplayer.Managers.ShowPlaylistIcon
import com.example.musicplayer.Managers.Song
import com.example.musicplayer.Managers.getPlaylistSongs
import com.example.musicplayer.Managers.initiateSong
import com.example.musicplayer.Managers.onPlaylistPage
import com.example.musicplayer.Managers.SongOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

val playlistCoverSize = 280

var doneSetUpPlaylist = false

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun PlaylistScreen(navController: NavHostController, reactiveState: ReactiveState) {

    // Initiate base variables and states
    val interactionSource = remember { MutableInteractionSource() }
    var songsArray by remember { mutableStateOf(listOf<Song>()) }
    val scope = CoroutineScope(Dispatchers.Default)
    val openRenameDialog = remember { mutableStateOf(false) }
    val sharedPrefs = reactiveState.sharedPrefs
    val ip = sharedPrefs.getString("ip", "localhost:8096")

    var clickedSong: Song? = null // for options bar
    val addToPlaylistDialog = remember { mutableStateOf(false) }


    if(!doneSetUpPlaylist || songsArray.size == 0) {
        LaunchedEffect(key1 = true) {
            scope.launch {
                songsArray = getPlaylistSongs(currentPlaylist?.playlistId!!).sortedBy { it.name }
            }
            doneSetUpPlaylist = true
        }
    }
    originalSongsList = songsArray.sortedBy { it.name }.toMutableList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("", color = Color(white)) },
                navigationIcon = {
                    IconButton(onClick = {
                        onPlaylistPage = false
                        doneSetUpPlaylist = false
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(white))
                    }
                },
                actions = {
                    var expanded = remember { mutableStateOf(false) }

                    IconButton(onClick = {
                        expanded.value = true
                    }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Settings",
                            tint = Color(white)
                        )
                        PlaylistOptions(expanded = expanded, openRenameDialog, currentPlaylist!!, scope, navController, reactiveState)
                    }
                },
                backgroundColor = Color(purpleGrey),
            )
        },
        modifier = Modifier.fillMaxSize(),
        backgroundColor = Color(darkGrey)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {


            LazyColumn(
                modifier = Modifier
                    .background(Color(darkGrey))
            ) {
                item {
                    Box(
                    modifier = Modifier
                        .fillMaxWidth()
                )
                    {
                        ShowPlaylistIcon(
                            playlist = currentPlaylist!!
                        )
                    }

                }
                // First Row
                item {
                    Column (
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Playlists",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = Color(white)
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(text = currentPlaylist?.name!!, style = TextStyle(
                            fontSize = 24.sp,
                            color = Color(white)
                        ), modifier = Modifier.padding(bottom = 8.dp))
                        Text(
                            text = songsArray.size.toString()+" Songs • "
                                    +formatTime(currentPlaylist?.length),
                            style = TextStyle(
                                fontSize = 18.sp,
                                color = Color(white)
                            ), modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.End),
                            horizontalArrangement = Arrangement.End
                        ) {
                            // Left-side Button - "Play"
                            Button(
                                onClick = {
                                    if (songsArray.size != 0) {
                                        initiateSong(reactiveState, songsArray[0])
                                    }
                                },
                                modifier = Modifier
                                    .background(color = Color(darkGrey))
                                    .padding(top = 8.dp, end = 8.dp)
                                    .weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(grey))
                            ) {
                                Text(
                                    text = "Play",
                                    color = Color(white)
                                )
                            }

                            // Right-side Button - "Random"
                            Button(
                                onClick = {
                                        if (songsArray.size != 0) {
                                            reactiveState.shuffle = true
                                            initiateSong(
                                                reactiveState,
                                                reactiveState.currentSong,
                                                false)
                                            queue!!.dequeue()
                                        } },
                                modifier = Modifier
                                    .background(color = Color(darkGrey))
                                    .padding(top = 8.dp, start = 8.dp)
                                    .weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(
                                    lightPurple))
                            ) {
                                Text(
                                    text = "Shuffle",
                                    color = Color(darkPurple)
                                )
                            }
                        }
                    }

                    // Divider
                    Divider(
                        color = Color(purpleGrey),
                        thickness = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    Text(text = "Songs", modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                        color = Color(white)
                        )
                }

                // Create each item from the song data
                items(songsArray.size) { index ->
                    var song = songsArray[index]
                    val interactionSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(darkGrey))
                            .padding(top = 8.dp)
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
                        SongOptions(song, addToPlaylistDialog, onClickedSongChange = {
                            clickedSong = song
                        }, true, navController, ip!!)
                    }
                }

                item { Spacer(modifier = Modifier.height(112.dp)) }
            }
            when {
                openRenameDialog.value -> {
                    RenamePlaylistDialog(
                        onDismissRequest = { openRenameDialog.value = false },
                        onConfirmation = {
                            onPlaylistPage = true
                            openRenameDialog.value = false
                            MainScope().launch {
                                navController.navigate(NavGraph.Playlist.route)
                            }
                        },
                        api = api,
                        playlist = currentPlaylist!!,
                        scope = scope,
                        ip = sharedPrefs.getString("ip", "http://localhost:8096")!!
                    )
                }
            }
            reactiveState.progress =
                mediaPlayer?.currentPosition?.div(mediaPlayer?.duration?.toFloat() ?: 1f) ?: 0f
            // Music playing bar
            if (reactiveState.barOn || clickCounter > 0) {
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

}

