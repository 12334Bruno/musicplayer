package com.example.musicplayer.Managers

import android.graphics.BitmapFactory
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import com.example.musicplayer.albumDao
import com.example.musicplayer.api
import com.example.musicplayer.apiKey
import com.example.musicplayer.currentAlbum
import com.example.musicplayer.playlistDao
import com.example.musicplayer.queue
import com.example.musicplayer.songLibraryId
import com.example.musicplayer.songPlaylistDao
import com.example.musicplayer.toByteArray
import com.example.musicplayer.userId
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemImageRequest
import java.io.Serializable
import java.util.UUID

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey val albumId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "artist") var artist: String?,
    @ColumnInfo(name = "artistId") var artistId: String?,
    @ColumnInfo(name = "length") var length: Int,
    @ColumnInfo(name = "imageUrl") var imageUrl: String? = null,
    @ColumnInfo(name = "imageData") var imageData: ByteArray? = null
) : Serializable {

    fun getBitMap(): ImageBitmap {
        return BitmapFactory.decodeByteArray(imageData, 0, imageData!!.size).asImageBitmap()
    }
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums")
    fun getAll(): List<Album>

    @Query("SELECT * FROM songs song WHERE song.albumId = :albumId")
    fun getSongsByAlbumId(albumId: String): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg albums: Album)

    @Query("DELETE FROM albums WHERE albumId = :albumId")
    fun deleteAlbumById(albumId: String)
}


/**
 * Set up albums
 *  - gets the albums from the server
 *  - synchronizes them to the device
 *  - updates the albums array
 */
var initiatedAlbums = false
suspend fun setUpAlbums(reactiveState: ReactiveState) {
    try {
        // Get Albums
        var albumsObj = ItemsApi(api!!).getItems(
            sortBy = listOf("SortName"),
            sortOrder = listOf(SortOrder.ASCENDING),
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            fields = listOf(
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.SORT_NAME,
                ItemFields.BASIC_SYNC_INFO
            ),
            imageTypeLimit = 1,
            enableImageTypes = listOf(
                ImageType.PRIMARY,
                ImageType.BACKDROP,
                ImageType.BANNER,
                ImageType.THUMB
            ),
            startIndex = 0,
            limit = 100,
            parentId = songLibraryId,
            userId = UUID.fromString(userId),
        )
        var imageApi = ImageApi(api!!)

        // Get Image Cover for every album
        var serverAlbums = mutableListOf<Album>()
        for (album in albumsObj.content.items!!) {
            val response = imageApi.getItemImage(
                request = GetItemImageRequest(
                    itemId = album.id,
                    imageType = ImageType.PRIMARY,
                    fillHeight = 1200,
                    fillWidth = 1200,
                    quality = 100
                )
            )
            var songs = albumDao!!.getSongsByAlbumId(album.id.toString().replace("-", ""))

            var newAlbum: Album = Album(
                albumId = album.id.toString().replace("-", ""),
                name = album.name!!,
                imageData = response.content.toByteArray(),
                length = songs.map { it.length }.sum(),
                artist = album.artistItems!!.get(0).name,
                artistId = album.artistItems!!.get(0).id.toString().replace("-", "")
            )
            serverAlbums.add(0, newAlbum)

        }

        // Delete albums that were deleted
        var myAlbums = albumDao!!.getAll()
        for (myAlbum in myAlbums) {
            if (serverAlbums.map { it.albumId }.indexOf(myAlbum.albumId) == -1) {
                albumDao!!.deleteAlbumById(myAlbum.albumId)

                var index =
                    reactiveState.albumsArray.value.map { it.albumId }.indexOf(myAlbum.albumId)
                if (index != -1) {
                    reactiveState.albumsArray.value.removeAt(index)
                }
            }
        }
        albumDao!!.insertAll(*serverAlbums.toTypedArray())
    } catch (e:Exception) {}
    reactiveState.albumsArray.value = albumDao?.getAll()!!.sortedBy{it.name}.toMutableList()
    initiatedAlbums = true
}

/**
 * Clickable options for the album row
 *  - add to queue
 *  - plqy next
 */
@Composable
fun AlbumOptions(
    expanded: MutableState<Boolean>,
    album: Album,
    scope: CoroutineScope,
) {

    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false }
    ) {
        DropdownMenuItem(onClick = {
            var songs: List<Song> = emptyList()
            scope.launch() {
                songs = albumDao!!.getSongsByAlbumId(album.albumId).sortedBy { it.name }
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
                songs = albumDao!!.getSongsByAlbumId(album.albumId).sortedBy { it.name }
                println("Song: ${album.name}: " + songs.size)
                if (queue != null) {
                    queue!!.pushToFront(songs)
                }
            }
            expanded.value = false
        }) {
            Text("Play next")
        }
    }
}