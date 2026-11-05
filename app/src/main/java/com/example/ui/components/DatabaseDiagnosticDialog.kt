package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CheckItem
import com.example.model.DatabaseDiagnosticReport
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOnlineGreen

@Composable
fun DatabaseDiagnosticDialog(
    report: DatabaseDiagnosticReport,
    isRunning: Boolean = false,
    onRerunDiagnostics: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("database_diagnostic_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (report.mariadbAuthentication.success) LanOnlineGreen else Color(0xFFC62828)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Diagnóstico en Vivo de MariaDB 10",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (report.timestamp.isNotBlank()) "Ejecutado: ${report.timestamp}" else "Servidor Synology Web Station",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onRerunDiagnostics,
                            enabled = !isRunning,
                            modifier = Modifier.testTag("rerun_diagnostics_button")
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Volver a diagnosticar", tint = FacebookBlue)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tarjeta de Configuración Detectada
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Parámetros Evaluados en config.php",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Host & Puerto:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${report.dbHost}:${report.dbPort}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("Usuario BD:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(report.dbUser, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("Base de Datos:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(report.dbName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (report.dbPassIsDefault) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = Color(0xFFFFF3E0),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Aviso: DB_PASS en config.php aún tiene la clave genérica 'TuClaveSynology123'. Debes editarla con tu contraseña real.",
                                                fontSize = 11.sp,
                                                color = Color(0xFFE65100),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Checklist de Pruebas
                    item {
                        Text(
                            text = "Resultados de Pruebas Técnicas",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    item {
                        DiagnosticCheckRow(
                            title = "1. Extensión PDO MySQL en PHP 7.3",
                            item = report.pdoMysqlExtension,
                            successColor = LanOnlineGreen
                        )
                    }

                    item {
                        DiagnosticCheckRow(
                            title = "2. Conectividad TCP a ${report.dbHost}:${report.dbPort}",
                            item = report.tcpConnection,
                            successColor = LanOnlineGreen
                        )
                    }

                    item {
                        DiagnosticCheckRow(
                            title = "3. Sockets UNIX Locales (mysqld10.sock)",
                            item = report.unixSockets,
                            successColor = LanOnlineGreen
                        )
                    }

                    item {
                        DiagnosticCheckRow(
                            title = "4. Autenticación MariaDB 10 (${report.dbUser}@${report.dbHost}:${report.dbPort})",
                            item = report.mariadbAuthentication,
                            successColor = LanOnlineGreen
                        )
                    }

                    if (report.databaseExists) {
                        item {
                            DiagnosticCheckRow(
                                title = "5. Base de Datos '${report.dbName}' y Tablas",
                                item = CheckItem(
                                    success = report.tablesCount > 0,
                                    message = "Base de datos sincronizada. Tablas listas: ${report.tablesCount}"
                                ),
                                successColor = LanOnlineGreen
                            )
                        }
                    }

                    // Tarjeta de Recomendación y Acción en Synology DSM
                    if (report.recommendation.isNotBlank()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (report.mariadbAuthentication.success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (report.mariadbAuthentication.success) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (report.mariadbAuthentication.success) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (report.mariadbAuthentication.success) "Estado Operativo" else "Solución Recomendada para Synology DSM",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (report.mariadbAuthentication.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = report.recommendation,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            color = if (report.mariadbAuthentication.success) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val summary = buildString {
                                appendLine("=== INFORME DE DIAGNÓSTICO MARIADB 10 (SYNOLOGY NAS) ===")
                                appendLine("Fecha: ${report.timestamp}")
                                appendLine("PHP: ${report.phpVersion}")
                                appendLine("Config: ${report.dbUser}@${report.dbHost}:${report.dbPort} -> DB: ${report.dbName}")
                                appendLine("PDO MySQL: ${report.pdoMysqlExtension.message}")
                                appendLine("TCP 3307: ${report.tcpConnection.message}")
                                appendLine("Sockets: ${report.unixSockets.message}")
                                appendLine("Autenticación: ${report.mariadbAuthentication.message}")
                                appendLine("Tablas: ${report.tablesCount}")
                                appendLine("Recomendación: ${report.recommendation}")
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico MariaDB", summary))
                            Toast.makeText(context, "Informe de diagnóstico copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("copy_diagnostic_summary_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Informe", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).testTag("close_diagnostic_dialog_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                    ) {
                        Text("Entendido / Cerrar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCheckRow(
    title: String,
    item: CheckItem,
    successColor: Color
) {
    Surface(
        color = if (item.success) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color(0xFFFFEBEE).copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (item.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (item.success) successColor else Color(0xFFC62828),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (item.success) MaterialTheme.colorScheme.onSurface else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (!item.detail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
