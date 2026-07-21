package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inscopelabs.abx.server.R
import java.util.Locale

class SelectedItemAdapter(
    private val items: MutableList<SelectedItem>,
    private val onItemChanged: () -> Unit,
    private val onItemRemoved: (SelectedItem) -> Unit
) : RecyclerView.Adapter<SelectedItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.itemIcon)
        val name: TextView = view.findViewById(R.id.itemName)
        val size: TextView = view.findViewById(R.id.itemSize)
        val purposeChip: TextView = view.findViewById(R.id.purposeChip)
        val decreaseBtn: ImageButton = view.findViewById(R.id.decreasePriorityButton)
        val priorityText: TextView = view.findViewById(R.id.priorityValueText)
        val increaseBtn: ImageButton = view.findViewById(R.id.increasePriorityButton)
        val removeBtn: ImageButton = view.findViewById(R.id.removeItemButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_context, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Icon
        holder.icon.setImageResource(
            if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_insert_drive_file
        )

        // Text labels
        holder.name.text = item.displayName
        holder.size.text = formatBytes(item.sizeBytes)

        // Purpose (tap to cycle)
        holder.purposeChip.text = item.purpose.name
        holder.purposeChip.setOnClickListener {
            val purposes = Purpose.values()
            val nextOrdinal = (item.purpose.ordinal + 1) % purposes.size
            item.purpose = purposes[nextOrdinal]
            holder.purposeChip.text = item.purpose.name
            onItemChanged()
        }

        // Priority
        holder.priorityText.text = item.priority.toString()
        holder.decreaseBtn.setOnClickListener {
            if (item.priority > 1) {
                item.priority--
                holder.priorityText.text = item.priority.toString()
                onItemChanged()
            }
        }
        holder.increaseBtn.setOnClickListener {
            if (item.priority < 10) {
                item.priority++
                holder.priorityText.text = item.priority.toString()
                onItemChanged()
            }
        }

        // Remove button
        holder.removeBtn.setOnClickListener {
            onItemRemoved(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
