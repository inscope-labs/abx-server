package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages saved context selections in the app's private storage.
 * Equivalent to the Node.js --context-file read/write, but with multiple named sets.
 */
class ContextStore(private val context: Context) {

    private val storeDir = File(context.filesDir, "contexts").apply { mkdirs() }

    fun saveSelection(selection: ContextSelection): String {
        val json = selection.toJson()
        val file = File(storeDir, "${selection.id}.json")
        file.writeText(json.toString(2))
        return selection.id
    }

    fun loadSelection(id: String): ContextSelection? {
        val file = File(storeDir, "$id.json")
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        return ContextStore.fromJson(json)
    }

    fun loadAllSelections(): List<ContextSelection> {
        return storeDir.listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    ContextStore.fromJson(JSONObject(file.readText()))
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    fun deleteSelection(id: String): Boolean {
        return File(storeDir, "$id.json").delete()
    }

    fun exists(id: String): Boolean = File(storeDir, "$id.json").exists()

    // Internal JSON (de)serialization
    private fun ContextSelection.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("timestamp", timestamp)
        put("defaultPurpose", defaultPurpose.value)
        put("defaultPriority", defaultPriority)
        put("items", JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("uri", item.uri.toString())
                    put("displayName", item.displayName)
                    put("mimeType", item.mimeType ?: "")
                    put("sizeBytes", item.sizeBytes)
                    put("isDirectory", item.isDirectory)
                    put("purpose", item.purpose.value)
                    put("priority", item.priority)
                })
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): ContextSelection {
            val itemsArray = json.getJSONArray("items")
            val items = (0 until itemsArray.length()).map { i ->
                val obj = itemsArray.getJSONObject(i)
                SelectedItem(
                    uri = Uri.parse(obj.getString("uri")),
                    displayName = obj.getString("displayName"),
                    mimeType = obj.optString("mimeType", null).takeIf { it.isNotEmpty() },
                    sizeBytes = obj.getLong("sizeBytes"),
                    isDirectory = obj.getBoolean("isDirectory"),
                    purpose = Purpose.valueOf(obj.getString("purpose").uppercase()),
                    priority = obj.getInt("priority")
                )
            }
            return ContextSelection(
                id = json.getString("id"),
                name = json.getString("name"),
                timestamp = json.getLong("timestamp"),
                items = items,
                defaultPurpose = Purpose.valueOf(json.getString("defaultPurpose").uppercase()),
                defaultPriority = json.getInt("defaultPriority")
            )
        }

        fun createNew(name: String, items: List<SelectedItem>, defaultPurpose: Purpose, defaultPriority: Int): ContextSelection =
            ContextSelection(
                id = UUID.randomUUID().toString(),
                name = name,
                timestamp = System.currentTimeMillis(),
                items = items,
                defaultPurpose = defaultPurpose,
                defaultPriority = defaultPriority
            )
    }
}
