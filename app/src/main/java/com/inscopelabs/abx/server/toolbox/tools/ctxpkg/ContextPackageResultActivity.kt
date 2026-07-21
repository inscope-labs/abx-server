package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inscopelabs.abx.server.R
import java.text.NumberFormat
import java.util.Locale

class ContextPackageResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_context_package_result)

        // Retrieve manifest
        val manifest = intent.getSerializableExtra("manifest") as? BuildManifest
        if (manifest == null) {
            Toast.makeText(this, "Error: Manifest not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Bind views
        val fileCountText = findViewById<TextView>(R.id.fileCountText)
        val totalTokensText = findViewById<TextView>(R.id.totalTokensText)
        val partCountText = findViewById<TextView>(R.id.partCountText)
        val skippedWarningCard = findViewById<View>(R.id.skippedWarningCard)
        val recyclerView = findViewById<RecyclerView>(R.id.processedFilesRecyclerView)
        val viewInFilesButton = findViewById<Button>(R.id.viewInFilesButton)
        val doneButton = findViewById<Button>(R.id.doneButton)

        // Set statistics with thousands separators
        val nf = NumberFormat.getInstance(Locale.US)
        fileCountText.text = nf.format(manifest.fileCount)
        totalTokensText.text = nf.format(manifest.totalTokens)
        partCountText.text = nf.format(manifest.partCount)

        // Show/Hide warning card if any files/dirs were skipped
        val hasSkippedFiles = !manifest.skippedFiles.isNullOrEmpty()
        val hasSkippedDirs = !manifest.skippedDirs.isNullOrEmpty()
        if (hasSkippedFiles || hasSkippedDirs) {
            skippedWarningCard.visibility = View.VISIBLE
        } else {
            skippedWarningCard.visibility = View.GONE
        }

        // Setup processed files list
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ProcessedFileAdapter(manifest.files)

        // View in Files action with robust intent fallback
        viewInFilesButton.setOnClickListener {
            val uri = Uri.parse(manifest.outputDir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "No compatible file manager found on this device", Toast.LENGTH_LONG).show()
                }
            }
        }

        doneButton.setOnClickListener {
            finish()
        }
    }
}
