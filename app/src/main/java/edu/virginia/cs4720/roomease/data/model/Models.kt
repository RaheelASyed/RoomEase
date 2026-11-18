package edu.virginia.cs4720.roomease.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior

// Immutable data classes for every Firestore document (Lectures 2 + 12).
// We map snapshots by hand so the exact field names the security rules check
// live in one place, and so we can attach "pending sync" metadata.

/** users/{uid} — private to its owner. householdId is navigation state only; membership is checked separately. */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val householdId: String? = null,
    val pendingHouseholdId: String? = null
)

/** households/{householdId} */
data class Household(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val inviteCode: String = ""
)

/** households/{householdId}/members/{uid} — the authority for membership. */
data class Member(
    val uid: String = "",
    val displayName: String = "",
    val role: String = ROLE_MEMBER
) {
    val isOwner: Boolean get() = role == ROLE_OWNER

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
    }
}

/** inviteCodes/{code} — lets a joiner find which household to ask. Knowing it grants nothing by itself. */
data class InviteInfo(
    val code: String = "",
    val householdId: String = "",
    val householdName: String = ""
)

/** households/{householdId}/joinRequests/{uid} — the owner approves this to authorize one specific account. */
data class JoinRequest(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_DECLINED = "declined"
    }
}

/** households/{householdId}/chores/{choreId} */
data class Chore(
    val id: String = "",
    val title: String = "",
    val assigneeId: String = "",
    val assigneeName: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val dueDate: Timestamp? = null,
    val done: Boolean = false,
    val completedAt: Timestamp? = null,
    val pendingSync: Boolean = false
)

/** households/{householdId}/expenses/{expenseId}. Money is always integer cents. */
data class Expense(
    val id: String = "",
    val description: String = "",
    val merchant: String = "",
    val totalCents: Long = 0,
    val paidBy: String = "",
    val paidByName: String = "",
    val participantIds: List<String> = emptyList(),
    val hasReceipt: Boolean = false,
    val createdAt: Timestamp? = null,
    val pendingSync: Boolean = false
)

/** households/{householdId}/messages/{messageId} */
data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val createdAt: Timestamp? = null,
    val pendingSync: Boolean = false
)

// ---- Snapshot -> model mappers ----

private val ESTIMATE = ServerTimestampBehavior.ESTIMATE

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = id,
    displayName = getString("displayName").orEmpty(),
    email = getString("email").orEmpty(),
    householdId = getString("householdId"),
    pendingHouseholdId = getString("pendingHouseholdId")
)

fun DocumentSnapshot.toHousehold(): Household = Household(
    id = id,
    name = getString("name").orEmpty(),
    ownerId = getString("ownerId").orEmpty(),
    inviteCode = getString("inviteCode").orEmpty()
)

fun DocumentSnapshot.toMember(): Member = Member(
    uid = id,
    displayName = getString("displayName").orEmpty(),
    role = getString("role") ?: Member.ROLE_MEMBER
)

fun DocumentSnapshot.toJoinRequest(): JoinRequest = JoinRequest(
    uid = id,
    displayName = getString("displayName").orEmpty(),
    email = getString("email").orEmpty(),
    status = getString("status") ?: JoinRequest.STATUS_PENDING
)

fun DocumentSnapshot.toChore(): Chore = Chore(
    id = id,
    title = getString("title").orEmpty(),
    assigneeId = getString("assigneeId").orEmpty(),
    assigneeName = getString("assigneeName").orEmpty(),
    createdBy = getString("createdBy").orEmpty(),
    createdByName = getString("createdByName").orEmpty(),
    dueDate = getTimestamp("dueDate"),
    done = getBoolean("done") ?: false,
    completedAt = getTimestamp("completedAt", ESTIMATE),
    pendingSync = metadata.hasPendingWrites()
)

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toExpense(): Expense = Expense(
    id = id,
    description = getString("description").orEmpty(),
    merchant = getString("merchant").orEmpty(),
    totalCents = getLong("totalCents") ?: 0L,
    paidBy = getString("paidBy").orEmpty(),
    paidByName = getString("paidByName").orEmpty(),
    participantIds = (get("participantIds") as? List<String>).orEmpty(),
    hasReceipt = getBoolean("hasReceipt") ?: false,
    createdAt = getTimestamp("createdAt", ESTIMATE),
    pendingSync = metadata.hasPendingWrites()
)

fun DocumentSnapshot.toMessage(): Message = Message(
    id = id,
    text = getString("text").orEmpty(),
    senderId = getString("senderId").orEmpty(),
    senderName = getString("senderName").orEmpty(),
    createdAt = getTimestamp("createdAt", ESTIMATE),
    pendingSync = metadata.hasPendingWrites()
)
