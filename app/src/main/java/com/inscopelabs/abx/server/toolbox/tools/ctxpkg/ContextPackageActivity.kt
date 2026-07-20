package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.inscopelabs.abx.server.R

/**
 * Minimal wiring activity for the ctxpkg feature. Wraps
 * ContextPackage.getInstance() (a singleton facade, unchanged — see
 * ContextPackage.kt) and lets the user pick a folder via the Storage
 * Access Framework. This proves the Activity -> ContextPackage ->
 * ToolboxFragment chain works end to end. Selection persistence, preview,
 * and export are separate, later stages — not built here.
 */
class ContextPackageActivity : AppCompatActivity() {

    private lateinit var resultText: TextView

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            resultText.text = "Picked: $uri"
        } else {
            resultText.text = "No folder selected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_context_package)

        // Proves the Activity is correctly wired to the ContextPackage facade.
        val contextPackage = ContextPackage.getInstance(applicationContext)
        Log.d("ContextPackageActivity", "ContextPackage ready, auditLog length=${contextPackage.getAuditLog().length}")

        findViewById<Toolbar>(R.id.contextPackageToolbar).setNavigationOnClickListener { finish() }

        resultText = findViewById(R.id.contextPackageResultText)
        findViewById<Button>(R.id.contextPackagePickFolderButton).setOnClickListener {
            pickFolderLauncher.launch(null)
        }
    }
}
