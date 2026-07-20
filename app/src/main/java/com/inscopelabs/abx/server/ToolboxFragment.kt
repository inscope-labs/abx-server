package com.inscopelabs.abx.server

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

class ToolboxFragment : Fragment(R.layout.fragment_toolbox) {

    private var navigationCallback: ToolboxNavigation? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ToolboxNavigation) {
            navigationCallback = context
        } else {
            throw RuntimeException("$context must implement ToolboxNavigation")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolboxToolbar)
        toolbar?.setNavigationOnClickListener {
            navigationCallback?.returnFromToolbox()
        }
    }

    override fun onDetach() {
        super.onDetach()
        navigationCallback = null
    }
}
