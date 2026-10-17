package com.medishelf.core.work.di

import com.medishelf.core.domain.reminders.ReminderTrigger
import com.medishelf.core.work.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkModule {
    @Binds abstract fun reminderTrigger(impl: ReminderScheduler): ReminderTrigger
}
