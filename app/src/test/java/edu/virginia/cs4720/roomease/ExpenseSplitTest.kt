package edu.virginia.cs4720.roomease

import edu.virginia.cs4720.roomease.data.model.Expense
import edu.virginia.cs4720.roomease.domain.ExpenseSplit
import edu.virginia.cs4720.roomease.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseSplitTest {

    @Test
    fun `even split has no leftover`() {
        assertEquals(mapOf("a" to 500L, "b" to 500L), ExpenseSplit.split(1000, listOf("a", "b")))
    }

    @Test
    fun `leftover cents go to the first ids in sorted order`() {
        // 1000 / 3 = 333 r 1 -> "a" gets the extra cent no matter the input order
        val split = ExpenseSplit.split(1000, listOf("c", "a", "b"))
        assertEquals(mapOf("a" to 334L, "b" to 333L, "c" to 333L), split)
    }

    @Test
    fun `shares always add back up to the total`() {
        for (total in listOf(1L, 2L, 99L, 100L, 101L, 12_345L, 99_999_99L)) {
            for (n in 1..8) {
                val ids = (1..n).map { "u$it" }
                val split = ExpenseSplit.split(total, ids)
                assertEquals("total=$total n=$n", total, split.values.sum())
                assertTrue(split.values.max() - split.values.min() <= 1)
            }
        }
    }

    @Test
    fun `split is deterministic regardless of input order`() {
        val a = ExpenseSplit.split(1001, listOf("z", "m", "a", "q"))
        val b = ExpenseSplit.split(1001, listOf("a", "q", "z", "m"))
        assertEquals(a, b)
    }

    @Test
    fun `duplicates and empty input are handled`() {
        assertEquals(mapOf("a" to 50L, "b" to 50L), ExpenseSplit.split(100, listOf("a", "b", "a")))
        assertTrue(ExpenseSplit.split(100, emptyList()).isEmpty())
        assertTrue(ExpenseSplit.split(0, listOf("a")).isEmpty())
    }

    @Test
    fun `pairwise balances from my point of view`() {
        val expenses = listOf(
            Expense(id = "1", totalCents = 900, paidBy = "me", participantIds = listOf("me", "r", "a")), // r, a owe me 300 each
            Expense(id = "2", totalCents = 1000, paidBy = "r", participantIds = listOf("me", "r")) // I owe r 500
        )
        val balances = ExpenseSplit.balancesFor("me", expenses)
        assertEquals(-200L, balances["r"]) // 300 - 500
        assertEquals(300L, balances["a"])
    }

    @Test
    fun `household net sums to zero`() {
        val expenses = listOf(
            Expense(id = "1", totalCents = 1001, paidBy = "a", participantIds = listOf("a", "b", "c")),
            Expense(id = "2", totalCents = 250, paidBy = "b", participantIds = listOf("a", "c"))
        )
        assertEquals(0L, ExpenseSplit.householdNet(expenses).values.sum())
    }

    @Test
    fun `money parsing never uses floating point`() {
        assertEquals(1234L, Money.parseCents("12.34"))
        assertEquals(1200L, Money.parseCents("12"))
        assertEquals(1250L, Money.parseCents("12.5"))
        assertEquals(123456L, Money.parseCents("$1,234.56"))
        assertEquals(5L, Money.parseCents(".05"))
        assertEquals(1L, Money.parseCents("0.01"))
        assertNull(Money.parseCents("0"))
        assertNull(Money.parseCents("12.345"))
        assertNull(Money.parseCents("abc"))
        assertNull(Money.parseCents(""))
        assertEquals("12.05", Money.toInput(1205))
        assertEquals("$12.05", Money.format(1205))
    }
}
