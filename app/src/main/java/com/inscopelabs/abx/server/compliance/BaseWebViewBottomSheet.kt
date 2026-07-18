package com.inscopelabs.abx.server.compliance

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.inscopelabs.abx.server.R

abstract class BaseWebViewBottomSheet : DialogFragment() {

    abstract val titleResId: Int
    abstract val assetPath: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_webview, container, false)
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

        val tvTitle: TextView = view.findViewById(R.id.tvBottomSheetTitle)
        val btnClose: Button = view.findViewById(R.id.btnBottomSheetClose)
        val webView: WebView = view.findViewById(R.id.webViewBottomSheet)

        tvTitle.setText(titleResId)

        btnClose.setOnClickListener {
            dismiss()
        }

        // Configure WebView securely
        webView.settings.apply {
            javaScriptEnabled = false // basic static compliance content needs no script execution
            allowFileAccess = true // required to load files from assets/
            allowContentAccess = false
        }

        // Load the HTML content from assets/
        webView.loadUrl(assetPath)
    }
}
