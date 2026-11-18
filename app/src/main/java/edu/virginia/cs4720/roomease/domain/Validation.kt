package edu.virginia.cs4720.roomease.domain

import java.security.SecureRandom

object Validation {
    private val emailRegex = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    /** Firebase's own minimum is 6 characters. */
    fun isValidPassword(password: String): Boolean = password.length >= 6

    fun isValidName(name: String): Boolean = name.trim().length in 1..50

    fun isValidHouseholdName(name: String): Boolean = name.trim().length in 1..60

    fun isValidChoreTitle(title: String): Boolean = title.trim().length in 1..80

    fun isValidExpenseDescription(text: String): Boolean = text.trim().length in 1..80
}

/** 6-character invite codes without look-alike characters (no 0/O, 1/I/L). */
object InviteCodes {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 6
    private val random = SecureRandom()

    fun generate(): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun normalize(input: String): String = input.trim().uppercase().filter { it.isLetterOrDigit() }

    fun isWellFormed(input: String): Boolean = normalize(input).let { code ->
        code.length == LENGTH && code.all { it in ALPHABET }
    }
}

/** "Raheel Syed" -> "RS" for the avatar circles in the mockups. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}
