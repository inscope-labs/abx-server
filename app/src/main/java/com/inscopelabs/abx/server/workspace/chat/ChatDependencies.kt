package com.inscopelabs.abx.server.workspace.chat

import android.content.Context

/**
 * Manual, process-lifetime wiring for the chat subsystem. The app has no DI
 * framework (no Hilt/Koin) anywhere else, so this follows the same manual
 * singleton pattern already used by ChatDatabase.getInstance() rather than
 * introducing a new pattern for one subsystem.
 *
 * ChatManager owns a CoroutineScope(SupervisorJob) internally and is meant
 * to outlive any single fragment/activity instance (so an in-flight stream
 * survives rotation), which is why this is keyed off applicationContext and
 * cached for the process, not per-Fragment.
 */
object ChatDependencies {

    @Volatile
    private var chatManagerInstance: ChatManager? = null

    @Volatile
    private var chatSecurityInstance: ChatSecurity? = null

    @Volatile
    private var chatRepositoryInstance: ChatRepository? = null

    fun chatManager(context: Context): ChatManager {
        return chatManagerInstance ?: synchronized(this) {
            chatManagerInstance ?: build(context.applicationContext).also { chatManagerInstance = it }
        }
    }

    fun chatSecurity(context: Context): ChatSecurity {
        return chatSecurityInstance ?: synchronized(this) {
            chatSecurityInstance ?: ChatSecurity(context.applicationContext).also { chatSecurityInstance = it }
        }
    }

    /**
     * Same underlying repository/database instance ChatManager uses
     * internally. Exposed so the ViewModel can do a one-shot "does a
     * session already exist" read against Room directly on startup,
     * instead of racing ChatManager's own StateFlow (which starts at
     * emptyList() and only reflects real data after its internal
     * collector coroutine has had a chance to run).
     */
    fun chatRepository(context: Context): ChatRepository {
        return chatRepositoryInstance ?: synchronized(this) {
            chatRepositoryInstance ?: ChatRepository(ChatDatabase.getInstance(context.applicationContext).chatDao())
                .also { chatRepositoryInstance = it }
        }
    }

    private fun build(appContext: Context): ChatManager {
        val repository = chatRepository(appContext)
        val tokenCounter = TokenCounter()
        val chatMemory = ChatMemory(tokenCounter)
        val promptBuilder = PromptBuilder(tokenCounter)
        val chatLogger = ChatLogger()
        val security = chatSecurity(appContext)
        val chatCache = ChatCache()
        val chatExport = ChatExport()
        val providerFactory = ProviderFactory()

        return ChatManager(
            repository = repository,
            providerFactory = providerFactory,
            promptBuilder = promptBuilder,
            tokenCounter = tokenCounter,
            chatMemory = chatMemory,
            chatLogger = chatLogger,
            chatSecurity = security,
            chatCache = chatCache,
            chatExport = chatExport
        )
    }
}
