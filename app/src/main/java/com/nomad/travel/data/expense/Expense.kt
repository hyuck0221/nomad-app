package com.nomad.travel.data.expense

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT currency, SUM(amount) AS total FROM expenses GROUP BY currency")
    suspend fun totals(): List<CurrencyTotal>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}

data class CurrencyTotal(
    val currency: String,
    val total: Double
)

@Database(entities = [Expense::class], version = 1, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var instance: ExpenseDatabase? = null
        fun get(context: Context): ExpenseDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ExpenseDatabase::class.java,
                "nomad-expenses.db"
            ).build().also { instance = it }
        }
    }
}
