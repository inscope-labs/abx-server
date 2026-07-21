package com.inscopelabs.abx.server

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.inscopelabs.abx.server.toolbox.tools.ctxpkg.ContextPackageActivity

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

        view.findViewById<View>(R.id.openContextPackageRow)?.setOnClickListener {
            startActivity(Intent(requireContext(), ContextPackageActivity::class.java))
        }
    }

    override fun onDetach() {
        super.onDetach()
        navigationCallback = null
    }
}
