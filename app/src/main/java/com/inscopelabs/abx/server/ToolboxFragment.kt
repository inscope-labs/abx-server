package com.inscopelabs.abx.server

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
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

        setupTabs(view)

        view.findViewById<View>(R.id.openContextPackageRow)?.setOnClickListener {
            startActivity(Intent(requireContext(), ContextPackageActivity::class.java))
        }
    }

    private fun setupTabs(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.toolboxTabLayout) ?: return
        val containerTools = view.findViewById<View>(R.id.containerToolsTab) ?: return
        val containerUserDb = view.findViewById<View>(R.id.containerUserDbTab) ?: return

        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_tools)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_userdb)))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                val isTools = position == 0
                containerTools.visibility = if (isTools) View.VISIBLE else View.GONE
                containerUserDb.visibility = if (isTools) View.GONE else View.VISIBLE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default to Tools tab (position 0)
        containerTools.visibility = View.VISIBLE
        containerUserDb.visibility = View.GONE
    }

    override fun onDetach() {
        super.onDetach()
        navigationCallback = null
    }
}
