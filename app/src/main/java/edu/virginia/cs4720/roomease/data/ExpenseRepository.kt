package edu.virginia.cs4720.roomease.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import edu.virginia.cs4720.roomease.data.model.Expense
import edu.virginia.cs4720.roomease.data.model.toExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Expenses store only the total (integer cents) and who shares it.
 * Each person's share is derived with [edu.virginia.cs4720.roomease.domain.ExpenseSplit],
 * so the stored data can't disagree with itself and rules don't need to add up a map.
 * Receipt photos never go to Firestore; only the confirmed merchant and total do.
 */
class ExpenseRepository(
    private val db: FirebaseFirestore,
    private val writeErrors: WriteErrorBus
) {
    private fun expenses(householdId: String) =
        db.collection("households").document(householdId).collection("expenses")

    fun observeExpenses(householdId: String): Flow<List<Expense>> =
        expenses(householdId).orderBy("createdAt", Query.Direction.DESCENDING).limit(200).snapshots()
            .map { snap -> snap.documents.map { it.toExpense() } }

    fun newExpenseId(householdId: String): String = expenses(householdId).document().id

    suspend fun alreadySaved(householdId: String, expenseId: String): Boolean =
        isInLocalCache(expenses(householdId).document(expenseId))

    fun addExpense(
        householdId: String,
        expenseId: String,
        description: String,
        merchant: String,
        totalCents: Long,
        paidBy: String,
        paidByName: String,
        participantIds: List<String>,
        hasReceipt: Boolean
    ) {
        val task = expenses(householdId).document(expenseId).set(
            mapOf(
                "description" to description.trim(),
                "merchant" to merchant.trim(),
                "totalCents" to totalCents,
                "paidBy" to paidBy,
                "paidByName" to paidByName,
                "participantIds" to participantIds.sorted(),
                "hasReceipt" to hasReceipt,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        writeErrors.track(task, "save the expense")
    }
}
