package com.example.musicplayer

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.navigation.NavHostController


@SuppressLint("UnusedMaterialScaffoldPaddingParameter", "UnrememberedMutableState")
@Composable
fun SongScreen(navController: NavHostController, reactiveState: ReactiveState) {

    // Obtain the onBackPressedDispatcher

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Current Playback", color = Color(white)) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(white))
                    }
                },
                actions = {
                },
                backgroundColor = Color(purpleGrey),
            )
        },
        bottomBar = {
            BottomAppBar(
                backgroundColor = Color(darkGrey),
                modifier = Modifier
                    .padding(0.dp, 0.dp, 0.dp, 16.dp),
                content = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(darkGrey)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { // Repeat button clicked
                            reactiveState.repeat = !reactiveState.repeat
                        }) {
                            Icon(
                                Icons.Filled.Repeat,
                                contentDescription = "Repeat",
                                tint = if (!reactiveState.repeat) Color(white) else Color(lightPurple),
                                modifier = Modifier.size(if (reactiveState.repeat) 36.dp else 32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { // Back Skip Button clicked
                            println("Play previous song")
                            onPlayPreviousSong(reactiveState)
                        }) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Backward",
                                tint = Color(white),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { // onPlayPause clicked
                            reactiveState.playing = !reactiveState.playing
                            playSong(reactiveState.currentSong!!, false, mediaPlayer?.isPlaying == false, progress, reactiveState)
                        }) {
                            Icon(
                                imageVector = if (reactiveState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (reactiveState.playing) "Pause" else "Play",
                                tint = Color(white),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { // Skip Button clicked
                            onPlayNextSong(reactiveState)
                        }) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Forward",
                                tint = Color(white),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { // Shuffle button clicked
                            reactiveState.shuffle = !reactiveState.shuffle
                            if (reactiveState.shuffle) {
                                queue?.shuffle(true, reactiveState)
                            }
                            queue?.setQueue(reactiveState.currentSong!!, reactiveState.shuffle)
                            queue?.dequeue()
                        }) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (!reactiveState.shuffle) Color(white) else Color(lightPurple),
                                modifier = Modifier.size(if (reactiveState.shuffle) 36.dp else 32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                },
                elevation = 0.dp
            )


        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(darkGrey))
                .padding(16.dp)
        ) {
            // Add padding to the top of the Image
            if (reactiveState.currentSong.imageData != null) {
                Image(
                    bitmap = reactiveState.currentSong.getBitMap(),
                    contentDescription = "Album Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(top = 16.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.song_icon),
                    contentDescription = "Album Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(darkGrey))
                    .padding(0.dp, 0.dp, 0.dp, 64.dp)
                ,
                verticalArrangement = Arrangement.Bottom, // Align text to the bottom
                horizontalAlignment = Alignment.Start
            ) {
                Column {
                    Text(
                        modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 8.dp).width(350.dp),
                        text = reactiveState.currentSong.name ?: "Unknown Title",
                        color = Color(white),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = reactiveState.currentSong.artist ?: "Unknown Artist",
                        color = Color(white),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(350.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reactiveState.currentSong.albumName ?: reactiveState.currentSong.name ?: "Unknown Title",
                        color = Color(grey),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(350.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                }

                LinearProgressIndicator(
                    progress = reactiveState.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color(lightPurple),
                    backgroundColor = Color(grey),

                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(mediaPlayer?.currentPosition?.div(1000)), color = Color(white))
                    Text(formatTime(reactiveState.currentSong.length), color = Color(white))
                }

            }
        }

    }

    updateProgress(reactiveState)
}

fun formatTime(seconds: Int?): String {
    if (seconds == null || seconds < 0) {
        return "00:00"
    }

    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
