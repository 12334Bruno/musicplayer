package com.example.musicplayer.Managers
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.musicplayer.NavGraph
import com.example.musicplayer.R
import com.example.musicplayer.ReactiveState
import com.example.musicplayer.SongPlaylist
import com.example.musicplayer.apiKey
import com.example.musicplayer.boxSize
import com.example.musicplayer.darkGrey
import com.example.musicplayer.formatUUIDFromString
import com.example.musicplayer.ip
import com.example.musicplayer.lightPurple
import com.example.musicplayer.playlistCoverSize
import com.example.musicplayer.playlistDao
import com.example.musicplayer.playlistLibraryId
import com.example.musicplayer.queue
import com.example.musicplayer.songDao
import com.example.musicplayer.songLibraryId
import com.example.musicplayer.songPlaylistDao
import com.example.musicplayer.songsDirectory
import com.example.musicplayer.userId
import com.example.musicplayer.white
import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.ItemsApi
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.util.Locale
import java.util.UUID

var onPlaylistPage = false

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val playlistId: String,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "length") var length: Int = 0
)

// Many to many relationship set up
data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "songId",
        associateBy = Junction(SongPlaylist::class)
    )
    val songs: List<Song>
)


@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists")
    fun getAll(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg playlists: Playlist)

    @Delete
    fun delete(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    fun deleteByPlaylistId(playlistId: String)
}

@Dao
interface SongPlaylistDao {
    @Transaction
    @Query("SELECT * FROM songs WHERE songId = :songId")
    fun getPlaylistsForSong(songId: String): List<SongWithPlaylists>

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getSongsForPlaylist(playlistId: String): List<PlaylistWithSongs>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg songPlaylists: SongPlaylist)

    @Query("DELETE FROM songplaylist WHERE playlistId = :playlistId")
    fun deleteByPlaylistId(playlistId: String)

    @Query("DELETE FROM songplaylist WHERE songId = :songId")
    fun deleteSongById(vararg songId: String)
}


/**
 * Sets up playlists in the app
 * - synchronizes the playlists from the server to the device
 * - synchronizes the songs in the playlists
 */
suspend fun setUpPlaylists(reactiveState: ReactiveState) {
    val client = HttpClient()
    // Try to get Song from server
    try {
        // Get all playlists
        val playlistResponse: String = client.get("${ip}/Items") {
            parameter("api_key", apiKey)
            parameter("UserId", userId)
            parameter("ParentId", playlistLibraryId)

            parameter("StartIndex", 0)
            parameter("Limit", 100)
            parameter(
                "Fields",
                "PrimaryImageAspectRatio,SortName,Path,SongCount,ChildCount,MediaSourceCount,PrimaryImageAspectRatio"
            )
            parameter("ImageTypeLimit", 1)
            parameter("SortBy", "IsFolder,SortName")
            parameter("SortOrder", "Ascending")
        }


        // Format Songs
        val playlistJson = JSONObject(playlistResponse)
        val jsonArray = playlistJson.getJSONArray("Items")
        var serverPlaylists = List(jsonArray.length()) { index ->
            val playlistJson = jsonArray.getJSONObject(index)
            Playlist(
                playlistId = playlistJson.getString("Id"),
                name = playlistJson.getString("Name")
            )
        }

        // Get image cover for the song and update songs for each playlist
        serverPlaylists.forEach { playlist ->
            // Try to get Song from server
            try {
                // Get playlist songs
                val songsResponse: String = client.get("${ip}/Items") {
                    parameter("api_key", apiKey)
                    parameter("UserId", userId)
                    parameter("ParentId", playlist.playlistId)

                    parameter("SortBy", "Album,SortName")
                    parameter("SortOrder", "Ascending")
                    parameter("IncludeItemTypes", "Audio")
                    parameter("Recursive", "true")
                    parameter("Fields", "AudioInfo,ParentId")
                    parameter("StartIndex", "0")
                    parameter("ImageTypeLimit", "1")
                    parameter("EnableImageTypes", "Primary")
                    parameter("Limit", "100")
                }
                // Format Songs Obj
                val songsJson = JSONObject(songsResponse)
                val jsonArray = songsJson.getJSONArray("Items")
                var serverSongs = List(jsonArray.length()) { index ->
                    val songJson = jsonArray.getJSONObject(index)
                    var albumId = if (songJson.has("AlbumId")) {songJson.getString("AlbumId")} else {songJson.getString("Id")}
                    Song(
                        songId = songJson.getString("Id"),
                        name = songJson.getString("Name"),
                        artist = songJson.getJSONArray("Artists").getString(0),
                        albumName = songJson.getString("Album"),
                        length = (songJson.getString("RunTimeTicks").toLong() * 1/10000000).toInt(),
                        albumId = albumId,
                        artistId = songJson.getJSONArray("ArtistItems").getJSONObject(0).getString("Id")
                    )
                }

                var songPlaylists: MutableList<SongPlaylist> = mutableListOf<SongPlaylist>()
                // Get image cover for the song
                serverSongs.forEach { song ->
                    if (song.imageUrl == null || song.imageData == null) {
                        val imageUrl =
                            "${ip}/Items/${song.songId}/Images/Primary?api_key=$apiKey" +
                                    "&UserId=$userId&ParentId=$songLibraryId" +
                                    "&fillWidth=@@@&fillHeight=@@@&tag=f6ae3489521ee5bb6bc7adfbcba63be1&quality=100"
                        // Update the imageUrl in the Song object
                        song.imageUrl = imageUrl

                        var byteArray: ByteArray? = null
                        try {
                            byteArray = URL(song.makeUrl(1200)!!).readBytes()
                        } catch (e: Exception) {
                        }
                        song.imageData = byteArray

                    }
                    playlist.length += song.length

                    // Initiate song file path
                    val fileName = "${song.songId}_${sanitizeFileName(song.name!!)}.mp3"
                    val outputFilePath = File(songsDirectory, fileName).absolutePath
                    val songStreamUrl = "${ip}/Audio/${song.songId}/stream?api_key=$apiKey&UserId=$userId"
                    downloadFile(songStreamUrl, outputFilePath)
                    song.songFilePath = outputFilePath

                    songPlaylists.add(0, SongPlaylist(
                        song.songId,
                        playlist.playlistId
                    ))
                }

                // Delete artists that were deleted on the server
                var mySongs = songPlaylistDao!!.getSongsForPlaylist(playlist.playlistId)

                if (mySongs.size != 0) {
                    for (mySong in mySongs.get(0).songs) {
                        if (serverSongs.map { it.songId }.indexOf(mySong.songId) == -1) {
                            songPlaylistDao!!.deleteSongById(mySong.songId)
                        }
                    }
                }

                // Insert songPlaylist relationship to db
                songPlaylistDao?.insertAll(*songPlaylists.toTypedArray())
                songDao!!.insertAll(*serverSongs.toTypedArray())

            } catch (e: Exception) { // Something went wrong
                println("Error connecting to the server 1 (Playlist manager): ${e.message}")
            }

        }

        // Delete artists that were deleted on the server
        var myPlaylists = playlistDao!!.getAll()
        for (myPlaylist in myPlaylists) {

            if (serverPlaylists.map{it.playlistId}.indexOf(myPlaylist.playlistId) == -1) {
                playlistDao!!.deleteByPlaylistId(myPlaylist.playlistId)

                var index = reactiveState.playlistsArray.value.map{it.playlistId}.indexOf(myPlaylist.playlistId)
                if (index != -1) {
                    reactiveState.playlistsArray.value.removeAt(index)
                }
            }
        }

        playlistDao?.insertAll(*serverPlaylists.toTypedArray())

    } catch (e: Exception) { // Something went wrong
        println("Error connecting to the server 2 (Playlist set up): ${e}")
    }
    reactiveState.playlistsArray.value = playlistDao!!.getAll().sortedBy{it.name}.toMutableList()
}

/**
 * 2x2 Playlist Icon that is dynamically rendered for each playlist
 * - Gets the first 4 valid covers (or loops over the existing ones if there are less 4)
 * - Returns "no playlist icon" if no songs with valid icons are present
 */
@Composable
fun ShowPlaylistIcon(playlist: Playlist) {
    var songsForCover by rememberSaveable { mutableStateOf(listOf<Song>()) }

    val scope = rememberCoroutineScope()
    var bitmaps by remember { mutableStateOf(listOf<ImageBitmap>()) }

    LaunchedEffect(playlist.playlistId) {
        scope.launch(Dispatchers.IO) {
            songsForCover = getPlaylistSongs(playlist.playlistId)

            // If the playlist has songs
            if (songsForCover.isNotEmpty()) {
                val tempBitmaps = ArrayList<ImageBitmap>()
                var gotImages = 0
                var i = 0

                // Get the first 4 valid covers
                while (gotImages != 4) {
                    if (songsForCover.get(i % songsForCover.size).imageData != null) {
                        tempBitmaps.add(songsForCover.get(i % songsForCover.size).getBitMap())
                        gotImages += 1
                    }
                    i += 1
                }
                withContext(Dispatchers.Main) {
                    bitmaps = tempBitmaps
                }
            }
        }
    }

    // Change size depending on if the cover should be large (playlist activity)
    // or small (playlists page)
    var size = boxSize
    var modifier = Modifier.padding(0.dp)
    if (onPlaylistPage) {
        size = playlistCoverSize
        modifier = Modifier.fillMaxWidth()
    }

    // Render the correct icons
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size.dp)
                .aspectRatio(1f)
                .padding(if (onPlaylistPage) 16.dp else 0.dp)
        ) {
            if (songsForCover.size != 0 && bitmaps.size == 4) {
                if (onPlaylistPage) size -= 32
                Image(
                    bitmap = bitmaps.get(0),
                    contentDescription = "Song Image",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size((size / 2).dp)
                )
                Image(
                    bitmap = bitmaps.get(1),
                    contentDescription = "Song Image",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size((size / 2).dp)
                )
                Image(
                    bitmap = bitmaps.get(2),
                    contentDescription = "Song Image",
                    modifier = Modifier
                        .size((size / 2).dp)
                        .align(Alignment.BottomStart)
                )
                Image(
                    bitmap = bitmaps.get(3),
                    contentDescription = "Song Image",
                    modifier = Modifier
                        .size((size / 2).dp)
                        .align(Alignment.BottomEnd)
                )
            } else { // Default playlist cover
                Image(
                    painter = painterResource(id = R.drawable.playlist_icon),
                    contentDescription = "PlaylistIcon",
                    modifier = Modifier
                        .size(size.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Clickable playlist options
 *  - add to queue (whole playlist to end of queue)
 *  - play next (whole playlist as next)
 *  - rename playlist
 */
@Composable
fun PlaylistOptions(
    expanded: MutableState<Boolean>,
    openRenameDialog: MutableState<Boolean>,
    playlist: Playlist,
    scope: CoroutineScope,
    navController: NavHostController,
    reactiveState: ReactiveState
) {

    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false }
    ) {
        DropdownMenuItem(onClick = {
            var songs: List<Song> = emptyList()

            scope.launch() {
                songs = getPlaylistSongs(playlist!!.playlistId).sortedBy { it.name }
                if (queue != null) {
                    queue!!.enqueueSongs(songs)
                }
            }
            expanded.value = false

        }) {
            Text("Add to queue")
        }
        DropdownMenuItem(onClick = {

            var songs: List<Song> = emptyList()
            scope.launch() {
                songs = getPlaylistSongs(playlist!!.playlistId).sortedBy { it.name }
                if (queue != null) {
                    queue!!.pushToFront(songs)
                }
            }
            expanded.value = false
        }) {
            Text("Play next")
        }
        DropdownMenuItem(onClick = {
            expanded.value = false
            openRenameDialog.value = true
        }) {
            Text("Rename")
        }
        DropdownMenuItem(onClick = {
            scope.launch {
                // Delete playlist request
                val client = HttpClient()
                try {
                    client.delete<Unit>("${ip}/Items/${playlist.playlistId}") {
                        parameter("api_key", apiKey)
                        parameter("UserId", userId)
                    }
                    // Delete all associations in the db (songPlaylist relationships and playlist itself)
                    songPlaylistDao!!.deleteByPlaylistId(playlist.playlistId)
                    playlistDao!!.deleteByPlaylistId(playlist.playlistId)

                    // Update playlists array in the app
                    reactiveState.playlistsArray.value.removeAt(reactiveState.playlistsArray.value.map{it.playlistId}.indexOf(playlist.playlistId))
                    MainScope().launch {
                        navController.navigate(NavGraph.Playlists.route)
                    }
                    onPlaylistPage = false

                } catch(e: Exception) {println(e)}
            }
            expanded.value = false
        }) {
            Text("Delete")
        }
    }
}

/**
 * Rename playlist popup dialog with text input
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenamePlaylistDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    playlist: Playlist,
    scope: CoroutineScope,
    api: ApiClient?,
    ip: String,
) {
    var newPlaylistName by remember { mutableStateOf(playlist.name) }
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
                        Text(text = "Rename playlist", color = Color(white),
                            style = TextStyle(fontSize = 16.sp),
                            modifier = Modifier
                                .padding(8.dp)
                        )
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("New Playlist Name") },
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
            onDismissRequest = {},
            confirmButton = {
                Text(
                    text = "Rename",
                    color = Color(lightPurple),
                    style = TextStyle(fontSize = 16.sp),
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable {

                            scope.launch {
                                try {
                                    // Rename request
                                    var response = ItemsApi(api!!).getItems(
                                        userId = UUID.fromString(userId),
                                        ids = listOf(formatUUIDFromString(playlist.playlistId)!!)
                                    )
                                    // Get the server playlist (.get(0) since it surely the only one)
                                    var serverPlaylist = response.content.items!!.get(0)

                                    var serverJsonObject = JSONObject(Gson().toJson(serverPlaylist))

                                    // Payload
                                    val newJsonObject =
                                        createJsonForEdit(serverJsonObject, newPlaylistName)

                                    // Post request to change the naem
                                    var client = HttpClient()
                                    val playlistResponse: String =
                                        client.post("${ip}/Items/${playlist.playlistId}") {
                                            contentType(ContentType.Application.Json)
                                            body = newJsonObject
                                                .put("api_key", apiKey)
                                                .put("userId", userId)
                                                .toString()
                                            header(
                                                "X-Emby-Authorization",
                                                "MediaBrowser Client=\"Jellyfin Web\", Device=\"Firefox\", DeviceId=\"TW96aWxsYS81LjAgKFdpbmRvd3MgTlQgMTAuMDsgV2luNjQ7IHg2NDsgcnY6MTIwLjApIEdlY2tvLzIwMTAwMTAxIEZpcmVmb3gvMTIwLjB8MTcwMjIyODM5NjEwOQ==\", Version=\"10.8.12\", Token=\"31f138e0d91c4a6f8794c4f0c9c7087e\""
                                            )
                                        }
                                    // Set new name in the whole app
                                    playlist.name = newPlaylistName
                                    playlistDao!!.insertAll(playlist)
                                } catch (e: Exception) {
                                    println(e)
                                }
                            }

                            onConfirmation()
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

fun createJsonForEdit(serverJsonObject: JSONObject, newPlaylistName: String): JSONObject {
    val jsonStr = """{"Id":"","Name":"","OriginalTitle":"","ForcedSortName":"","CommunityRating":"","CriticRating":"","IndexNumber":null,"AirsBeforeSeasonNumber":"","AirsAfterSeasonNumber":"","AirsBeforeEpisodeNumber":"","ParentIndexNumber":null,"DisplayOrder":"","Album":"","AlbumArtists":[],"ArtistItems":[],"Overview":"","Status":"","AirDays":[],"AirTime":"","Genres":["Country","Pop","Soul"],"Tags":[],"Studios":[],"PremiereDate":null,"DateCreated":"2024-03-04T16:34:58.939Z","EndDate":null,"ProductionYear":"","AspectRatio":"","Video3DFormat":"","OfficialRating":"","CustomRating":"","People":[],"LockData":false,"LockedFields":[],"ProviderIds":{},"PreferredMetadataLanguage":"","PreferredMetadataCountryCode":"","Taglines":[]}"""
    val jsonObject = JSONObject(jsonStr)
    val newJsonObject = JSONObject()
    var value: Any? = null
    for (key in jsonObject.keys()) {
        if (serverJsonObject.has(key)) {
            value = serverJsonObject.get(key)
        } else if (serverJsonObject.has(key.replaceFirstChar { it.lowercase(Locale.ROOT) })) {
            value = serverJsonObject.get(key.replaceFirstChar { it.lowercase(Locale.ROOT) })
        } else {
            value = jsonObject.get(key)
        }
        newJsonObject.put(key, value)
    }
    newJsonObject.put("name", newPlaylistName)
    return newJsonObject
}


fun getPlaylistSongs(playlistId: String): List<Song> {
    var tempList = songPlaylistDao?.getSongsForPlaylist(playlistId)
    println(tempList)
    if (tempList!!.size != 0) {
        return tempList!!.get(0)?.songs!!
    }
    return listOf()
}
