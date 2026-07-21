package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inscopelabs.abx.server.R
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ContextPackageActivity : AppCompatActivity() {

    private lateinit var contextPackage: ContextPackage
    private val selectedItems = mutableListOf<SelectedItem>()
    private lateinit var adapter: SelectedItemAdapter

    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var addContextItemButton: ImageButton
    private lateinit var tokenBudgetText: TextView
    private lateinit var tokenBudgetProgress: ProgressBar
    private lateinit var buildPackageButton: Button
    private lateinit var progressOverlay: View

    // Pick Multiple Files Launcher
    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w("ContextPackageActivity", "Could not take persistable permission for $uri", e)
                }
                
                val name = with(SafUtils) { getFileName(uri) }
                val size = with(SafUtils) { getFileSize(uri) }
                val mimeType = with(SafUtils) { getMimeType(uri) }
                val isDir = with(SafUtils) { isDirectory(uri) }

                // Avoid duplicates
                if (selectedItems.none { it.uri == uri }) {
                    selectedItems.add(SelectedItem(uri, name, mimeType, size, isDir))
                }
            }
            updateUiState()
        }
    }

    // Pick Folder (Add Folder) Launcher
    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("ContextPackageActivity", "Could not take persistable permission for folder $uri", e)
            }

            val name = with(SafUtils) { getFileName(uri) }
            val size = with(SafUtils) { getFileSize(uri) }
            val isDir = true

            // Avoid duplicates
            if (selectedItems.none { it.uri == uri }) {
                selectedItems.add(SelectedItem(uri, name, null, size, isDir))
            }
            updateUiState()
        }
    }

    // Pick Output Folder for package compile and export
    private val pickOutputFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("ContextPackageActivity", "Could not take persistable permission for output folder $uri", e)
            }
            startBuildFlow(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_context_package)

        contextPackage = ContextPackage.getInstance(applicationContext)

        // Initialize Toolbar
        val toolbar = findViewById<Toolbar>(R.id.contextPackageToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        addContextItemButton = findViewById(R.id.addContextItemButton)
        addContextItemButton.setOnClickListener { showAddPopupMenu() }

        // Initialize Views
        recyclerView = findViewById(R.id.selectedItemsRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        tokenBudgetText = findViewById(R.id.tokenBudgetText)
        tokenBudgetProgress = findViewById(R.id.tokenBudgetProgress)
        buildPackageButton = findViewById(R.id.buildPackageButton)
        progressOverlay = findViewById(R.id.progressOverlay)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SelectedItemAdapter(
            selectedItems,
            onItemChanged = { updateUiState() },
            onItemRemoved = { item ->
                selectedItems.remove(item)
                updateUiState()
            }
        )
        recyclerView.adapter = adapter

        buildPackageButton.setOnClickListener {
            // Pick output directory to build context package
            pickOutputFolderLauncher.launch(null)
        }

        updateUiState()
    }

    private fun showAddPopupMenu() {
        val popup = PopupMenu(this, addContextItemButton)
        popup.menuInflater.inflate(R.menu.context_package_add_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_files -> {
                    pickFilesLauncher.launch(arrayOf("*/*"))
                    true
                }
                R.id.action_add_folder -> {
                    pickFolderLauncher.launch(null)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun updateUiState() {
        adapter.notifyDataSetChanged()

        if (selectedItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            buildPackageButton.isEnabled = false
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            buildPackageButton.isEnabled = true
        }

        // Live token budget estimate: bytes / 4 heuristic
        val totalBytes = selectedItems.sumOf { it.sizeBytes }
        val estimatedTokens = (totalBytes / 4).toInt()
        val maxTokens = Config.DEFAULT_MAX_TOKENS

        val nf = NumberFormat.getInstance(Locale.US)
        tokenBudgetText.text = String.format(
            Locale.US,
            getString(R.string.token_budget),
            nf.format(estimatedTokens),
            nf.format(maxTokens)
        )
        tokenBudgetProgress.max = maxTokens
        tokenBudgetProgress.progress = estimatedTokens.coerceAtMost(maxTokens)
    }

    private fun startBuildFlow(outputDirUri: Uri) {
        progressOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 1. Save current selection
                val selectionName = "Selection_${System.currentTimeMillis()}"
                val selection = contextPackage.saveSelection(
                    name = selectionName,
                    items = selectedItems
                )

                // 2. Export package with builder flow
                val manifest = contextPackage.exportPackage(
                    selectionId = selection.id,
                    outputTreeUri = outputDirUri
                )

                progressOverlay.visibility = View.GONE

                // 3. Launch Result Activity with serializable manifest extra
                val intent = Intent(this@ContextPackageActivity, ContextPackageResultActivity::class.java).apply {
                    putExtra("manifest", manifest)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                progressOverlay.visibility = View.GONE
                Log.e("ContextPackageActivity", "Export package failed", e)
                Toast.makeText(this@ContextPackageActivity, "Build failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
