package com.inscopelabs.abx.server

import androidx.fragment.app.Fragment

/**
 * One of the two main user views, and the default shown once the loading
 * gate completes. Renders at the exact same coordinates as ChatFragment
 * (both fill mainContentContainer) — toggled via the Chat/Files switch,
 * never shown simultaneously with ChatFragment.
 */
class FilesFragment : Fragment(R.layout.fragment_files)
