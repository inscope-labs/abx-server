package com.inscopelabs.abx.server.workspace.chat

class ChatMarkdownRenderer {
    fun render(rawMarkdown: String): String {
        // In a real app, use a Markdown library like Markwon
        // Here we just return as-is for simplicity
        return rawMarkdown.trim()
    }
}