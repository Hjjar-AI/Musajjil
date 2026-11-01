package com.musajjil.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

val StorageJson = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
inline fun <reified T> stored(value: T): String = StorageJson.encodeToString(value)
inline fun <reified T> restored(value: String): T = StorageJson.decodeFromString(value)
