package com.inscopelabs.abx.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.core.audit.ReasonCode
import com.inscopelabs.abx.server.core.session.SessionState
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreenContent(
    sessionState: SessionState,
    ttlRemaining: Int,
    isHardwareBacked: Boolean,
    isStrongBoxBacked: Boolean,
    gatewayPairedStatus: String,
    fingerprint: String,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onRotateKey: () -> Unit,
    onShowPairing: () -> Unit,
    onOpenLocalBridge: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onAddSimulatedEvent: (ReasonCode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive = sessionState is SessionState.ACTIVE
    val isChainSecure = remember(sessionState) { AuditLog.verifyIntegrity() }
    val logs = remember(sessionState) { AuditLog.getEntries() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome and Headline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ABX UTILITY V2.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Device Security Console",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isActive) "LIVE" else "STANDBY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. SYSTEM HEALTH PANEL
        ABXCard(
            modifier = Modifier.testTag("dashboard_health_panel"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "System Health & Safety Posture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Posture indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Enclave Security",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isStrongBoxBacked) "StrongBox" else if (isHardwareBacked) "TEE Secured" else "Software",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isHardwareBacked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Audit Integrity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isChainSecure) "SECURE" else "TAMPERED",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isChainSecure) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                // Pairing status summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (gatewayPairedStatus.startsWith("Paired")) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (gatewayPairedStatus.startsWith("Paired")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Gateway Pairing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (gatewayPairedStatus.startsWith("Paired")) "ACTIVE" else "UNPAIRED",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (gatewayPairedStatus.startsWith("Paired")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Dynamic State KPI: Metric row (e.g. TTL remaining, active session id)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ABXMetricCard(
                title = "Session Remaining",
                value = if (isActive) "${ttlRemaining}s" else "Inactive",
                subtitle = if (isActive) "Secure attestation ticket" else "Start session in Access tab",
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1.2f)
            )

            ABXMetricCard(
                title = "Identity Verified",
                value = if (fingerprint.isNotEmpty()) "READY" else "PENDING",
                subtitle = if (fingerprint.isNotEmpty()) "${fingerprint.take(6)}...${fingerprint.takeLast(6)}" else "Keys uninitialized",
                icon = Icons.Default.Fingerprint,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. QUICK ACTIONS CAROUSEL
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Quick Actions & Workflows",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ABXQuickActionCard(
                    title = if (isActive) "Stop Session" else "Start Session",
                    icon = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    onClick = {
                        if (isActive) onStopSession() else onStartSession()
                    },
                    containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )

                ABXQuickActionCard(
                    title = "Paste Share Bridge",
                    icon = Icons.Default.Share,
                    onClick = onOpenLocalBridge
                )

                ABXQuickActionCard(
                    title = "Pair Gateway",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = onShowPairing
                )

                ABXQuickActionCard(
                    title = "Rotate Creds",
                    icon = Icons.Default.Autorenew,
                    onClick = onRotateKey
                )

                ABXQuickActionCard(
                    title = "Simulate Block",
                    icon = Icons.Default.BugReport,
                    onClick = {
                        onAddSimulatedEvent(ReasonCode.PATH_OUT_OF_BOUNDS)
                    }
                )

                ABXQuickActionCard(
                    title = "Security Wipe",
                    icon = Icons.Default.DeleteForever,
                    onClick = {
                        onNavigateToTab(4) // Settings Tab
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            }
        }

        // 3. RUNNING SERVICES STATUS TILE
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Secure Infrastructure Services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            ABXServiceTile(
                title = "Reverse Tunnel Connection",
                status = if (isActive) "Secure WebSocket connection active" else "Standby — waiting for session start",
                isActive = isActive,
                icon = Icons.Default.VpnLock,
                onToggle = { active ->
                    if (active) onStartSession() else onStopSession()
                }
            )
        }

        // 4. AI WORKSPACE SUMMARY
        ABXCard(
            modifier = Modifier.testTag("dashboard_ai_workspace")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI Secure MCP Workspace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Primary Model Node", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Gemini Server Attested", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Security Isolation", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "HSM Isolated Keystore", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Active MCP Policy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Scoped Sandbox v1.1", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. PLUGIN STATUS GRID
        ABXCard(
            modifier = Modifier.testTag("dashboard_plugins_status")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Feature Plugins & Modules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = if (isActive) Color(0xFF2E7D32) else Color.Gray, modifier = Modifier.size(16.dp))
                            Text("File System", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Text("Policy Engine", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.SettingsInputComponent, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Text("Session Manager", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AltRoute, contentDescription = null, tint = if (isActive) Color(0xFF2E7D32) else Color.Gray, modifier = Modifier.size(16.dp))
                            Text("Reverse Tunnel", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // 6. MCP CONNECTIONS & AUTOMATION QUEUE STATUS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ABXCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "MCP Connections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isActive) Color(0xFF2E7D32) else Color.Gray))
                        Text(text = if (isActive) "Gateway: Secure" else "Gateway: Inactive", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2E7D32)))
                        Text(text = "Local: Waiting", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ABXCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Automation Queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(text = "• TTL Check Timer: Running", style = MaterialTheme.typography.bodySmall)
                    Text(text = "• Key Check Cron: Idle", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 7. ACTIVITY FEED
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Security Events",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { onNavigateToTab(3) } // Navigate to Activity Log Tab
                ) {
                    Text("VIEW ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            ABXLogViewer(
                logs = logs.takeLast(3),
                onClear = null
            )
        }
    }
}
