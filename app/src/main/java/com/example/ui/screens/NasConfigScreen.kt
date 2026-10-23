package com.example.ui.screens

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.SynologyScripts
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FamilyBluePrimary
import com.example.ui.theme.LanOnlineGreen
import com.example.util.SynologyZipExporter

@Composable
fun NasConfigScreen(
    nasConfig: NasConfig,
    onSaveConfig: (ip: String, port: Int, path: String, dbPort: Int) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var ipInput by remember(nasConfig.ip) { mutableStateOf(nasConfig.ip) }
    var portInput by remember(nasConfig.port) { mutableStateOf(nasConfig.port.toString()) }
    var pathInput by remember(nasConfig.path) { mutableStateOf(nasConfig.path) }
    var dbPortInput by remember(nasConfig.dbPort) { mutableStateOf(nasConfig.dbPort.toString()) }

    var downloadStatusMsg by remember { mutableStateOf<String?>(null) }
    var selectedScriptTab by remember { mutableIntStateOf(0) }
    val scriptTitles = listOf(
        "schema.sql (Base de Datos)",
        "config.php (Conexión)",
        "index.php (API REST)",
        "Guía DSM 7/6"
    )

    val currentConfig = remember(ipInput, portInput, pathInput, dbPortInput, nasConfig) {
        nasConfig.copy(
            ip = ipInput.trim().ifEmpty { "192.168.1.100" },
            port = portInput.toIntOrNull() ?: 8085,
            path = pathInput.trim().ifEmpty { "/family_api" },
            dbPort = dbPortInput.toIntOrNull() ?: 3307
        )
    }

    // Launcher para guardar el archivo ZIP en el almacenamiento del dispositivo
    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val success = SynologyZipExporter.exportToUri(context, uri, currentConfig)
            if (success) {
                downloadStatusMsg = "¡Archivo ZIP descargado con éxito! Ya puedes transferirlo o descomprimirlo en tu Synology NAS."
                Toast.makeText(context, "Archivo ZIP descargado correctamente", Toast.LENGTH_SHORT).show()
            } else {
                downloadStatusMsg = "Error al escribir el archivo ZIP."
                Toast.makeText(context, "Error al generar archivo ZIP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .padding(bottom = 80.dp)
            .testTag("nas_config_screen")
    ) {
        // Status Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Synology NAS Web Station",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (nasConfig.isConnected) LanOnlineGreen else Color(0xFFE53935))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (nasConfig.isConnected) {
                                        "Conectado por LAN (${nasConfig.lastPingMs} ms)"
                                    } else {
                                        "Sin conexión activa (Modo sin conexión)"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (nasConfig.isConnected) LanOnlineGreen else Color(0xFFE53935),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onTestConnection,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probar", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Diagnostic parameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Servidor Web:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Web Station (Puerto ${currentConfig.port})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Column {
                        Text("Base de Datos:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("MariaDB/MySQL (Puerto ${currentConfig.dbPort})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Ruta en NAS:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("/volume1/web/family_api/", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Column {
                        Text("Modo de Red:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("100% LAN Local (Sin Internet)", style = MaterialTheme.typography.bodySmall.copy(color = FacebookBlue, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection & Database Settings Inputs
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ajustes de Conexión LAN y Base de Datos",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Configura la IP del NAS, el puerto de Web Station y el puerto de MariaDB/MySQL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Fila 1: IP del NAS y Puerto Web Station
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("IP Synology NAS") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Puerto Web") },
                        placeholder = { Text("8085") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fila 2: Puerto Base de Datos (MariaDB / MySQL) y Ruta API
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dbPortInput,
                        onValueChange = { dbPortInput = it },
                        label = { Text("Puerto BD") },
                        placeholder = { Text("3307") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = {
                            Text("3307 MariaDB 10 / 3306 MySQL", fontSize = 10.sp)
                        }
                    )

                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        label = { Text("Ruta relativa API") },
                        placeholder = { Text("/family_api") },
                        modifier = Modifier.weight(1.6f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val parsedPort = portInput.toIntOrNull() ?: 8085
                        val parsedDbPort = dbPortInput.toIntOrNull() ?: 3307
                        onSaveConfig(ipInput.trim(), parsedPort, pathInput.trim(), parsedDbPort)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Parámetros de Red y BD", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card de Descarga de Paquete de Configuración ZIP
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE7F3FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, tint = FacebookBlue)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Descargar Configuración (.ZIP)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Paquete completo listo para descomprimir en tu Synology NAS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lista de contenido del ZIP
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Contenido generado con tus parámetros (Puerto BD: ${currentConfig.dbPort}):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• schema.sql: Crea la BD 'family_feed' y tablas con MariaDB/MySQL", fontSize = 11.sp)
                        Text("• config.php: Conexión PDO con puerto ${currentConfig.dbPort} y codificación UTF-8", fontSize = 11.sp)
                        Text("• index.php: API REST con subida real de fotos/vídeos y miniaturas WebP", fontSize = 11.sp)
                        Text("• .htaccess: Ajuste de límites de subida hasta 512 MB para Web Station", fontSize = 11.sp)
                        Text("• docker-compose.yml: Alternativa si usas Docker / Container Manager", fontSize = 11.sp)
                        Text("• LEEME_INSTRUCCIONES.txt: Guía en español paso a paso para DSM 7/6", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botón Principal: Descargar ZIP al almacenamiento
                    Button(
                        onClick = {
                            createZipLauncher.launch("synology_family_nas_config.zip")
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Descargar ZIP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Botón Secundario: Compartir ZIP
                    OutlinedButton(
                        onClick = {
                            val shareIntent = SynologyZipExporter.shareZip(context, currentConfig)
                            if (shareIntent != null) {
                                context.startActivity(shareIntent)
                            } else {
                                Toast.makeText(context, "No se pudo compartir el archivo ZIP", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir", fontSize = 13.sp)
                    }
                }

                if (downloadStatusMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = LanOnlineGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LanOnlineGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = downloadStatusMsg ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(color = LanOnlineGreen, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Synology Backend Scripts Viewer
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = FacebookBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Visor de Scripts para el NAS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "Previsualiza o copia manualmente el código para tu Synology:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedScriptTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    scriptTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedScriptTab == index,
                            onClick = { selectedScriptTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedScriptTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contenido dinámico según el puerto de base de datos actual
                val contentToShow = when (selectedScriptTab) {
                    0 -> SynologyScripts.schemaSql
                    1 -> SynologyScripts.generateConfigPhp(
                        dbHost = currentConfig.dbHost,
                        dbPort = currentConfig.dbPort,
                        dbName = currentConfig.dbName
                    )
                    2 -> SynologyScripts.indexPhp
                    else -> """
                        PASOS RÁPIDOS EN SYNOLOGY DSM:
                        
                        1. Instala 'Web Station', 'PHP 8.2' y 'MariaDB 10' desde el Centro de Paquetes.
                        2. Abre 'phpMyAdmin' (o consola SSH a MariaDB en puerto ${currentConfig.dbPort}), crea la base de datos 'family_feed' y ejecuta el script 'schema.sql'.
                        3. En File Station, dentro de la carpeta compartida 'web', crea la subcarpeta 'family_api'.
                        4. Guarda los archivos 'index.php', 'config.php' y '.htaccess' allí (o usa el botón 'Descargar ZIP' de arriba).
                        5. Crea dentro de 'family_api' la carpeta 'uploads' y dale permisos de lectura/escritura (usuario 'http').
                        6. En Web Station, crea un 'Portal de servicio web' basado en puertos (puerto ${currentConfig.port}) que apunte a /web/family_api usando PHP 8.2.
                        7. ¡Listo! La app Android se conectará instantáneamente en tu Wi-Fi local.
                    """.trimIndent()
                }

                SelectionContainer {
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        Text(
                            text = contentToShow,
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}
