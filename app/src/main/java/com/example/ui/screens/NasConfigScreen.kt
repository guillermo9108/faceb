package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen

@Composable
fun NasConfigScreen(
    nasConfig: NasConfig,
    onSaveConfig: (NasConfig) -> Unit,
    onTestConnection: () -> Unit,
    onRepairDatabase: () -> Unit,
    onOpenErrorLogs: () -> Unit,
    onRunDiagnostics: () -> Unit,
    errorCount: Int = 0,
    isRepairing: Boolean = false,
    isRunningDiagnostics: Boolean = false
) {
    var ip by remember(nasConfig.ip) { mutableStateOf(nasConfig.ip) }
    var port by remember(nasConfig.port) { mutableStateOf(nasConfig.port.toString()) }
    var path by remember(nasConfig.path) { mutableStateOf(nasConfig.path) }
    var dbHost by remember(nasConfig.dbHost) { mutableStateOf(nasConfig.dbHost) }
    var dbPort by remember(nasConfig.dbPort) { mutableStateOf(nasConfig.dbPort.toString()) }
    var dbName by remember(nasConfig.dbName) { mutableStateOf(nasConfig.dbName) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta de Estado de Conexión LAN
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (nasConfig.isConnected) LanOnlineGreen.copy(alpha = 0.12f) else LanOfflineAmber.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (nasConfig.isConnected) LanOnlineGreen else LanOfflineAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (nasConfig.isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (nasConfig.isConnected) "Conectado al Synology NAS" else "Desconectado del NAS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (nasConfig.isConnected) LanOnlineGreen else LanOfflineAmber
                    )
                    Text(
                        text = if (nasConfig.isConnected) {
                            "IP: ${nasConfig.ip}:${nasConfig.port} • Latencia: ${nasConfig.lastPingMs} ms"
                        } else {
                            nasConfig.lastError ?: "Comprueba la IP y que Web Station esté activo en la red local"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Parámetros de Red y Web Station
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = FacebookBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Servidor Web Station (PHP 7.3)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Dirección IP local del Synology NAS") },
                    placeholder = { Text("Ej: 192.168.43.101") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("nas_ip_input")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto") },
                        placeholder = { Text("80 u 8085") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("nas_port_input")
                    )

                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Ruta Web") },
                        placeholder = { Text("/family_api") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("nas_path_input")
                    )
                }
            }
        }

        // Parámetros de MariaDB 10
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = FacebookBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Base de Datos MariaDB 10",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dbHost,
                        onValueChange = { dbHost = it },
                        label = { Text("Host MariaDB") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )

                    OutlinedTextField(
                        value = dbPort,
                        onValueChange = { dbPort = it },
                        label = { Text("Puerto") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dbName,
                    onValueChange = { dbName = it },
                    label = { Text("Nombre de Base de Datos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Botones de Acción
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 80
                    val dbPortInt = dbPort.toIntOrNull() ?: 3307
                    onSaveConfig(
                        nasConfig.copy(
                            ip = ip.trim(),
                            port = portInt,
                            path = path.trim(),
                            dbHost = dbHost.trim(),
                            dbPort = dbPortInt,
                            dbName = dbName.trim()
                        )
                    )
                },
                modifier = Modifier.weight(1f).testTag("save_nas_config_button"),
                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar")
            }

            OutlinedButton(
                onClick = onTestConnection,
                modifier = Modifier.weight(1f).testTag("test_nas_connection_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Probar")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Diagnóstico y Reparación
        Text(
            text = "Herramientas de Mantenimiento",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onRunDiagnostics,
                enabled = !isRunningDiagnostics,
                modifier = Modifier.weight(1f).testTag("run_diagnostics_button")
            ) {
                if (isRunningDiagnostics) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Diagnosticar")
            }

            FilledTonalButton(
                onClick = onRepairDatabase,
                enabled = !isRepairing,
                modifier = Modifier.weight(1f).testTag("repair_db_button")
            ) {
                if (isRepairing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Build, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reparar BD")
            }
        }

        // Acceso al Registro de Errores
        OutlinedButton(
            onClick = onOpenErrorLogs,
            modifier = Modifier.fillMaxWidth().testTag("open_error_logs_button")
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = if (errorCount > 0) LanOfflineAmber else MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (errorCount > 0) "Registro de Errores ($errorCount)" else "Ver Registro de Errores")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
