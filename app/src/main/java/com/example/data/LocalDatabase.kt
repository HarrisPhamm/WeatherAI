package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "favorite_cities")
data class FavoriteCity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = "",
    val adminArea: String? = ""
)

@Dao
interface FavoriteCityDao {
    @Query("SELECT * FROM favorite_cities ORDER BY id ASC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: FavoriteCity): Long

    @Query("DELETE FROM favorite_cities WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM favorite_cities")
    suspend fun count(): Int
}

@Database(entities = [FavoriteCity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteCityDao(): FavoriteCityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weather_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        try {
                            // Prepopulate default Vietnamese cities safely within the same helper transaction
                            db.execSQL("INSERT INTO favorite_cities (name, latitude, longitude, country, adminArea) VALUES ('Hà Nội', 21.0285, 105.8542, 'Việt Nam', 'Thủ đô Hà Nội')")
                            db.execSQL("INSERT INTO favorite_cities (name, latitude, longitude, country, adminArea) VALUES ('TP. Hồ Chí Minh', 10.7769, 106.7009, 'Việt Nam', 'Thành phố Hồ Chí Minh')")
                            db.execSQL("INSERT INTO favorite_cities (name, latitude, longitude, country, adminArea) VALUES ('Đà Nẵng', 16.0544, 108.2022, 'Việt Nam', 'Thành phố Đà Nẵng')")
                            db.execSQL("INSERT INTO favorite_cities (name, latitude, longitude, country, adminArea) VALUES ('Đà Lạt', 11.9404, 108.4583, 'Việt Nam', 'Tỉnh Lâm Đồng')")
                        } catch (e: Exception) {
                            // Safe fallback in case table structure undergoes dynamic changes in migrations or tests
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WeatherRepository(private val dao: FavoriteCityDao) {
    val favoritesFlow: Flow<List<FavoriteCity>> = dao.getAllFavoritesFlow()

    suspend fun insert(city: FavoriteCity) {
        dao.insert(city)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }
}
