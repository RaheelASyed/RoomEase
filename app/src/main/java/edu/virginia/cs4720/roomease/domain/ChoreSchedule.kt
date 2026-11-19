package edu.virginia.cs4720.roomease.domain

import com.google.firebase.Timestamp
import edu.virginia.cs4720.roomease.data.model.Chore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date

/** How often a chore comes back. [value] is what's stored in Firestore (and checked by the rules). */
enum class ChoreRepeat(val value: String, val label: String) {
    NONE("none", "Never"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    BIWEEKLY("biweekly", "Every 2 weeks"),
    MONTHLY("monthly", "Monthly");

    fun after(date: LocalDate): LocalDate = when (this) {
        NONE -> date
        DAILY -> date.plusDays(1)
        WEEKLY -> date.plusWeeks(1)
        BIWEEKLY -> date.plusWeeks(2)
        MONTHLY -> date.plusMonths(1)
    }

    companion object {
        fun from(value: String?): ChoreRepeat = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/**
 * Pure scheduling rules for repeating chores (unit tested).
 * When the assignee completes a repeating chore, the same chore moves on to its next occurrence;
 * these functions decide its due date and who it goes to.
 */
object ChoreSchedule {

    /** One interval after the current due date, rolled forward so it's never in the past. */
    fun nextDueDate(repeat: ChoreRepeat, currentDue: LocalDate, today: LocalDate): LocalDate {
        if (repeat == ChoreRepeat.NONE) return currentDue
        var next = repeat.after(currentDue)
        while (next.isBefore(today)) next = repeat.after(next)
        return next
    }

    /**
     * With [rotate], the next member in [memberOrder] (wrapping around); otherwise the same person.
     * If the current assignee has left the household, it goes to the first member.
     * [memberOrder] must be the same on every phone (members sorted by name, then uid).
     */
    fun nextAssignee(currentAssignee: String, memberOrder: List<String>, rotate: Boolean): String {
        if (memberOrder.isEmpty()) return currentAssignee
        val index = memberOrder.indexOf(currentAssignee)
        return when {
            index < 0 -> memberOrder.first()
            !rotate -> currentAssignee
            else -> memberOrder[(index + 1) % memberOrder.size]
        }
    }
}

fun Timestamp.toLocalDate(): LocalDate = toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

/** Noon local time, so a due date never shifts a day across time zones. */
fun LocalDate.toNoonTimestamp(): Timestamp = Timestamp(Date.from(atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()))

/**
 * One row in the Completed section. A one-off chore is the chore itself (done = true);
 * a repeating chore has already moved on to its next due date, so its row is built from
 * lastCompletedBy / lastCompletedAt and shows up alongside the live chore.
 */
data class CompletedChore(
    val chore: Chore,
    val completedBy: String,
    val completedAt: Timestamp?,
    val repeating: Boolean
) {
    /**
     * LazyColumn key. A one-off chore keeps its id so it slides from its section into Completed;
     * a repeating chore's live row already uses chore.id, so its completion needs its own key.
     */
    val key: String get() = if (repeating) "done-${chore.id}" else chore.id
}

/** The Chores tab, split the way it's shown: this week (no header), Upcoming, Completed. */
data class ChoreBoard(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val thisWeek: List<Chore>,
    val upcoming: List<Chore>,
    val completed: List<CompletedChore>,
    val weekDone: Int,
    val weekTotal: Int
) {
    val isEmpty: Boolean get() = thisWeek.isEmpty() && upcoming.isEmpty() && completed.isEmpty()

    companion object {
        /**
         * [onlyFor] = a uid for "My chores", null for "All chores".
         *
         * Everything is scoped to Sunday–Saturday of the current week:
         * - **completed** = one-off chores completed this week + repeating chores last completed
         *   this week (a repeating chore moves on to its next due date when completed, so its
         *   completion comes from lastCompletedAt). They drop off when the next week starts;
         * - **done** = completed.size; **total** = done + open chores due this week (or overdue).
         */
        fun build(
            chores: List<Chore>,
            onlyFor: String?,
            today: LocalDate
        ): ChoreBoard {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val weekEnd = weekStart.plusDays(6)
            fun inWeek(date: LocalDate?) = date != null && !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            fun due(c: Chore) = c.dueDate?.toLocalDate()

            val open = chores.filter { !it.done && (onlyFor == null || it.assigneeId == onlyFor) }
                .sortedWith(compareBy({ due(it) ?: LocalDate.MAX }, { it.title.lowercase() }))
            val thisWeek = open.filter { due(it)?.isAfter(weekEnd) != true } // due this week, overdue, or no date
            val upcoming = open.filter { due(it)?.isAfter(weekEnd) == true }

            val oneOffs = chores
                .filter { it.done && inWeek(it.completedAt?.toLocalDate()) && (onlyFor == null || it.assigneeId == onlyFor) }
                .map { CompletedChore(it, it.assigneeId, it.completedAt, repeating = false) }
            val repeats = chores
                .filter {
                    ChoreRepeat.from(it.repeat) != ChoreRepeat.NONE && !it.done && it.lastCompletedBy != null &&
                        inWeek(it.lastCompletedAt?.toLocalDate()) && (onlyFor == null || it.lastCompletedBy == onlyFor)
                }
                .map { CompletedChore(it, it.lastCompletedBy!!, it.lastCompletedAt, repeating = true) }
            val completed = (oneOffs + repeats).sortedByDescending { it.completedAt?.seconds ?: 0L }

            val done = completed.size
            return ChoreBoard(weekStart, weekEnd, thisWeek, upcoming, completed, done, done + thisWeek.size)
        }
    }
}
