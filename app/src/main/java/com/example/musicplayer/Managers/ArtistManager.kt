package com.example.musicplayer.Managers

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.musicplayer.ReactiveState
import com.example.musicplayer.albumDao
import com.example.musicplayer.api
import com.example.musicplayer.artistDao
import com.example.musicplayer.playlistDao
import com.example.musicplayer.songLibraryId
import com.example.musicplayer.toByteArray
import org.jellyfin.sdk.api.operations.ArtistsApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetItemImageRequest
import java.io.Serializable


@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey val artistId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "imageData") val imageData: ByteArray?,
): Serializable {

    fun getBitMap(): ImageBitmap {
        return BitmapFactory.decodeByteArray(imageData, 0, imageData!!.size).asImageBitmap()
    }
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists")
    fun getAll(): List<Artist>

    @Transaction
    @Query("SELECT * FROM albums album WHERE album.artistId = :artistId")
    fun getAllAlbums(artistId: String): List<Album> // Album has to have artistId

    @Transaction
    @Query("SELECT * FROM songs song WHERE song.artistId = :artistId AND song.albumId = song.songId")
    fun getAllSongs(artistId: String): List<Song> // Song has to have artistId and song.albumId = song.songId

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg artists: Artist)

    @Query("DELETE FROM artists WHERE artistId = :artistId")
    fun deleteArtistById(artistId: String)
}

/**
 * Sets up artists array
 * - synchronizes artists from the server to the device
 */
var initatedArtists = false
suspend fun setUpArtits(reactiveState: ReactiveState) {
    try {
        // Get artists
        var artistsObj = ArtistsApi(api!!).getAlbumArtists(parentId= songLibraryId)
        var imageApi = ImageApi(api!!)

        var serverArtists = mutableListOf<Artist>()
        for (artist in artistsObj.content.items!!) {
            var response: ByteArray? = null
            try {
                // Get Image Cover for every artist
                response = imageApi.getItemImage(
                    request = GetItemImageRequest(
                        itemId = artist.id,
                        imageType = ImageType.PRIMARY,
                        fillHeight = 1200,
                        fillWidth = 1200,
                        quality = 100
                    )
                ).content.toByteArray()
            } catch (e: Exception) {}
            // Create artist objects
            var artist = Artist(
                artistId = artist.id.toString().replace("-", ""),
                name = artist.name!!,
                imageData = response
            )
            // Hold artists in temp to add them all together later into db
            serverArtists.add(0, artist)
        }

        // Delete artists that were deleted on the server
        var myArtists = artistDao!!.getAll()
        for (myArtist in myArtists) {
            if (serverArtists.map{it.artistId}.indexOf(myArtist.artistId) == -1) {
                artistDao!!.deleteArtistById(myArtist.artistId)

                var index = reactiveState.artistsArray.value.map{it.artistId}.indexOf(myArtist.artistId)
                if (index != -1) {
                    reactiveState.artistsArray.value.removeAt(index)
                }
            }
        }

        // Insert artists into db
        artistDao!!.insertAll(*serverArtists.toTypedArray())
    } catch (e: Exception) {println("Error initiating artists: ${e.localizedMessage}")}

    reactiveState.artistsArray.value = artistDao!!.getAll().sortedBy{it.name}.toMutableList()
    initatedArtists = true
}
