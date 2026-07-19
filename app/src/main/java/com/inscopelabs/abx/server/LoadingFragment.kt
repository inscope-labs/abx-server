package com.inscopelabs.abx.server

import androidx.fragment.app.Fragment

/**
 * Shown first, in mainContentContainer, until the startup process completes.
 * Root view is transparent so the gray root canvas stays visible behind it.
 */
class LoadingFragment : Fragment(R.layout.fragment_loading)
