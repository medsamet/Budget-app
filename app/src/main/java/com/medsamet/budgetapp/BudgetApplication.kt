package com.medsamet.budgetapp

import android.app.Application
import com.medsamet.budgetapp.notif.Reminders

class BudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.scheduleDailyCheck(this)
    }
}
