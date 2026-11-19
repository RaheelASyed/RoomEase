package edu.virginia.cs4720.roomease

import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.domain.ChoreBoard
import edu.virginia.cs4720.roomease.domain.ChoreRepeat
import edu.virginia.cs4720.roomease.domain.ChoreSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import edu.virginia.cs4720.roomease.domain.toNoonTimestamp
import java.time.LocalDate

class ChoreScheduleTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `next due date adds one interval`() {
        assertEquals(LocalDate.of(2026, 11, 19), ChoreSchedule.nextDueDate(ChoreRepeat.DAILY, today, today))
        assertEquals(LocalDate.of(2026, 11, 25), ChoreSchedule.nextDueDate(ChoreRepeat.WEEKLY, today, today))
        assertEquals(LocalDate.of(2026, 12, 2), ChoreSchedule.nextDueDate(ChoreRepeat.BIWEEKLY, today, today))
        assertEquals(LocalDate.of(2026, 12, 18), ChoreSchedule.nextDueDate(ChoreRepeat.MONTHLY, today, today))
    }

    @Test
    fun `overdue chores roll forward so the next one is never in the past`() {
        val threeWeeksAgo = today.minusWeeks(3)
        assertEquals(today, ChoreSchedule.nextDueDate(ChoreRepeat.WEEKLY, threeWeeksAgo, today))
        assertEquals(today.plusDays(4), ChoreSchedule.nextDueDate(ChoreRepeat.WEEKLY, today.minusDays(10), today))
    }

    @Test
    fun `rotation goes to the next member and wraps around`() {
        val order = listOf("a", "b", "c")
        assertEquals("b", ChoreSchedule.nextAssignee("a", order, rotate = true))
        assertEquals("c", ChoreSchedule.nextAssignee("b", order, rotate = true))
        assertEquals("a", ChoreSchedule.nextAssignee("c", order, rotate = true))
    }

    @Test
    fun `without rotation the same person keeps it`() {
        assertEquals("b", ChoreSchedule.nextAssignee("b", listOf("a", "b", "c"), rotate = false))
    }

    @Test
    fun `a removed assignee hands the chore to the first member`() {
        assertEquals("a", ChoreSchedule.nextAssignee("gone", listOf("a", "b"), rotate = true))
        assertEquals("a", ChoreSchedule.nextAssignee("gone", listOf("a", "b"), rotate = false))
    }

    @Test
    fun `repeat values round-trip and unknown values mean never`() {
        ChoreRepeat.entries.forEach { assertEquals(it, ChoreRepeat.from(it.value)) }
        assertEquals(ChoreRepeat.NONE, ChoreRepeat.from(null))
        assertEquals(ChoreRepeat.NONE, ChoreRepeat.from("hourly"))
    }

    // ---- weekly board (today = Wed Nov 18, 2026 -> week is Sun Nov 15 .. Sat Nov 21) ----

    private fun chore(id: String, who: String, due: LocalDate, done: Boolean = false, completed: LocalDate? = null,
                      repeat: String = "none", lastBy: String? = null, lastAt: LocalDate? = null) =
        Chore(id = id, title = id, assigneeId = who, assigneeName = who, dueDate = due.toNoonTimestamp(), done = done,
            completedAt = completed?.toNoonTimestamp(), repeat = repeat, lastCompletedBy = lastBy, lastCompletedAt = lastAt?.toNoonTimestamp())

    @Test
    fun `week runs Sunday to Saturday`() {
        val board = ChoreBoard.build(emptyList(), null, today)
        assertEquals(LocalDate.of(2026, 11, 15), board.weekStart)
        assertEquals(LocalDate.of(2026, 11, 21), board.weekEnd)
    }

    @Test
    fun `chores split into this week, upcoming and completed`() {
        val board = ChoreBoard.build(
            listOf(
                chore("overdue", "a", today.minusDays(5)),
                chore("saturday", "a", LocalDate.of(2026, 11, 21)),
                chore("nextSunday", "a", LocalDate.of(2026, 11, 22)),
                chore("doneMon", "a", today, done = true, completed = LocalDate.of(2026, 11, 16))
            ),
            onlyFor = null, today = today
        )
        assertEquals(listOf("overdue", "saturday"), board.thisWeek.map { it.id })
        assertEquals(listOf("nextSunday"), board.upcoming.map { it.id })
        assertEquals(listOf("doneMon"), board.completed.map { it.chore.id })
    }

    @Test
    fun `weekly progress counts one-off and repeating completions this week only`() {
        val board = ChoreBoard.build(
            listOf(
                chore("open", "a", today),                                                        // open this week
                chore("doneThisWeek", "a", today, done = true, completed = today),                // done this week
                chore("doneLastWeek", "a", today, done = true, completed = today.minusWeeks(1)),  // doesn't count
                chore("weeklyAdvanced", "b", today.plusWeeks(1), repeat = "weekly", lastBy = "a", lastAt = today), // done this week
                chore("future", "a", today.plusWeeks(2))                                          // not this week
            ),
            onlyFor = null, today = today
        )
        assertEquals(2, board.weekDone)
        assertEquals(3, board.weekTotal) // 2 done + 1 open
    }

    @Test
    fun `completed only lists this week's completions, including repeating chores`() {
        val board = ChoreBoard.build(
            listOf(
                chore("doneTue", "a", today, done = true, completed = LocalDate.of(2026, 11, 17)),
                chore("doneSun", "a", today, done = true, completed = LocalDate.of(2026, 11, 15)),        // week start: shown
                chore("doneLastSat", "a", today, done = true, completed = LocalDate.of(2026, 11, 14)),    // last week: hidden
                chore("weeklyMon", "b", today.plusWeeks(1), repeat = "weekly", lastBy = "a", lastAt = LocalDate.of(2026, 11, 16)),
                chore("weeklyOld", "b", today, repeat = "weekly", lastBy = "a", lastAt = LocalDate.of(2026, 11, 10)) // last week: hidden
            ),
            onlyFor = null, today = today
        )
        // Newest first; the repeating chore is listed both as a live chore and as a completion.
        assertEquals(listOf("doneTue", "weeklyMon", "doneSun"), board.completed.map { it.chore.id })
        assertEquals(listOf(false, true, false), board.completed.map { it.repeating })
        assertEquals("a", board.completed[1].completedBy)
        assertEquals(listOf("weeklyOld"), board.thisWeek.map { it.id })
        assertEquals(listOf("weeklyMon"), board.upcoming.map { it.id })
        assertEquals(3, board.weekDone)
        // Rows need distinct keys from the live chore rows.
        assertEquals(listOf("doneTue", "done-weeklyMon", "doneSun"), board.completed.map { it.key })
    }

    @Test
    fun `next week, last week's completions are gone`() {
        val nextSunday = LocalDate.of(2026, 11, 22)
        val board = ChoreBoard.build(
            listOf(
                chore("done", "a", today, done = true, completed = today),
                chore("weekly", "b", nextSunday, repeat = "weekly", lastBy = "a", lastAt = today)
            ),
            onlyFor = null, today = nextSunday
        )
        assertEquals(emptyList<String>(), board.completed.map { it.chore.id })
        assertEquals(0, board.weekDone)
        assertEquals(1, board.weekTotal) // the weekly chore, now due this week
    }

    @Test
    fun `my chores filters by assignee, and repeating credit goes to whoever completed it`() {
        val chores = listOf(
            chore("mineOpen", "me", today),
            chore("theirsOpen", "them", today),
            chore("weekly", "them", today.plusWeeks(1), repeat = "weekly", lastBy = "me", lastAt = today)
        )
        val mine = ChoreBoard.build(chores, onlyFor = "me", today = today)
        assertEquals(listOf("mineOpen"), mine.thisWeek.map { it.id })
        assertEquals(1, mine.weekDone)
        assertEquals(2, mine.weekTotal)
        assertEquals(listOf("weekly"), mine.completed.map { it.chore.id }) // I completed it, so it's in my Completed
        val theirs = ChoreBoard.build(chores, onlyFor = "them", today = today)
        assertEquals(emptyList<String>(), theirs.completed.map { it.chore.id })
    }
}
