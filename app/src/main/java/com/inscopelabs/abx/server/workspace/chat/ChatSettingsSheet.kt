package com.inscopelabs.abx.server.workspace.chat

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.inscopelabs.abx.server.R

/**
 * Lets the user pick a provider/model and store an API key for it
 * (ChatSecurity persists it in EncryptedSharedPreferences). Opened from the
 * settings icon in WorkspaceFragment's top bar, and also reachable by tapping
 * the "no API key" banner.
 */
class ChatSettingsSheet : DialogFragment() {

    companion object {
        private const val ARG_PROVIDER = "provider"
        private const val ARG_MODEL = "model"

        fun newInstance(provider: String, model: String): ChatSettingsSheet {
            return ChatSettingsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER, provider)
                    putString(ARG_MODEL, model)
                }
            }
        }
    }

    /** Invoked with (provider, model, apiKey) when the user taps Save. */
    var onSave: ((provider: String, model: String, apiKey: String) -> Unit)? = null

    private lateinit var chatSecurity: ChatSecurity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_chat_settings, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatSecurity = ChatDependencies.chatSecurity(requireContext())

        val providerSpinner: Spinner = view.findViewById(R.id.chatSettingsProviderSpinner)
        val modelInput: EditText = view.findViewById(R.id.chatSettingsModelInput)
        val apiKeyInput: EditText = view.findViewById(R.id.chatSettingsApiKeyInput)
        val keyStoredHint: TextView = view.findViewById(R.id.chatSettingsKeyStoredHint)
        val cancelButton: Button = view.findViewById(R.id.chatSettingsCancelButton)
        val saveButton: Button = view.findViewById(R.id.chatSettingsSaveButton)

        val providers = ProviderFactory.SUPPORTED_PROVIDERS
        providerSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            providers
        )

        val initialProvider = arguments?.getString(ARG_PROVIDER) ?: "gemini"
        val initialModel = arguments?.getString(ARG_MODEL) ?: ChatSettings().model
        providerSpinner.setSelection(providers.indexOf(initialProvider).coerceAtLeast(0))
        modelInput.setText(initialModel)
        updateKeyStoredHint(keyStoredHint, initialProvider)

        providerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                selectedView: View?,
                position: Int,
                id: Long
            ) {
                updateKeyStoredHint(keyStoredHint, providers[position])
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        cancelButton.setOnClickListener { dismiss() }

        saveButton.setOnClickListener {
            val provider = providers[providerSpinner.selectedItemPosition]
            val model = modelInput.text.toString().trim().ifEmpty { ChatSettings().model }
            val apiKey = apiKeyInput.text.toString().trim()

            if (apiKey.isNotEmpty()) {
                chatSecurity.storeApiKey(provider, apiKey)
            }
            onSave?.invoke(provider, model, apiKey)
            dismiss()
        }
    }

    private fun updateKeyStoredHint(hintView: TextView, provider: String) {
        val hasKey = !chatSecurity.getApiKey(provider).isNullOrBlank()
        hintView.visibility = if (hasKey) View.VISIBLE else View.GONE
    }
}
