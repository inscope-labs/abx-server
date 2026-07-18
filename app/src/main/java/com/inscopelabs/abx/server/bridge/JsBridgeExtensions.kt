package com.inscopelabs.abx.server.bridge

import org.json.JSONObject

fun JSONObject.safeGetString(key: String): String = optString(key, "")
fun JSONObject.safeGetInt(key: String): Int = optInt(key, 0)
fun JSONObject.safeGetBoolean(key: String): Boolean = optBoolean(key, false)
