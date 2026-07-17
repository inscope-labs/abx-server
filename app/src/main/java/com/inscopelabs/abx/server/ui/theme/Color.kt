package com.inscopelabs.abx.server.ui.theme

import androidx.compose.ui.graphics.Color

val Color_White = Color(0xFFFFFFFF)

// Heroku-console inspired palette: white / gray / blue.
// Blue is reserved for the one thing that matters (active state, primary action).
// Gray carries structure. White/near-white surfaces stay calm and unbranded.

// --- Blue (accent / primary action / active session) ---
val Blue10 = Color(0xFFEFF4FE)
val Blue20 = Color(0xFFDCE7FC)
val Blue40 = Color(0xFF3D6FE0)
val Blue50 = Color(0xFF2F5FD0)
val Blue80 = Color(0xFF9DBBFA)
val Blue90 = Color(0xFFCFDDFB)

// --- Gray (structure, text, borders, inactive) ---
val Gray05 = Color(0xFFFAFAFA)
val Gray10 = Color(0xFFF2F2F2)
val Gray20 = Color(0xFFE3E3E3)
val Gray30 = Color(0xFFC7C7C7)
val Gray50 = Color(0xFF8A8A8A)
val Gray70 = Color(0xFF4D4D4D)
val Gray85 = Color(0xFF2A2A2A)
val Gray95 = Color(0xFF171717)

// --- Semantic status roles (used instead of ad-hoc hex in components) ---
val SuccessGreen40 = Color(0xFF2E7D32)
val SuccessGreen90 = Color(0xFFDCEFDD)
val WarningAmber40 = Color(0xFFB25E09)
val WarningAmber90 = Color(0xFFFBEBD9)
val ErrorRed40 = Color(0xFFC62828)
val ErrorRed90 = Color(0xFFFAE0E0)
