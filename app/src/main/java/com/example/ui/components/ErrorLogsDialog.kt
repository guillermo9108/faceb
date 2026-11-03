package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.AppErrorLog
import com.example.model.ErrorCategory
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen

@Composable
fun ErrorLogsDialog(
    logs: List<AppErrorLog>,
    nasConfig: NasConfig,
    isRepairing: Boolean = false,
    onDismiss: () -> Unit,
    onClearLogs: () -> Unit,
    onRepairDatabase: () -> Unit,
    onExportLogs: () -> String
) {
    val context = LocalContext.current
    var selectedCategoryFilter by remember { mutableStateOf<ErrorCategory?>(null) }

    val filteredLogs = remember(logs, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) logs
        else logs.filter { it.category == selectedCategoryFilter }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("error_logs_dialog"),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                // 1. Header con botón cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Registro de Errores",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (logs.isNotEmpty()) Color(0xFFC62828) else LanOnlineGreen,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${logs.size}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Diagnóstico Synology Web Station (PHP 7.3) + MariaDB 10",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_error_logs_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Resumen del entorno y botones de acción rápida
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Servidor NAS:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${nasConfig.ip}:${nasConfig.port} (${nasConfig.path})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Base MariaDB 10:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${nasConfig.dbHost}:${nasConfig.dbPort} / ${nasConfig.dbName}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Botón Copiar Registro
                            FilledTonalButton(
                                onClick = {
                                    val text = onExportLogs()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Registro Diagnóstico Synology", text))
                                    Toast.makeText(context, "Registro copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("copy_logs_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar", fontSize = 11.sp)
                            }

                            // Botón Reparar MariaDB
                            Button(
                                onClick = onRepairDatabase,
                                enabled = !isRepairing,
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(36.dp)
                                    .testTag("dialog_repair_db_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                if (isRepairing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reparando...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reparar Tablas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Botón Limpiar Logs
                            OutlinedButton(
                                onClick = onClearLogs,
                                enabled = logs.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("clear_logs_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Limpiar", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Chips de Filtro por Categoría
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("Todos (${logs.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FacebookBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                    ErrorCategory.values().forEach { cat ->
                        val count = logs.count { it.category == cat }
                        if (count > 0 || cat == ErrorCategory.MARIADB || cat == ErrorCategory.SYNOLOGY_API) {
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                                label = { Text("${cat.badge} ${cat.displayName} ($count)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FacebookBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // 4. Lista de Errores o Estado Vacío
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = LanOnlineGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin errores registrados",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "La conexión con Synology Web Station (PHP 7.3) y MariaDB 10 está operando normalmente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("error_logs_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            ErrorLogItemCard(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorLogItemCard(log: AppErrorLog) {
    var expanded by remember { mutableStateOf(false) }

    val categoryColor = when (log.category) {
        ErrorCategory.MARIADB -> Color(0xFFC62828)
        ErrorCategory.SYNOLOGY_API -> Color(0xFFD84315)
        ErrorCategory.NETWORK -> LanOfflineAmber
        ErrorCategory.VIDEO_CHUNKING -> Color(0xFF6A1B9A)
        else -> Color(0xFF455A64)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = categoryColor.copy(alpha = 0.07f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Fila superior: Categoría, timestamp y código HTTP
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${log.category.badge} ${log.category.displayName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = categoryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (log.httpCode != null && log.httpCode > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = categoryColor
                    ) {
                        Text(
                            text = "HTTP ${log.httpCode}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Título
            Text(
                text = log.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )

            // Mensaje explicativo
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!log.endpoint.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ruta: ${log.endpoint}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Solución recomendada si existe
            if (!log.suggestedFix.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = FacebookBlue.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("💡", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log.suggestedFix,
                            style = MaterialTheme.typography.labelSmall.copy(color = FacebookBlue, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            // Botón expandir detalles técnicos
            if (!log.rawServerResponse.isNullOrBlank() || !log.stackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Ocultar detalles técnicos" else "Ver detalles técnicos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Detalles técnicos del servidor:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.rawServerResponse ?: log.stackTrace ?: "",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}
