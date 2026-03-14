package com.example.musicplayer

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musicplayer.Managers.Playlist
import com.example.musicplayer.Managers.PlaylistOptions
import com.example.musicplayer.Managers.RenamePlaylistDialog
import com.example.musicplayer.Managers.ShowPlaylistIcon
import com.example.musicplayer.Managers.onPlaylistPage
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject

val boxSize = 48

var currentPlaylist: Playlist? = null
var doneSetupPlaylists = false
var optionsPlaylist: Playlist? = null

@Composable
@SuppressLint("UnrememberedMutableState")
fun PlaylistsPage(navController: NavHostController, reactiveState: ReactiveState) {
    val interactionSource = remember { MutableInteractionSource() }
    val openCreateDialog = remember { mutableStateOf(false) }
    val openRenameDialog = remember { mutableStateOf(false) }
    val scope = CoroutineScope(Dispatchers.Default)
    val sharedPrefs = reactiveState.sharedPrefs

    LaunchedEffect(key1 = (reactiveState.playlistsArray.value.size == 0 && !doneSetupPlaylists)) {
        scope.launch {
            reactiveState.playlistsArray.value = playlistDao?.getAll()!!.sortedBy { it.name }.toMutableList()
            doneSetupPlaylists = true
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
            // Navbar row 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(purpleGrey))
                    .padding(16.dp, 16.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Music App",
                    color = Color(white),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            navBar("Playlists", navController, sharedPrefs, navBarHeight)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = navBarHeight.dp) // Adjust top padding to match navbar height
            ) {
                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .background(Color(darkGrey))
                ) {
                    // If the playlists haven't been loaded show wait notice
                    item { waitNotice(reactiveState.playlistsArray.value, doneSetupPlaylists)}

                    // Loop over playlists and display them
                    items(reactiveState.playlistsArray.value.count()){ index ->
                        val playlist = reactiveState.playlistsArray.value[index]
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
                                    currentPlaylist = playlist
                                    navController.navigate(NavGraph.Playlist.route)
                                    onPlaylistPage = true
                                }
                                .padding(16.dp, 8.dp, 16.dp, 8.dp)
                                .clipToBounds(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!onPlaylistPage) {
                                ShowPlaylistIcon(playlist) // Playlist cover
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
                                        color = Color(white),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Options set up
                            val expanded = remember { mutableStateOf(false) }
                            IconButton(onClick = {
                                expanded.value = true
                                optionsPlaylist = playlist
                            }) {
                                Icon(
                                    Icons.Filled.MoreHoriz,
                                    contentDescription = "More options",
                                    tint = Color(white)
                                )
                                PlaylistOptions(expanded, openRenameDialog, playlist, scope, navController, reactiveState)
                            }
                        }

                    }

                    item { Spacer(modifier = Modifier.height(112.dp)) }

                }
            }

            ButtonWithIcon(
                onClick = {
                    openCreateDialog.value = true
                },
                Icons.Default.Add,
                reactiveState,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
            when {
                openCreateDialog.value -> {
                    CreatePlaylistAlert(
                        onDismissRequest = { openCreateDialog.value = false },
                        onConfirmation = {
                            openCreateDialog.value = false
                        },
                        ip = ip,
                        reactiveState
                    )
                }
            }
            when {
                openRenameDialog.value -> {
                    RenamePlaylistDialog(
                        onDismissRequest = {
                            openRenameDialog.value = false
                            optionsPlaylist = null
                                           },
                        onConfirmation = {
                            onPlaylistPage = true
                            openRenameDialog.value = false
                            currentPlaylist = optionsPlaylist
                            optionsPlaylist = null
                            MainScope().launch {
                                navController.navigate(NavGraph.Playlist.route)
                            }
                        },
                        api = api,
                        playlist = optionsPlaylist!!,
                        scope = scope,
                        ip = ip
                    )
                }
            }

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
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistAlert(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    ip: String,
    reactiveState: ReactiveState
) {
    var newPlaylistName by remember { mutableStateOf("") }

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

                        Text(text = "New Playlist", color = Color(white),
                            style = TextStyle(fontSize = 16.sp),
                            modifier = Modifier
                                .padding(8.dp)
                        )
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Playlist Name") },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Text
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }

            },
            onDismissRequest = {
                onDismissRequest()
            },
            confirmButton = {
                Text(
                    text = "Create",
                    color = Color(lightPurple),
                    style = TextStyle(fontSize = 16.sp),
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val client = HttpClient()
                                    val playlistResponse: String = client.post("${ip}/Playlists") {
                                        contentType(ContentType.Application.Json)
                                        body = JSONObject()
                                            .put("api_key", apiKey)
                                            .put("userId", userId)
                                            .put("Name", newPlaylistName)
                                            .toString()
                                        header(
                                            "X-Emby-Authorization",
                                            "MediaBrowser Client=\"Jellyfin Web\", Device=\"Firefox\", DeviceId=\"TW96aWxsYS81LjAgKFdpbmRvd3MgTlQgMTAuMDsgV2luNjQ7IHg2NDsgcnY6MTIwLjApIEdlY2tvLzIwMTAwMTAxIEZpcmVmb3gvMTIwLjB8MTcwMjIyODM5NjEwOQ==\", Version=\"10.8.12\", Token=\"31f138e0d91c4a6f8794c4f0c9c7087e\""
                                        )
                                    }
                                    val songsJson = JSONObject(playlistResponse)
                                    val Id = songsJson
                                        .get("Id")
                                        .toString()

                                    val newPlaylist = Playlist(
                                        Id,
                                        newPlaylistName,
                                        0,
                                    )
                                    playlistDao?.insertAll(newPlaylist)
                                    currentPlaylist = newPlaylist
                                    reactiveState.playlistsArray.value.add(0, newPlaylist)
                                } catch (e: Exception) {
                                    println("Playlists Page error creating playlist: ${e.message}")
                                }
                                onConfirmation()
                            }

                        }
                )
            },
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