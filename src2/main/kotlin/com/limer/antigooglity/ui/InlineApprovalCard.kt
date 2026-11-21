package com.limer.antigooglity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limer.antigooglity.coordinator.ApprovalDecision
import com.limer.antigooglity.coordinator.UiApprovalRequest

@Composable
fun InlineApprovalCard(
    request: UiApprovalRequest,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceBg,
        border = BorderStroke(
            1.dp,
            when (request.decision) {
                ApprovalDecision.PENDING -> WarningAmber.copy(alpha = 0.5f)
                ApprovalDecision.APPROVED -> SuccessGreen.copy(alpha = 0.4f)
                ApprovalDecision.REJECTED -> DangerRed.copy(alpha = 0.4f)
                ApprovalDecision.CANCELLED -> BorderColor
            }
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Vector Shield Alert Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(WarningAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AntigravityIcons.ShieldAlert(
                        modifier = Modifier.size(14.dp),
                        color = WarningAmber,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Security Confirmation Required",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "The agent is requesting to execute a terminal command:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Command Spec Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Background,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = request.command.command,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AccentBlue,
                    )
                    if (request.command.args.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = request.command.args.joinToString(" "),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Working dir: ${request.command.workingDirectory}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions or Decision Status
            when (request.decision) {
                ApprovalDecision.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onReject(request.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text("Reject", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onApprove(request.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text("Allow", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
                ApprovalDecision.APPROVED -> {
                    Row(
                        modifier = Modifier
                            .background(SuccessGreen.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AntigravityIcons.Checkmark(modifier = Modifier.size(12.dp), color = SuccessGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Command execution allowed by user",
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                ApprovalDecision.REJECTED -> {
                    Row(
                        modifier = Modifier
                            .background(DangerRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✕ Command execution rejected by user",
                            color = DangerRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                ApprovalDecision.CANCELLED -> {
                    Row(
                        modifier = Modifier
                            .background(SurfaceElevated, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "⏹ Request cancelled",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
