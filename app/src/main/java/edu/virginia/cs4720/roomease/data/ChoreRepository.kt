package edu.virginia.cs4720.roomease.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.data.model.toChore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** The editable parts of a chore (create and edit share this). */
data class ChoreDetails(
    val title: String,
    val assigneeId: String,
    val assigneeName: String,
    val dueDate: Timestamp,
    val repeat: String,
    val rotate: Boolean
)

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

    /** One chore, for the edit screen (cache first, so it also opens offline). */
    suspend fun getChore(householdId: String, choreId: String): Chore? {
        val snap = chores(householdId).document(choreId).get().await()
        return if (snap.exists()) snap.toChore() else null
    }

    /** A fresh document id. The add-chore screen keeps it in saved state so a retry or rotation reuses it. */
    fun newChoreId(householdId: String): String = chores(householdId).document().id

    suspend fun alreadySaved(householdId: String, choreId: String): Boolean =
        isInLocalCache(chores(householdId).document(choreId))

    private fun newChoreFields(details: ChoreDetails, createdBy: String, createdByName: String) = mapOf(
        "title" to details.title.trim(),
        "assigneeId" to details.assigneeId,
        "assigneeName" to details.assigneeName,
        "createdBy" to createdBy,
        "createdByName" to createdByName,
        "dueDate" to details.dueDate,
        "repeat" to details.repeat,
        "rotate" to details.rotate,
        "done" to false,
        "completedAt" to null,
        "createdAt" to FieldValue.serverTimestamp()
    )

    fun createChore(householdId: String, choreId: String, details: ChoreDetails, createdBy: String, createdByName: String) {
        val task = chores(householdId).document(choreId).set(newChoreFields(details, createdBy, createdByName))
        writeErrors.track(task, "save the chore")
    }

    /** Any member may edit the details; completion state and the creator are untouched (rules enforce it). */
    fun updateChore(householdId: String, choreId: String, details: ChoreDetails) {
        val task = chores(householdId).document(choreId).update(
            mapOf(
                "title" to details.title.trim(),
                "assigneeId" to details.assigneeId,
                "assigneeName" to details.assigneeName,
                "dueDate" to details.dueDate,
                "repeat" to details.repeat,
                "rotate" to details.rotate
            )
        )
        writeErrors.track(task, "update the chore")
    }

    /** Rules allow only the chore's creator or the household owner. */
    fun deleteChore(householdId: String, choreId: String) {
        writeErrors.track(chores(householdId).document(choreId).delete(), "delete the chore")
    }

    /** One-off chores: only the assignee can tick / untick it (rules enforce this). */
    fun setDone(householdId: String, choreId: String, done: Boolean) {
        val task = chores(householdId).document(choreId).update(
            mapOf(
                "done" to done,
                "completedAt" to if (done) FieldValue.serverTimestamp() else null
            )
        )
        writeErrors.track(task, "update the chore")
    }

    /**
     * Repeating chores don't get "done": completing one moves the SAME chore on to its next
     * due date (and the next roommate if it rotates), recording who completed it and when.
     */
    fun advanceRepeating(
        householdId: String,
        choreId: String,
        nextAssigneeId: String,
        nextAssigneeName: String,
        nextDue: Timestamp,
        completedBy: String
    ) {
        val task = chores(householdId).document(choreId).update(
            mapOf(
                "assigneeId" to nextAssigneeId,
                "assigneeName" to nextAssigneeName,
                "dueDate" to nextDue,
                "lastCompletedBy" to completedBy,
                "lastCompletedAt" to FieldValue.serverTimestamp()
            )
        )
        writeErrors.track(task, "complete the chore")
    }

    /** Undo for [advanceRepeating]: put back exactly what was there before. */
    fun undoAdvance(householdId: String, before: Chore) {
        val task = chores(householdId).document(before.id).update(
            mapOf(
                "assigneeId" to before.assigneeId,
                "assigneeName" to before.assigneeName,
                "dueDate" to before.dueDate,
                "lastCompletedBy" to before.lastCompletedBy,
                "lastCompletedAt" to before.lastCompletedAt
            )
        )
        writeErrors.track(task, "undo")
    }
}
