package com.veyndan.paging3.library.integrations

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemsIndexed
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.delay

private class CustomRemoteMediator(private val database: AppDatabase) : RemoteMediator<Int, Dummy>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Dummy>): MediatorResult {
        return when (loadType) {
            LoadType.REFRESH -> {
                delay(1000)

                database.withTransaction {
                    database.itemDao().deleteAll()

                    val items = List(state.config.pageSize) { Dummy(key = it) }

                    database.itemDao().insertAll(items)
                }

                MediatorResult.Success(endOfPaginationReached = false)
            }
            LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                delay(1000)

                val items = state.pages.sumOf { it.data.size }
                    .let { it..(it + state.config.pageSize) }
                    .map { Dummy(key = it) }

                database.itemDao().insertAll(items)

                MediatorResult.Success(endOfPaginationReached = false)
            }
        }
    }
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pager = run {
            val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "database-name").build()

            Pager(
                PagingConfig(
                    pageSize = 30,
                    prefetchDistance = 1,
                    initialLoadSize = 30,
                    enablePlaceholders = false,
                ),
                remoteMediator = CustomRemoteMediator(database),
                pagingSourceFactory = {
                    database.itemDao().pagingSource()
                },
            )
        }

        val items = pager.flow.cachedIn(lifecycleScope)

        setContent {
            val lazyPagingItems = items.collectAsLazyPagingItems()

            Surface {
                LazyColumn {
                    itemsIndexed(lazyPagingItems) { index, item ->
                        Text("$index · ${item!!.key}", Modifier.padding(16.dp), fontSize = 16.sp)
                        Divider()
                    }
                }
            }
        }
    }
}

@Entity
data class Dummy(
    @PrimaryKey val key: Int,
)

@Dao
interface ItemDao {

    @Query("SELECT * FROM dummy")
    fun pagingSource(): PagingSource<Int, Dummy>

    @Insert
    suspend fun insertAll(number: List<Dummy>)

    @Query("DELETE FROM dummy")
    suspend fun deleteAll()
}

@Database(entities = [Dummy::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
