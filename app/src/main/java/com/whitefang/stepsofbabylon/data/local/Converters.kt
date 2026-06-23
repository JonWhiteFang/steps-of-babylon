package com.whitefang.stepsofbabylon.data.local

import androidx.room.TypeConverter
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromIntIntMap(map: Map<Int, Int>): String = JSONObject(map.mapKeys { it.key.toString() }).toString()

    @TypeConverter
    fun toIntIntMap(json: String): Map<Int, Int> =
        JSONObject(json).let { obj -> obj.keys().asSequence().associate { it.toInt() to obj.getInt(it) } }

    @TypeConverter
    fun fromStringIntMap(map: Map<String, Int>): String = JSONObject(map).toString()

    @TypeConverter
    fun toStringIntMap(json: String): Map<String, Int> =
        JSONObject(json).let { obj -> obj.keys().asSequence().associate { it to obj.getInt(it) } }
}
