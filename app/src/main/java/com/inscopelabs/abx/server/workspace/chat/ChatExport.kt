package com.inscopelabs.abx.server.workspace.chat

class ChatExport {
    fun exportSession(session: ChatSession, format: String): String {
        return when (format.lowercase()) {
            "json" -> serializeJson(session)
            "txt" -> serializeTxt(session)
            "html" -> serializeHtml(session)
            else -> serializeMarkdown(session)
        }
    }

    private fun serializeMarkdown(session: ChatSession): String {
        val sb = StringBuilder()
        sb.append("# ${session.title}\n\n")
        session.messages.forEach { msg ->
            sb.append("### ${msg.role}\n${msg.content}\n\n")
        }
        return sb.toString()
    }

    private fun serializeTxt(session: ChatSession): String {
        val sb = StringBuilder()
        sb.append("Title: ${session.title}\n")
        session.messages.forEach { msg ->
            sb.append("[${msg.role}]: ${msg.content}\n")
        }
        return sb.toString()
    }

    private fun serializeJson(session: ChatSession): String {
        return """{
            "id": "${session.id}",
            "title": "${session.title}",
            "provider": "${session.provider}",
            "model": "${session.model}",
            "messages": ${session.messages.map { mapOf("role" to it.role.name, "content" to it.content) }}
        }"""
    }

    private fun serializeHtml(session: ChatSession): String {
        val sb = StringBuilder()
        sb.append("<html><head><title>${session.title}</title></head><body>")
        sb.append("<h1>${session.title}</h1>")
        session.messages.forEach { msg ->
            sb.append("<p><strong>${msg.role}:</strong> ${msg.content}</p>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}