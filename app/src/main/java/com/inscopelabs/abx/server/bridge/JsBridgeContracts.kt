package com.inscopelabs.abx.server.bridge

import org.json.JSONObject

/**
 * Implement this interface to define native business logic exposed to a
 * Toolbox tool's JavaScript. Injected into JsBridgeManager per tool session.
 */
interface JsActionHandler {
    /**
     * Handle an action from JavaScript.
     * @return A JSON-serializable result (String, Int, JSONObject, etc.)
     * @throws Exception if the action fails (sent back to JS as an error)
     */
    fun handle(action: String, payload: JSONObject): Any?
}

data class JsRequest(
    val action: String,
    val requestId: String?,
    val payload: JSONObject
)

data class JsResponse(
    val requestId: String?,
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        requestId?.let { put("requestId", it) }
        put("success", success)
        data?.let { put("data", it) }
        error?.let { put("error", it) }
    }.toString()
}
