package com.inscopelabs.abx.server.workspace.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inscopelabs.abx.server.R

private const val VIEW_TYPE_USER = 0
private const val VIEW_TYPE_ASSISTANT = 1

class ChatAdapter(
    private val markdownRenderer: ChatMarkdownRenderer = ChatMarkdownRenderer()
) : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == MessageRole.USER) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_chat_message_user, parent, false))
        } else {
            AssistantMessageViewHolder(inflater.inflate(R.layout.item_chat_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message, markdownRenderer)
        }
    }

    private class UserMessageViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.messageBubbleText)

        fun bind(message: Message) {
            text.text = message.content
        }
    }

    private class AssistantMessageViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.assistantLabel)
        private val content: TextView = itemView.findViewById(R.id.messageContentText)

        fun bind(message: Message, markdownRenderer: ChatMarkdownRenderer) {
            content.text = markdownRenderer.render(message.content)
            val context = itemView.context
            if (message.status == MessageStatus.ERROR) {
                label.text = context.getString(R.string.chat_assistant_error_label)
                label.setTextColor(ContextCompat.getColor(context, R.color.color_error))
                content.setTextColor(ContextCompat.getColor(context, R.color.color_error))
            } else {
                label.text = context.getString(R.string.chat_assistant_label)
                label.setTextColor(ContextCompat.getColor(context, R.color.color_on_surface_variant))
                content.setTextColor(ContextCompat.getColor(context, R.color.color_on_surface))
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem == newItem
            }
        }
    }
}
