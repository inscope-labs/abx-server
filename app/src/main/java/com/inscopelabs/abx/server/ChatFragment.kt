package com.inscopelabs.abx.server

import androidx.fragment.app.Fragment

/**
 * One of the two main user views. Renders at the exact same coordinates as
 * FilesFragment (both fill mainContentContainer) — toggled via the
 * Chat/Files switch, never shown simultaneously with FilesFragment.
 */
class ChatFragment : Fragment(R.layout.fragment_chat)
