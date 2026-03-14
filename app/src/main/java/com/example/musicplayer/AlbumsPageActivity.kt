package com.example.musicplayer;

import android.annotation.SuppressLint;
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musicplayer.Managers.Album
import com.example.musicplayer.Managers.AlbumOptions
import com.example.musicplayer.Managers.initiatedAlbums
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


var currentAlbum: Album? = null
var doneSetupAlbums = false
var onAlbumPage = false

suspend fun ByteReadChannel.toByteArray(): ByteArray {
    return this.readRemaining(limit = Long.MAX_VALUE, 0).readBytes()
}


@SuppressLint("UnrememberedMutableState")
@Composable
fun AlbumsPage(navController: NavHostController, reactiveState: ReactiveState) {
    val interactionSource = remember { MutableInteractionSource() }
    val applicationContext = LocalContext.current
    val scope = CoroutineScope(Dispatchers.Default)
    val sharedPrefs = reactiveState.sharedPrefs


    LaunchedEffect(key1 = (reactiveState.albumsArray.value.size == 0 && !doneSetupAlbums)) {
        scope.launch {
            reactiveState.albumsArray.value = albumDao?.getAll()!!.sortedBy { it.name }.toMutableList()
            doneSetupAlbums = true
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

            navBar("Albums", navController, sharedPrefs, navBarHeight)

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
                    item { waitNotice(reactiveState.albumsArray.value, initiatedAlbums)}
                    items(reactiveState.albumsArray.value.count()){ index ->
                        var album = reactiveState.albumsArray.value[index]
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
                                    currentAlbum = album
                                    navController.navigate(NavGraph.Album.route)
                                    onAlbumPage = true
                                }
                                .padding(16.dp, 8.dp, 16.dp, 8.dp)
                                .clipToBounds(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (album.imageData == null) {
                                Image(
                                    painter = painterResource(id = R.drawable.song_icon),
                                    contentDescription = "Album Image",
                                    modifier = Modifier
                                        .size(48.dp)
                                )
                            } else {
                                Image(
                                    bitmap = album.getBitMap(),
                                    contentDescription = "Album Image",
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
                                    text = album.name,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        color = Color(white)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            var expanded = remember { mutableStateOf(false) }
                            IconButton(onClick = {
                                expanded.value = true
                            }) {
                                Icon(
                                    Icons.Filled.MoreHoriz,
                                    contentDescription = "More options",
                                    tint = Color(white)
                                )
                                AlbumOptions(expanded, album, scope)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(112.dp)) }

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
