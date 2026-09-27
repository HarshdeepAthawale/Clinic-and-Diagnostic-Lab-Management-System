package com.cdlms.appointment.reminder;

/** Delivers one reminder. Throwing leaves the reminder unsent, so the next run retries it. */
public interface ReminderSender {

    void send(Reminder reminder);
}
