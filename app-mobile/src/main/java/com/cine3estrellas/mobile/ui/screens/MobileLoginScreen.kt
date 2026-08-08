package com.cine3estrellas.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cine3estrellas.*
import com.cine3estrellas.mobile.Gold
import com.cine3estrellas.mobile.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MobileLoginScreen(onLoginSuccess: () -> Unit) {
    var authCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            repeat(3) {
                shakeOffset.animateTo(10f, animationSpec = tween(50))
                shakeOffset.animateTo(-10f, animationSpec = tween(50))
            }
            shakeOffset.animateTo(0f, animationSpec = tween(50))
            delay(4000)
            errorMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Cinematic Background
        Image(
            painter = painterResource(id = R.drawable.login_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(15.dp).alpha(0.4f)
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo and Brand
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                painter = painterResource(id = R.drawable.brand_text),
                contentDescription = "Cine 3 Estrellas",
                modifier = Modifier.height(40.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = shakeOffset.value.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416).copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "INGRESO DE USUARIO",
                        style = MaterialTheme.typography.titleMedium,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Instruction Steps
                    InstructionStep(1, "Busca @Cine_3Estrellas_bot en Telegram")
                    InstructionStep(2, "Envía /start para recibir tu código")
                    InstructionStep(3, "Ingresa el código aquí abajo")

                    Spacer(modifier = Modifier.height(32.dp))

                    // Input Field
                    OutlinedTextField(
                        value = authCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) authCode = it },
                        label = { Text("Código de Acceso") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Gold,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons
                    Button(
                        onClick = {
                            if (authCode.length == 6) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val user = SupabaseManager.findUserByAuthCode(authCode)
                                        if (user != null) {
                                            if (!SupabaseManager.isCodeExpired(user.authCodeExpiresAt)) {
                                                val syncedUser = SupabaseManager.syncUser(context, user, "Mobile (Android)")
                                                if (syncedUser != null) {
                                                    DataCache.saveSession(context, syncedUser)
                                                    onLoginSuccess()
                                                } else {
                                                    errorMessage = "Error al sincronizar sesión"
                                                }
                                            } else {
                                                errorMessage = "El código ha expirado"
                                            }
                                        } else {
                                            errorMessage = "Código inválido"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Error de conexión"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                errorMessage = "Ingresa los 6 dígitos"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text("INGRESAR", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Cine_3Estrellas_bot"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = Gold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ABRIR TELEGRAM", color = Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "El código es gratuito y se genera al instante.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }

        // Error Notification
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFB00020),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = errorMessage ?: "",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun InstructionStep(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = Gold,
            contentColor = Color.Black
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = number.toString(), fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}
