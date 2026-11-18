package edu.virginia.cs4720.roomease.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.data.model.toChore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lecture 9 Repository pattern: the ViewModel can't tell cache from cloud. */
class ChoreRepository(
    private val db: FirebaseFirestore,
    private val writeErrors: WriteErrorBus
) {
    private fun chores(householdId: String) =
        db.collection("households").document(householdId).collection("chores")

    /** Live list for the whole household, soonest due first (single-field index, no composite needed). */
    fun observeChores(householdId: String): Flow<List<Chore>> =
        chores(householdId).orderBy("dueDate", Query.Direction.ASCENDING).snapshots()
            .map { snap -> snap.documents.map { it.toChore() } }

    /** A fresh document id. The add-chore screen keeps it in saved state so a retry or rotation reuses it. */
    fun newChoreId(householdId: String): String = chores(householdId).document().id

    suspend fun alreadySaved(householdId: String, choreId: String): Boolean =
        isInLocalCache(chores(householdId).document(choreId))

    fun createChore(
        householdId: String,
        choreId: String,
        title: String,
        assigneeId: String,
        assigneeName: String,
        createdBy: String,
        createdByName: String,
        dueDate: Timestamp
    ) {
        val task = chores(householdId).document(choreId).set(
            mapOf(
                "title" to title.trim(),
                "assigneeId" to assigneeId,
                "assigneeName" to assigneeName,
                "createdBy" to createdBy,
                "createdByName" to createdByName,
                "dueDate" to dueDate,
                "done" to false,
                "completedAt" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        writeErrors.track(task, "save the chore")
    }

    /** Rules only let the assignee change done/completedAt. */
    fun setDone(householdId: String, choreId: String, done: Boolean) {
        val task = chores(householdId).document(choreId).update(
            mapOf(
                "done" to done,
                "completedAt" to if (done) FieldValue.serverTimestamp() else null
            )
        )
        writeErrors.track(task, "update the chore")
    }
}
