package com.inscopelabs.abx.server.compliance

import com.inscopelabs.abx.server.R

class PrivacyPolicyBottomSheet : BaseWebViewBottomSheet() {
    override val titleResId: Int = R.string.menu_privacy_policy
    override val assetPath: String = "file:///android_asset/compliance/privacy_policy.html"
}
