package edu.virginia.cs4720.roomease.domain

/** What on-device text recognition could suggest. Either field may be missing; the user can always edit. */
data class ReceiptSuggestion(
    val merchant: String? = null,
    val totalCents: Long? = null
) {
    val isEmpty: Boolean get() = merchant == null && totalCents == null
}

/**
 * Turns raw OCR text into a merchant + total suggestion. Pure Kotlin so it's unit tested.
 * Heuristics: the total is the amount on a "TOTAL" / "AMOUNT DUE" line (not SUBTOTAL),
 * falling back to the largest amount on the receipt; the merchant is the first
 * text-like line near the top that isn't an address, phone number or date.
 */
object ReceiptParser {
    private val amountRegex = Regex("""(?<![\d.])\$?\s?(\d{1,3}(?:,\d{3})+|\d{1,6})[.,](\d{2})(?!\d)""")
    private val totalKeywords = listOf("grand total", "amount due", "balance due", "total due", "total")
    private val notTotalKeywords = listOf("subtotal", "sub total", "sub-total", "tax", "tip", "savings", "change", "discount")
    private val skipMerchant = Regex(
        """(?i)(\d{3}[-.\s]?\d{3}[-.\s]?\d{4})|(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})|^\d+\s+\w+|receipt|welcome|thank|www\.|\.com|store\s*#|tel|phone"""
    )

    fun parse(text: String): ReceiptSuggestion {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ReceiptSuggestion(merchant = findMerchant(lines), totalCents = findTotal(lines))
    }

    private fun amountsIn(line: String): List<Long> = amountRegex.findAll(line).map { match ->
        val dollars = match.groupValues[1].replace(",", "").toLong()
        dollars * 100 + match.groupValues[2].toLong()
    }.toList()

    private fun findTotal(lines: List<String>): Long? {
        for (keyword in totalKeywords) {
            lines.forEachIndexed { index, raw ->
                val line = raw.lowercase()
                if (keyword in line && notTotalKeywords.none { it in line }) {
                    // The amount is usually on the same line; some receipts put it on the next one.
                    val amount = amountsIn(raw).lastOrNull() ?: lines.getOrNull(index + 1)?.let { amountsIn(it).lastOrNull() }
                    if (amount != null && amount > 0) return amount
                }
            }
        }
        return lines.flatMap { amountsIn(it) }.filter { it in 1..10_000_000 }.maxOrNull()
    }

    private fun findMerchant(lines: List<String>): String? = lines.take(6).firstOrNull { line ->
        line.count { it.isLetter() } >= 3 && !skipMerchant.containsMatchIn(line) && amountsIn(line).isEmpty()
    }?.let { name ->
        name.lowercase().split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }.take(60)
    }
}
