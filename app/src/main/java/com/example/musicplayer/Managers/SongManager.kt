package com.example.musicplayer.Managers

import android.graphics.BitmapFactory
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.navigation.NavHostController
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import com.example.musicplayer.NavGraph
import com.example.musicplayer.ReactiveState
import com.example.musicplayer.SongPlaylist
import com.example.musicplayer.api
import com.example.musicplayer.apiKey
import com.example.musicplayer.clickCounter
import com.example.musicplayer.currentPlaylist
import com.example.musicplayer.doneSetUpSongs
import com.example.musicplayer.formatUUIDFromString
import com.example.musicplayer.ip
import com.example.musicplayer.originalSongsList
import com.example.musicplayer.playSong
import com.example.musicplayer.progress
import com.example.musicplayer.queue
import com.example.musicplayer.songDao
import com.example.musicplayer.songLibraryId
import com.example.musicplayer.songPlaylistDao
import com.example.musicplayer.songsDirectory
import com.example.musicplayer.songsToPlay
import com.example.musicplayer.userId
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.jellyfin.sdk.api.operations.PlaylistsApi
import org.json.JSONObject
import java.io.File
import java.io.Serializable
import java.net.URL

// Entity table (Song data) for the SQLite database
@Entity (tableName = "songs")
data class Song(
    @PrimaryKey val songId: String,
    @ColumnInfo(name = "albumId") val albumId: String?,
    @ColumnInfo(name = "name") var name: String?,
    @ColumnInfo(name = "artist") var artist: String?,
    @ColumnInfo(name = "artistId") var artistId: String?,
    @ColumnInfo(name = "length") var length: Int = 0,
    @ColumnInfo(name = "albumName") var albumName: String? = null,
    @ColumnInfo(name = "songFilePath") var songFilePath: String? = null,
    @ColumnInfo(name = "imageUrl") var imageUrl: String? = null,
    @ColumnInfo(name = "imageData") var imageData: ByteArray? = null
) : Serializable {

    // Create a parsable url string for the image
    fun makeUrl(num: Int): String? {
        return imageUrl?.replace("@@@", num.toString())
    }

    // Use the ByteArray (from imageData) and parse it to an image bitmap
    fun getBitMap(): ImageBitmap {
        return BitmapFactory.decodeByteArray(imageData, 0, imageData!!.size).asImageBitmap()
    }
}

// Many to many relationship for song-playlist
data class SongWithPlaylists(
    @Embedded val song: Song,
    @Relation(
        parentColumn = "songId",
        entityColumn = "playlistId",
        associateBy = Junction(SongPlaylist::class) // Create the junction for the entity tables
    )
    val playlists: List<Playlist>
)


// Data access object for songs
@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun getAll(): List<Song>

    @Query("SELECT * FROM songs WHERE songId IN (:songIds)")
    fun loadAllByIds(songIds: IntArray): List<Song>

    @Query("SELECT * FROM songs WHERE name LIKE :name LIMIT 1")
    fun findByName(name: String): Song

    @Query("SELECT * FROM songs WHERE name LIKE :query")
    fun searchSongs(query: String): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg songs: Song)

    @Query("DELETE FROM songs WHERE songId = :songId")
    fun deleteSongById(songId: String)
}

/*
Song queue, which manages the queue for playing songs
 */
class SongQueue() {
    var queue: MutableList<Song> = mutableListOf(Song("as", "Default", "029302", null, null, 0))

    // Sets the queue based on the shuffle value
    // Allows to dynamically craete a list of songs to play based on the desired song to play
    fun setQueue(song: Song, shuffle: Boolean) {
        if (songsToPlay == emptyList<Song>() || !shuffle) {
            songsToPlay = originalSongsList.sortedBy { it.name }.toMutableList()
        }

        // I the song that was to set queue isn't in list of songs to play, start over in the list
        var currentIndex = songsToPlay.map{it.songId}.indexOf(song.songId)
        if (currentIndex == -1) {
            currentIndex = 0
        }
        // Set the queue on a portion of the songs we have
        queue = songsToPlay.subList(currentIndex, songsToPlay.size).toMutableList()
    }

    // Returns the first element in the queue and removes it
    fun dequeue(): Song {
        return queue.removeAt(0)
    }

    // Gets the previous song
    fun getPreviousSong(reactiveState: ReactiveState): Song {
        var currentIndex = songsToPlay.map{it.songId}.indexOf(reactiveState.currentSong.songId)

        // If the previous song was a song that was at the end of the queue and the
        // queue reset, then loop back over to the end of the songs list and set it back
        if (currentIndex == 0) {
            setQueue(songsToPlay[songsToPlay.size - 1], reactiveState.shuffle)
        } else { // Else just go back in the array of played songs
            setQueue(songsToPlay[currentIndex - 1], reactiveState.shuffle)
        }
        return dequeue()
    }

    // Either shuffle completely, playing a random song (play random song button)
    fun shuffle(stayFirstInShuffle: Boolean, reactiveState: ReactiveState) {

        songsToPlay = originalSongsList.shuffled().toMutableList()

        // Keep the song we have in front (shuffle on button)
        if (stayFirstInShuffle) {
            val currentSongIndex = songsToPlay.map{it.songId}.indexOf(reactiveState.currentSong.songId)
            songsToPlay.removeAt(currentSongIndex)
        }
        queue = songsToPlay
    }

    // Enqueue all songs
    fun enqueueSongs(songs: List<Song>) {
        for (song in songs) {
            queue.add(queue.size, song)
        }
    }

    // Push songs to front in their original order
    fun pushToFront(songs: List<Song>) {
        var index = 0
        for (song in songs) {
            queue.add(index, song)
            index += 1
        }
    }
    fun getSize(): Int {return queue.size}
    fun getQueueList(): MutableList<Song>{return queue}
    fun front(): Song {return queue[0]}
    fun rear(): Song {return queue[getSize()]}
}


/**
 * Song options for each song row
 */
@Composable
fun SongOptions(
    song: Song,
    addToPlaylistDialog: MutableState<Boolean>,
    onClickedSongChange: (Song) -> Unit,
    delete: Boolean,
    navController: NavHostController? = null,
    ip: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = CoroutineScope(Dispatchers.Default)

    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Filled.MoreHoriz,
            contentDescription = "More options",
            tint = Color.White
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(onClick = {
                expanded = false
                queue!!.enqueueSongs(listOf(song))
            }) {
                Text("Add to queue")
            }
            DropdownMenuItem(onClick = {
                queue!!.pushToFront(listOf(song))
                expanded = false
            }) {
                Text("Play next")
            }
            if (!onPlaylistPage) {
                DropdownMenuItem(onClick = {
                    onClickedSongChange(song)
                    expanded = false
                    addToPlaylistDialog.value = true
                }) {
                    Text("Add to playlist")
                }
            }
            if (delete) {
                DropdownMenuItem(onClick = {
                    scope.launch {
                        val client = HttpClient()
                        var playlistId = currentPlaylist!!.playlistId
                        try {

                            // Using PlaylistsApi.removeItem doesn't seem to work so we firstly delete the playlist
                            client.delete<Unit>("${ip}/Items/${playlistId}") {
                                parameter("api_key", apiKey)
                                parameter("UserId", userId)
                            }

                            // delete the song-playlist connection in the db
                            songPlaylistDao!!.deleteSongById(song.songId)

                            // Create the playlist again
                            val playlistResponse: String = client.post("${ip}/Playlists") {
                                contentType(ContentType.Application.Json)
                                body = JSONObject()
                                    .put("api_key", apiKey)
                                    .put("userId", userId)
                                    .put("Name", currentPlaylist!!.name)
                                    .toString()
                                header("X-Emby-Authorization",
                                    "MediaBrowser Client=\"Jellyfin Web\", Device=\"Firefox\", DeviceId=\"TW96aWxsYS81LjAgKFdpbmRvd3MgTlQgMTAuMDsgV2luNjQ7IHg2NDsgcnY6MTIwLjApIEdlY2tvLzIwMTAwMTAxIEZpcmVmb3gvMTIwLjB8MTcwMjIyODM5NjEwOQ==\", Version=\"10.8.12\", Token=\"31f138e0d91c4a6f8794c4f0c9c7087e\"")
                            }

                            // Get the songs that are in the playlist
                            val songs = getPlaylistSongs(currentPlaylist?.playlistId!!).sortedBy { it.name }
                            for (song in songs) { // for each song add it to the playlist on the server
                                val playlistsApi = PlaylistsApi(api!!)
                                playlistsApi.addToPlaylist( // request
                                    formatUUIDFromString(playlistId)!!,
                                    listOf(formatUUIDFromString(song.songId)!!)
                                )

                            }
                            // After we are finished navigate back to playlists page
                            MainScope().launch {
                                navController!!.navigate(NavGraph.Playlists.route)
                            }

                            onPlaylistPage = false

                        } catch(e: Exception) {println(e)}
                    }
                    navController!!.navigate(NavGraph.Playlists.route)
                    onPlaylistPage = false
                    expanded = false
                }) {
                    Text("Remove from playlist")
                }
            }
        }

    }
}

/**
 * Set up songs across app and db
 * - synchronize the songs to the server
 */
suspend fun setUpSongs(reactiveState: ReactiveState) {
    val client = HttpClient()
    // Try to get Song from server
    try {
        val songsResponse: String = client.get("${ip}/Items") {
            parameter("api_key", apiKey)
            parameter("UserId", userId)
            parameter("ParentId", songLibraryId.toString())

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

        // Format Songs
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
        // Get image cover for the song
        serverSongs.forEach { song ->
            // Initiate song file path
            val fileName = "${song.songId}_${sanitizeFileName(song.name!!)}.mp3"
            val outputFilePath = File(songsDirectory, fileName).absolutePath
            song.songFilePath = outputFilePath

            // Get image url path
            val imageUrl =
                "${ip}/Items/${song.songId}/Images/Primary?api_key=$apiKey" +
                        "&UserId=$userId&ParentId=$songLibraryId" +
                        "&fillWidth=@@@&fillHeight=@@@&tag=f6ae3489521ee5bb6bc7adfbcba63be1&quality=100"
            // Update the imageUrl in the Song object
            song.imageUrl = imageUrl
            var byteArray: ByteArray? = null
            try {
                byteArray = URL(song.makeUrl(1200)!!).readBytes()
            } catch (e: Exception) {}
            song.imageData = byteArray

        }
        // Delete artists that were deleted on the server
        var mySongs = songDao!!.getAll()
        for (mySong in mySongs) {
            if (serverSongs.map{it.songId}.indexOf(mySong.songId) == -1) {
                songDao!!.deleteSongById(mySong.songId)

                var index = reactiveState.songsArray.value.map{it.songId}.indexOf(mySong.songId)
                if (index != -1) {
                    reactiveState.songsArray.value.removeAt(index)
                }
            }
        }
        // Insert all songs to the db and update songs array for the app
        songDao!!.insertAll(*serverSongs.toTypedArray())
        var tempSongs = songDao!!.getAll().toMutableList()
        originalSongsList = tempSongs

        // Download all songs/Check whether they are already downloaded
        for (song in mySongs) {
            // Initiate song file path
            val fileName = "${song.songId}_${sanitizeFileName(song.name!!)}.mp3"
            val outputFilePath = File(songsDirectory, fileName).absolutePath
            val songStreamUrl = "${ip}/Audio/${song.songId}/stream?api_key=$apiKey&UserId=$userId"

            CoroutineScope(Dispatchers.IO).launch {
                downloadFile(songStreamUrl, outputFilePath)
            }
        }


    } catch (e: Exception) { // Something went wrong
        println("Error when initiating songs (Song Mangager): ${e}")
    }
    reactiveState.songsArray.value = songDao!!.getAll().sortedBy{it.name}.toMutableList()
    doneSetUpSongs = true
}

/**
 * Function that sets up the queue and playing of a song
 */
fun initiateSong (
    reactiveState: ReactiveState,
    song: Song,
    stayFirstInShuffle: Boolean = true
    ) {
    clickCounter += 1 // pass through after first time for playing bar
    progress = 0f
    reactiveState.barOn = true // Refresh the playing bar
    reactiveState.playing = true
    reactiveState.currentSong = song

    // If the shuffle funcitonality has been turned on
    if (reactiveState.shuffle) {
        queue?.shuffle(stayFirstInShuffle, reactiveState)

        // If the first song should be completely random
        if (!stayFirstInShuffle) {
            reactiveState.currentSong = queue?.front()!!
        }
        queue?.pushToFront(listOf(reactiveState.currentSong))
    }
    // Set queue based on the song to be played
    queue?.setQueue(reactiveState.currentSong, reactiveState.shuffle)
    if (queue!!.getSize() == 0) {
        println("NO SONGS IN QUEUE")
    }
    playSong(queue!!.dequeue(), true, true, progress, reactiveState)

}

/**
 * Downlaod th song and save it to the app-accessible folder
 */
suspend fun downloadFile(url: String, outputFilePath: String) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(outputFilePath)

            // Check if the file already exists
            if (!file.exists()) {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val sink = file.sink().buffer()
                    response.body?.source()?.let { sink.writeAll(it) }
                    sink.close()
                } else {
                    println("Download failed: ${response.message}")
                }
            }
        } catch (e: Exception) {
            println("Error downloading file: $e")
        }
    }
}

fun searchSongs(query: String): List<Song> {
    return songDao!!.searchSongs(query)
}

fun sanitizeFileName(fileName: String): String {
    return fileName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
}

