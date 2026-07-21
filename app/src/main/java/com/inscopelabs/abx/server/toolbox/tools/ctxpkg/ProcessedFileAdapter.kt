package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inscopelabs.abx.server.R
import java.util.Locale

class ProcessedFileAdapter(
    private val files: List<ProcessedFile>
) : RecyclerView.Adapter<ProcessedFileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.itemIcon)
        val name: TextView = view.findViewById(R.id.itemName)
        val info: TextView = view.findViewById(R.id.itemInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_processed_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]

        holder.icon.setImageResource(R.drawable.ic_insert_drive_file)
        holder.name.text = file.path
        holder.info.text = String.format(Locale.US, "%d tokens • %s", file.tokens, formatBytes(file.bytes))
    }

    override fun getItemCount(): Int = files.size

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
