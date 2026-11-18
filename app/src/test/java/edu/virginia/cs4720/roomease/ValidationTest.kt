package edu.virginia.cs4720.roomease

import edu.virginia.cs4720.roomease.domain.InviteCodes
import edu.virginia.cs4720.roomease.domain.Validation
import edu.virginia.cs4720.roomease.domain.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test
    fun emails() {
        assertTrue(Validation.isValidEmail("raheel@virginia.edu"))
        assertTrue(Validation.isValidEmail("  a.b+c@x.io "))
        assertFalse(Validation.isValidEmail("raheel@"))
        assertFalse(Validation.isValidEmail("not an email"))
    }

    @Test
    fun invite_codes_match_the_rules_alphabet() {
        repeat(200) {
            val code = InviteCodes.generate()
            assertEquals(6, code.length)
            assertTrue(code, Regex("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$").matches(code))
        }
        assertEquals("ABC234", InviteCodes.normalize(" abc-234 "))
        assertTrue(InviteCodes.isWellFormed("abc234"))
        assertFalse(InviteCodes.isWellFormed("ABC10O")) // look-alike characters aren't used
    }

    @Test
    fun initials() {
        assertEquals("RS", initialsOf("Raheel Syed"))
        assertEquals("RA", initialsOf("Rayyan"))
        assertEquals("?", initialsOf("  "))
    }
}
