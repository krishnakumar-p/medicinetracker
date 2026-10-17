package com.medishelf.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Dates as epoch-day (timezone-free), instants as epoch-millis. Enums use Room's built-in name mapping. */
internal class Converters {
    @TypeConverter fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
