package edu.virginia.cs4720.roomease.domain

import edu.virginia.cs4720.roomease.data.model.Expense
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object ExpenseSplit {

    /**
     * Splits [totalCents] across [participantIds] with no rounding loss.
     * Everyone gets total / n; the leftover cents (total % n) go one each to the first
     * participants in sorted-uid order. Sorting makes the result identical on every phone.
     * Example: 1000 cents / 3 -> sorted ids [a, b, c] get 334, 333, 333.
     */
    fun split(totalCents: Long, participantIds: Collection<String>): Map<String, Long> {
        val ids = participantIds.distinct().sorted()
        if (ids.isEmpty() || totalCents <= 0) return emptyMap()
        val base = totalCents / ids.size
        val leftover = (totalCents % ids.size).toInt()
        return ids.mapIndexed { index, id -> id to base + if (index < leftover) 1 else 0 }.toMap()
    }

    /**
     * Pairwise balances from [me]'s point of view: positive = that roommate owes me,
     * negative = I owe them. Each participant owes the payer their share.
     */
    fun balancesFor(me: String, expenses: List<Expense>): Map<String, Long> {
        val net = mutableMapOf<String, Long>()
        expenses.forEach { expense ->
            split(expense.totalCents, expense.participantIds).forEach { (participant, share) ->
                when {
                    participant == expense.paidBy -> Unit
                    expense.paidBy == me -> net[participant] = (net[participant] ?: 0) + share
                    participant == me -> net[expense.paidBy] = (net[expense.paidBy] ?: 0) - share
                }
            }
        }
        return net
    }

    /** Household ledger: what each member paid minus what they consumed (positive = owed money). */
    fun householdNet(expenses: List<Expense>): Map<String, Long> {
        val net = mutableMapOf<String, Long>()
        expenses.forEach { expense ->
            net[expense.paidBy] = (net[expense.paidBy] ?: 0) + expense.totalCents
            split(expense.totalCents, expense.participantIds).forEach { (participant, share) ->
                net[participant] = (net[participant] ?: 0) - share
            }
        }
        return net
    }
}

object Money {
    private val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun format(cents: Long): String = format.format(cents / 100.0)

    fun formatAbs(cents: Long): String = format(abs(cents))

    /**
     * Parses what the user typed ("12", "12.5", "$1,234.56") into cents without using floating point.
     * Returns null for anything that isn't a positive amount with at most two decimals.
     */
    fun parseCents(input: String): Long? {
        val cleaned = input.trim().removePrefix("$").replace(",", "").trim()
        if (!Regex("""\d{1,7}(\.\d{0,2})?|\.\d{1,2}""").matches(cleaned)) return null
        val parts = cleaned.split('.')
        val dollars = parts[0].ifEmpty { "0" }.toLong()
        val centsPart = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).ifEmpty { "00" }.toLong()
        val total = dollars * 100 + centsPart
        return total.takeIf { it > 0 }
    }

    /** "1234" cents -> "12.34" for an editable text field. */
    fun toInput(cents: Long): String = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)
}
