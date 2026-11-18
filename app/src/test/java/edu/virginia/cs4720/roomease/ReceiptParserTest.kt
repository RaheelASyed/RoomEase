package edu.virginia.cs4720.roomease

import edu.virginia.cs4720.roomease.domain.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun `finds merchant and total, ignoring subtotal and tax`() {
        val text = """
            TRADER JOE'S
            2025 Bond St
            Charlottesville VA 22901
            BANANAS            1.29
            MILK               3.49
            SUBTOTAL           4.78
            TAX                0.25
            TOTAL              5.03
            VISA               5.03
        """.trimIndent()
        val result = ReceiptParser.parse(text)
        assertEquals("Trader Joe's", result.merchant)
        assertEquals(503L, result.totalCents)
    }

    @Test
    fun `total on the next line and thousands separators`() {
        val result = ReceiptParser.parse("Best Buy\nAmount Due\n$1,299.99\nThank you")
        assertEquals("Best Buy", result.merchant)
        assertEquals(129_999L, result.totalCents)
    }

    @Test
    fun `falls back to the largest amount when there is no total line`() {
        val result = ReceiptParser.parse("Corner Cafe\nLatte 4.50\nMuffin 3.25\n7.75")
        assertEquals(775L, result.totalCents)
    }

    @Test
    fun `skips phone numbers and dates when picking the merchant`() {
        val result = ReceiptParser.parse("434-555-0199\n11/18/2026\nHarris Teeter\nTotal 12.00")
        assertEquals("Harris Teeter", result.merchant)
    }

    @Test
    fun `unreadable text gives an empty suggestion`() {
        val result = ReceiptParser.parse("")
        assertNull(result.merchant)
        assertNull(result.totalCents)
        assertTrue(result.isEmpty)
    }
}
