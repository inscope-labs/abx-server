package com.inscopelabs.abx.server.compliance

import com.inscopelabs.abx.server.R

class AboutBottomSheet : BaseWebViewBottomSheet() {
    override val titleResId: Int = R.string.menu_about
    override val assetPath: String = "file:///android_asset/compliance/about.html"
}
