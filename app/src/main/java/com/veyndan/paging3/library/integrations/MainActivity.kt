package com.veyndan.paging3.library.integrations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

class MainActivity : AppCompatActivity(R.layout.main) {

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

        val pagingAdapter = DummyAdapter()
        val recyclerView = requireViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.adapter = pagingAdapter

        lifecycleScope.launch {
            pager.flow.collectLatest { pagingData ->
                pagingAdapter.submitData(pagingData)
            }
        }
    }
}

class DummyAdapter : PagingDataAdapter<Dummy, DummyAdapter.DummyViewHolder>(DummyComparator) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DummyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return DummyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DummyViewHolder, position: Int) {
        val item = getItem(position)!!
        holder.textView.text = "$position · ${item.key}"
    }

    class DummyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView.requireViewById<TextView>(R.id.text)
    }
}

object DummyComparator : DiffUtil.ItemCallback<Dummy>() {

    override fun areItemsTheSame(oldItem: Dummy, newItem: Dummy): Boolean = oldItem.key == newItem.key

    override fun areContentsTheSame(oldItem: Dummy, newItem: Dummy): Boolean = oldItem == newItem
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
