package com.nomad.travel.data.expense

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {
    fun observeAll(): Flow<List<Expense>> = dao.observeAll()
    suspend fun add(expense: Expense): Long = dao.insert(expense)
    suspend fun totals(): List<CurrencyTotal> = dao.totals()
    suspend fun remove(id: Long) = dao.delete(id)
}
