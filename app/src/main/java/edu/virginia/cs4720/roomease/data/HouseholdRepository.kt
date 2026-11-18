package edu.virginia.cs4720.roomease.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import edu.virginia.cs4720.roomease.data.model.Household
import edu.virginia.cs4720.roomease.data.model.InviteInfo
import edu.virginia.cs4720.roomease.data.model.JoinRequest
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.data.model.UserProfile
import edu.virginia.cs4720.roomease.data.model.toHousehold
import edu.virginia.cs4720.roomease.data.model.toJoinRequest
import edu.virginia.cs4720.roomease.data.model.toMember
import edu.virginia.cs4720.roomease.data.model.toUserProfile
import edu.virginia.cs4720.roomease.domain.InviteCodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Households, membership and the owner-approved invite flow.
 *
 * Join flow (all client writes; every step is checked by firestore.rules):
 * 1. Owner creates the household. One atomic batch writes the household, the owner's member doc,
 *    the inviteCodes/{code} lookup doc and users/{uid}.householdId.
 * 2. Joiner types the code -> reads inviteCodes/{code} -> creates joinRequests/{their uid}.
 * 3. Owner approves that request (status = "approved"). The request's document id IS the
 *    joiner's uid, so the approval is tied to one account.
 * 4. Joiner's app sees the approval and writes members/{uid} + users/{uid}.householdId in one batch.
 *    Rules only allow that member doc if joinRequests/{uid} is approved, so guessing a household id
 *    (or a code) never grants membership on its own.
 */
class HouseholdRepository(private val db: FirebaseFirestore) {

    private fun users() = db.collection("users")
    private fun households() = db.collection("households")
    private fun household(id: String) = households().document(id)

    fun observeUser(uid: String): Flow<UserProfile?> =
        users().document(uid).snapshots().map { if (it.exists()) it.toUserProfile() else null }

    /** True only when households/{hid}/members/{uid} exists: the real membership check. */
    fun observeMembership(householdId: String, uid: String): Flow<Boolean> =
        household(householdId).collection("members").document(uid).snapshots().map { it.exists() }

    fun observeHousehold(householdId: String): Flow<Household?> =
        household(householdId).snapshots().map { if (it.exists()) it.toHousehold() else null }

    fun observeMembers(householdId: String): Flow<List<Member>> =
        household(householdId).collection("members").snapshots()
            .map { snap -> snap.documents.map { it.toMember() }.sortedBy { it.displayName.lowercase() } }

    /** Creates a household and makes [user] its owner. Retries if the random invite code is taken. */
    suspend fun createHousehold(user: UserProfile, name: String): String {
        var lastError: Exception? = null
        repeat(3) {
            val householdRef = households().document()
            val code = InviteCodes.generate()
            val batch = db.batch()
            batch.set(
                householdRef,
                mapOf(
                    "name" to name.trim(),
                    "ownerId" to user.uid,
                    "inviteCode" to code,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                householdRef.collection("members").document(user.uid),
                mapOf(
                    "uid" to user.uid,
                    "displayName" to user.displayName,
                    "role" to Member.ROLE_OWNER,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("inviteCodes").document(code),
                mapOf(
                    "householdId" to householdRef.id,
                    "householdName" to name.trim(),
                    "ownerId" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(users().document(user.uid), mapOf("householdId" to householdRef.id, "pendingHouseholdId" to null))
            try {
                batch.commit().await()
                return householdRef.id
            } catch (e: FirebaseFirestoreException) {
                // A code collision shows up as PERMISSION_DENIED (rules forbid overwriting a code). Try a new one.
                lastError = e
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            }
        }
        throw lastError ?: IllegalStateException("Couldn't create household")
    }

    /** Returns null when no household uses that code. */
    suspend fun lookupInvite(code: String): InviteInfo? {
        val normalized = InviteCodes.normalize(code)
        val snap = db.collection("inviteCodes").document(normalized).get().await()
        if (!snap.exists()) return null
        return InviteInfo(
            code = normalized,
            householdId = snap.getString("householdId").orEmpty(),
            householdName = snap.getString("householdName").orEmpty()
        )
    }

    suspend fun requestToJoin(user: UserProfile, invite: InviteInfo) {
        val batch = db.batch()
        batch.set(
            household(invite.householdId).collection("joinRequests").document(user.uid),
            mapOf(
                "uid" to user.uid,
                "displayName" to user.displayName,
                "email" to user.email,
                "code" to invite.code,
                "status" to JoinRequest.STATUS_PENDING,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(users().document(user.uid), "pendingHouseholdId", invite.householdId)
        batch.commit().await()
    }

    fun observeMyJoinRequest(householdId: String, uid: String): Flow<JoinRequest?> =
        household(householdId).collection("joinRequests").document(uid).snapshots()
            .map { if (it.exists()) it.toJoinRequest() else null }

    suspend fun cancelJoinRequest(householdId: String, uid: String) {
        val batch = db.batch()
        batch.delete(household(householdId).collection("joinRequests").document(uid))
        batch.update(users().document(uid), "pendingHouseholdId", null)
        batch.commit().await()
    }

    /** Step 4: only succeeds once the owner approved joinRequests/{uid}. */
    suspend fun completeJoin(user: UserProfile, householdId: String) {
        val batch = db.batch()
        batch.set(
            household(householdId).collection("members").document(user.uid),
            mapOf(
                "uid" to user.uid,
                "displayName" to user.displayName,
                "role" to Member.ROLE_MEMBER,
                "joinedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(users().document(user.uid), mapOf("householdId" to householdId, "pendingHouseholdId" to null))
        batch.commit().await()
    }

    /** Owner only (rules): pending requests waiting for a decision. */
    fun observePendingRequests(householdId: String): Flow<List<JoinRequest>> =
        household(householdId).collection("joinRequests")
            .whereEqualTo("status", JoinRequest.STATUS_PENDING)
            .snapshots()
            .map { snap -> snap.documents.map { it.toJoinRequest() } }

    suspend fun decideJoinRequest(householdId: String, requesterUid: String, ownerUid: String, approve: Boolean) {
        household(householdId).collection("joinRequests").document(requesterUid).update(
            mapOf(
                "status" to if (approve) JoinRequest.STATUS_APPROVED else JoinRequest.STATUS_DECLINED,
                "decidedBy" to ownerUid,
                "decidedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    companion object {
        /** Keeps per-expense participant checks within the rules' document-access limit. */
        const val MAX_MEMBERS = 8
    }
}
