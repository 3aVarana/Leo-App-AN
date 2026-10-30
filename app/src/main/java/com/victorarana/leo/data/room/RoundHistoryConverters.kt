package com.victorarana.leo.data.room

import androidx.room.TypeConverter
import com.victorarana.leo.domain.LeoJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.Instant
import java.util.UUID

/**
 * How the stored types become SQLite columns: UUIDs as lowercase text, instants as epoch
 * milliseconds (finer precision is dropped), lists as JSON text.
 */
class RoundHistoryConverters {
    @TypeConverter
    fun uuidToString(value: UUID): String = value.toString()

    @TypeConverter
    fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun stringsToJson(value: List<String>): String = LeoJson.encodeToString(STRINGS, value)

    @TypeConverter
    fun jsonToStrings(value: String): List<String> = LeoJson.decodeFromString(STRINGS, value)

    @TypeConverter
    fun booleansToJson(value: List<Boolean>): String = LeoJson.encodeToString(BOOLEANS, value)

    @TypeConverter
    fun jsonToBooleans(value: String): List<Boolean> = LeoJson.decodeFromString(BOOLEANS, value)

    private companion object {
        val STRINGS = ListSerializer(String.serializer())
        val BOOLEANS = ListSerializer(Boolean.serializer())
    }
}
