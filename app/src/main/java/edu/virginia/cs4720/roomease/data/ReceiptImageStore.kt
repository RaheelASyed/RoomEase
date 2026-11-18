package edu.virginia.cs4720.roomease.data

import java.io.File

/**
 * Receipt photos live ONLY in app-private storage (filesDir/receipts). Other apps can't read them,
 * they're never uploaded, and they're deleted when the app is uninstalled.
 * A capture in progress is "pending_<expenseId>.jpg" so it survives rotation and process death;
 * on save it becomes "<expenseId>.jpg".
 */
class ReceiptImageStore(filesDir: File) {
    private val dir = File(filesDir, "receipts").apply { mkdirs() }

    fun pendingFile(expenseId: String): File = File(dir, "pending_${safe(expenseId)}.jpg")

    fun savedFile(expenseId: String): File = File(dir, "${safe(expenseId)}.jpg")

    fun hasSavedReceipt(expenseId: String): Boolean = savedFile(expenseId).exists()

    /** Moves the pending capture into place for a saved expense. */
    fun commit(expenseId: String): Boolean {
        val pending = pendingFile(expenseId)
        if (!pending.exists()) return false
        val target = savedFile(expenseId)
        return pending.renameTo(target) || (pending.copyTo(target, overwrite = true).exists() && pending.delete())
    }

    fun discardPending(expenseId: String) {
        pendingFile(expenseId).delete()
    }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
}
