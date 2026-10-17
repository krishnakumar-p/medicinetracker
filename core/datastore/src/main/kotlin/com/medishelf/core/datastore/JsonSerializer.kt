package com.medishelf.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** DataStore serializer for a @Serializable class; unknown keys are ignored for forward compatibility. */
internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}
