package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.ui.theme.FacebookBlue

@Composable
fun LoginRegisterScreen(
    members: List<FamilyMember>,
    nasIp: String,
    onLogin: (username: String, pass: String, remember: Boolean) -> Unit,
    onRegister: (username: String, nick: String, pass: String, emoji: String, colorHex: Long, remember: Boolean) -> Unit,
    onSkipLogin: () -> Unit,
    onOpenNasConfig: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Login, 1: Register

    // Login state
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginRememberSession by remember { mutableStateOf(true) }
    var isLoginPassVisible by remember { mutableStateOf(false) }

    // Register state
    var regUsername by remember { mutableStateOf("") }
    var regNick by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regRememberSession by remember { mutableStateOf(true) }
    var isRegPassVisible by remember { mutableStateOf(false) }
    var regEmoji by remember { mutableStateOf("👨‍💼") }
    var regColorHex by remember { mutableLongStateOf(0xFF1877F2L) }

    val emojiChoices = listOf(
        "👨‍💼", "👩‍🌾", "👧", "👦", "👵", "👴",
        "🧑‍🍳", "🎸", "⚽", "🌸", "🦁", "🚀",
        "🐱", "🐶", "🍕", "🎨", "💻", "🏖️"
    )

    val colorChoices = listOf(
        0xFF1877F2L, 0xFF42B72AL, 0xFFF7B125L,
        0xFFE4405FL, 0xFF833AB4L, 0xFF00A884L, 0xFF6C757DL
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Logo & Title
            Text(
                text = "familiar",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    color = FacebookBlue,
                    letterSpacing = (-1.5).sp
                )
            )

            Text(
                text = "Tu Muro Familiar Privado en Red LAN",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // NAS connection badge
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable { onOpenNasConfig() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = FacebookBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synology NAS: $nasIp (Puerto 80)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Auth Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_container_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = FacebookBlue,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    "Iniciar Sesión",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.testTag("login_tab")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    "Crear Cuenta",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.testTag("register_tab")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedTab == 0) {
                        // --- LOGIN FORM ---
                        Text(
                            text = "Usuario o Nombre",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = loginUsername,
                            onValueChange = { loginUsername = it },
                            placeholder = { Text("Ej: admin (o tu usuario)") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = FacebookBlue)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Contraseña o PIN",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            placeholder = { Text("Contraseña (admin por defecto)") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = FacebookBlue)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isLoginPassVisible = !isLoginPassVisible }) {
                                    Icon(
                                        imageVector = if (isLoginPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isLoginPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Recordar Sesión Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { loginRememberSession = !loginRememberSession }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = loginRememberSession,
                                onCheckedChange = { loginRememberSession = it },
                                colors = CheckboxDefaults.colors(checkedColor = FacebookBlue),
                                modifier = Modifier.testTag("remember_session_checkbox")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Recordar sesión",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "No volver a pedir contraseña al abrir",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Submit Login Button
                        Button(
                            onClick = {
                                onLogin(loginUsername.trim(), loginPassword, loginRememberSession)
                            },
                            enabled = loginUsername.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("login_submit_button")
                        ) {
                            Text(
                                text = "Entrar al Muro Familiar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    } else {
                        // --- REGISTER FORM ---
                        Text(
                            text = "Usuario (identificador para entrar)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = regUsername,
                            onValueChange = { regUsername = it },
                            placeholder = { Text("Ej: lucia2") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_username_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Nombre o Nick visible",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = regNick,
                            onValueChange = { regNick = it },
                            placeholder = { Text("Ej: Lucía 💕") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_nick_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Contraseña",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            placeholder = { Text("Contraseña o PIN") },
                            visualTransformation = if (isRegPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isRegPassVisible = !isRegPassVisible }) {
                                    Icon(
                                        imageVector = if (isRegPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Avatar Preview & Emojis
                        Text(
                            text = "Elige tu Avatar Familiar",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(regColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = regEmoji, fontSize = 26.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(emojiChoices) { emoji ->
                                    val isSelected = emoji == regEmoji
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) FacebookBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) FacebookBlue else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { regEmoji = emoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Color picker
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colorChoices) { colorHex ->
                                val isSelected = colorHex == regColorHex
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorHex))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { regColorHex = colorHex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { regRememberSession = !regRememberSession }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = regRememberSession,
                                onCheckedChange = { regRememberSession = it },
                                colors = CheckboxDefaults.colors(checkedColor = FacebookBlue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recordar sesión en este dispositivo",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                onRegister(
                                    regUsername.trim(),
                                    regNick.trim().ifBlank { regUsername.trim() },
                                    regPassword,
                                    regEmoji,
                                    regColorHex,
                                    regRememberSession
                                )
                            },
                            enabled = regUsername.isNotBlank() && regPassword.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("register_submit_button")
                        ) {
                            Text(
                                text = "Registrarme en la Familia",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Guest / Quick LAN skip button
            OutlinedButton(
                onClick = onSkipLogin,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("skip_login_button")
            ) {
                Text(
                    text = "Continuar como invitado (Acceso libre LAN)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
