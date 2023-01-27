package com.wbrawner.civicsquizbot

interface ReminderService {
    fun getRemindersAt(hour: Int, minute: Int): List<Reminder>
    fun setReminderForUser(reminder: Reminder)
    fun deleteReminderForUser(userId: Long)
}

class DatabaseReminderService(private val database: Database) : ReminderService {
    init {
        database.reminderQueries.create()
    }

    override fun getRemindersAt(hour: Int, minute: Int): List<Reminder> = database.reminderQueries
        .selectByTime(hour, minute)
        .executeAsList()

    override fun setReminderForUser(reminder: Reminder) {
        database.reminderQueries.upsertReminder(reminder.user_id, reminder.hour, reminder.minute)
    }

    override fun deleteReminderForUser(userId: Long) {
        database.reminderQueries.deleteReminder(userId)
    }
}