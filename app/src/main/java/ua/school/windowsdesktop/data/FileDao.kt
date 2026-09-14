package ua.school.windowsdesktop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface FileDao {
    @Query("SELECT * FROM files ORDER BY id") fun all(): List<FileEntity>
    @Query("DELETE FROM files") fun deleteAll()
    @Insert fun insertAll(files: List<FileEntity>)
    @Query("SELECT EXISTS(SELECT 1 FROM store_marker WHERE id = 1)") fun initialized(): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun markInitialized(marker: StoreMarker)
}
