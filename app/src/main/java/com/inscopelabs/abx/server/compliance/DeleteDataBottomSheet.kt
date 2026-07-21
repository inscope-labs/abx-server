package com.inscopelabs.abx.server.compliance

import com.inscopelabs.abx.server.R

class DeleteDataBottomSheet : BaseWebViewBottomSheet() {
    override val titleResId: Int = R.string.menu_delete_data
    override val assetPath: String = "file:///android_asset/compliance/delete_data.html"
}
