package com.example.musicplayer

import android.util.Base64
import androidx.room.TypeConverter
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.example.musicplayer.Managers.Album
import com.example.musicplayer.Managers.AlbumDao
import com.example.musicplayer.Managers.Artist
import com.example.musicplayer.Managers.ArtistDao
import com.example.musicplayer.Managers.Playlist
import com.example.musicplayer.Managers.PlaylistDao
import com.example.musicplayer.Managers.Song
import com.example.musicplayer.Managers.SongDao
import com.example.musicplayer.Managers.SongPlaylistDao


// Many to many cross-reference entities
@Entity(primaryKeys = ["songId", "playlistId"])
data class SongPlaylist(
    val songId: String,
    val playlistId: String
)


// Database initialization
@Database(entities = [
    Song::class,
    Playlist::class,
    SongPlaylist::class,
    Artist::class,
    Album::class
                     ], version = 38)

@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun songPlaylistDao(): SongPlaylistDao

    abstract fun artistDao(): ArtistDao

    abstract fun albumDao(): AlbumDao
}


class Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { Base64.decode(it, Base64.DEFAULT) }
    }
}

