package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SynologyScripts
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen
import com.example.util.SynologyZipExporter

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
    val context = LocalContext.current

    var ip by remember(nasConfig.ip) { mutableStateOf(nasConfig.ip) }
    var port by remember(nasConfig.port) { mutableStateOf(nasConfig.port.toString()) }
    var path by remember(nasConfig.path) { mutableStateOf(nasConfig.path) }
    var dbHost by remember(nasConfig.dbHost) { mutableStateOf(nasConfig.dbHost) }
    var dbPort by remember(nasConfig.dbPort) { mutableStateOf(nasConfig.dbPort.toString()) }
    var dbName by remember(nasConfig.dbName) { mutableStateOf(nasConfig.dbName) }
    var dbUser by remember(nasConfig.dbUser) { mutableStateOf(nasConfig.dbUser) }
    var dbPass by remember(nasConfig.dbPass) { mutableStateOf(nasConfig.dbPass) }
    var isPassVisible by remember { mutableStateOf(false) }

    var showConfigPhpModal by remember { mutableStateOf(false) }

    val currentGeneratedConfig = remember(nasConfig, ip, port, path, dbHost, dbPort, dbName, dbUser, dbPass) {
        val portInt = port.toIntOrNull() ?: 80
        val dbPortInt = dbPort.toIntOrNull() ?: 3307
        nasConfig.copy(
            ip = ip.trim(),
            port = portInt,
            path = path.trim(),
            dbHost = dbHost.trim(),
            dbPort = dbPortInt,
            dbName = dbName.trim(),
            dbUser = dbUser.trim(),
            dbPass = dbPass.trim()
        )
    }

    val configPhpCode = remember(currentGeneratedConfig) {
        SynologyScripts.generateConfigPhp(
            dbHost = currentGeneratedConfig.dbHost,
            dbPort = currentGeneratedConfig.dbPort,
            dbName = currentGeneratedConfig.dbName,
            dbUser = currentGeneratedConfig.dbUser,
            dbPass = currentGeneratedConfig.dbPass
        )
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val success = SynologyZipExporter.exportToUri(context, uri, currentGeneratedConfig)
            if (success) {
                Toast.makeText(context, "Archivo 'synology_family_config.zip' guardado exitosamente", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error al guardar el archivo ZIP", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
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
                        text = "Servidor Web Station (PHP 7.3 / 8)",
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
                        label = { Text("Puerto Web") },
                        placeholder = { Text("80 o 5000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("nas_port_input")
                    )

                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Ruta relativa API") },
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
                        placeholder = { Text("127.0.0.1") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )

                    OutlinedTextField(
                        value = dbPort,
                        onValueChange = { dbPort = it },
                        label = { Text("Puerto (3307)") },
                        placeholder = { Text("3307") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dbName,
                    onValueChange = { dbName = it },
                    label = { Text("Nombre de Base de Datos") },
                    placeholder = { Text("family_feed") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Usuario y Contraseña de MariaDB
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dbUser,
                        onValueChange = { dbUser = it },
                        label = { Text("Usuario MariaDB") },
                        placeholder = { Text("root") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = dbPass,
                        onValueChange = { dbPass = it },
                        label = { Text("Contraseña") },
                        placeholder = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPassVisible = !isPassVisible }) {
                                Icon(
                                    imageVector = if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPassVisible) "Ocultar" else "Mostrar"
                                )
                            }
                        },
                        visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        // SECCIÓN DEDICADA: Generador de config.php y Paquete ZIP para Synology
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FacebookBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = FacebookBlue)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Paquete de Instalación y config.php",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Descarga los archivos listos para subir al servidor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Explicación de seguridad APK -> PHP -> MariaDB
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FacebookBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "La APK se conecta a MariaDB a través del archivo index.php (Web Station). No requiere puertos abiertos a internet ni expone credenciales externas.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Botones para Ver/Copiar config.php y Descargar ZIP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showConfigPhpModal = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver config.php", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("config.php", configPhpCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "config.php copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Código", fontSize = 12.sp)
                    }
                }

                // Botón principal de Descarga directa de archivo ZIP
                Button(
                    onClick = {
                        createZipLauncher.launch("synology_family_config.zip")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Descargar Paquete ZIP para NAS", fontWeight = FontWeight.Bold)
                }

                // Botón alternativo para Compartir ZIP directamente a apps
                OutlinedButton(
                    onClick = {
                        val intent = SynologyZipExporter.shareZip(context, currentGeneratedConfig)
                        if (intent != null) {
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "No se pudo preparar el archivo ZIP para compartir", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir ZIP (WhatsApp, Drive, PC...)")
                }

                Text(
                    text = "Incluye: config.php ya configurado, index.php, schema.sql (8 tablas), .htaccess y guía de instalación.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Botones de Guardar y Probar Configuración
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onSaveConfig(currentGeneratedConfig)
                    Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
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

    // Modal de Inspección de Código config.php
    if (showConfigPhpModal) {
        AlertDialog(
            onDismissRequest = { showConfigPhpModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = FacebookBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vista Previa: config.php", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Este archivo se ubica en /web/family_api/config.php de tu Synology NAS:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = configPhpCode,
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("config.php", configPhpCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "config.php copiado", Toast.LENGTH_SHORT).show()
                        showConfigPhpModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar al Portapapeles")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigPhpModal = false }) {
                    Text("Cerrar")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
