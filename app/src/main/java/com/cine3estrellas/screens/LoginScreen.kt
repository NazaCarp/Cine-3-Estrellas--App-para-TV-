package com.cine3estrellas.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cine3estrellas.R
import com.cine3estrellas.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var telegramId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Dynamic resource lookup to prevent NoSuchFieldError in Preview
    val loginBgResId = remember(context) {
        val id = context.resources.getIdentifier("login_bg", "drawable", context.packageName)
        if (id != 0) id else 0
    }

    val textFieldFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    val shakeOffset = remember { Animatable(0f) }
    val errorAlpha by animateFloatAsState(if (errorMessage != null) 1f else 0f)

    LaunchedEffect(Unit) {
        if (DataCache.savedIdForLogin != null) {
            telegramId = DataCache.savedIdForLogin!!
            errorMessage = "Tu sesión ha expirado o ya no eres miembro del grupo."
            DataCache.savedIdForLogin = null
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Focus the "INGRESAR" button if the error is related to group membership
            if (errorMessage!!.contains("@Cine_3Estrellas")) {
                buttonFocusRequester.requestFocus()
            }

            repeat(3) {
                shakeOffset.animateTo(10f, animationSpec = tween(50))
                shakeOffset.animateTo(-10f, animationSpec = tween(50))
            }
            shakeOffset.animateTo(0f, animationSpec = tween(50))
            kotlinx.coroutines.delay(4000)
            errorMessage = null
        }
    }

    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
    }
// ... (rest of the code unchanged until login logic)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Background Cinematic Image (Blurred)
        if (loginBgResId != 0) {
            Image(
                painter = painterResource(id = loginBgResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(20.dp).alpha(0.3f)
            )
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 50.dp, vertical = 24.dp)
                .imePadding()
                .verticalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Instructions
            Column(
                modifier = Modifier.weight(1.8f).padding(end = 40.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // APP LOGO AND BRAND TEXT
                Row(verticalAlignment = Alignment.Bottom) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo Icon",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Image(
                        painter = painterResource(id = R.drawable.brand_text),
                        contentDescription = "Logo Text",
                        modifier = Modifier
                            .height(60.dp)
                            .padding(bottom = 0.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column {
                    Text(
                        text = "BIENVENIDO A LA",
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "EXPERIENCIA PREMIUM",
                        style = TextStyle(
                            fontSize = 32.sp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White, Color.White.copy(alpha = 0.4f))
                            ),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.2.em
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Vertical Stepper Design
                Column(
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    InstructionStepModern(1, "Escanea el código QR o busca @Cine_3Estrellas_bot en Telegram.", isLast = false)
                    InstructionStepModern(2, "Inicia el bot y únete a nuestro grupo oficial.", isLast = false)
                    InstructionStepModern(3, "El bot te dará tu Código de Acceso. Ingrésalo a la derecha.", isLast = true)
                }
            }

            // Right Side: Login Form
            Box(
                modifier = Modifier
                    .offset(x = shakeOffset.value.dp)
                    .width(320.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF141416).copy(alpha = 0.95f))
                    .border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            listOf(
                                if (errorMessage != null) Color(0xFFFF5252).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "INGRESO DE USUARIO",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (errorMessage != null) Color(0xFFFF5252).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // NEW QR CODE IMAGE
                    Image(
                        painter = painterResource(id = R.drawable.qr_login),
                        contentDescription = "QR Login",
                        modifier = Modifier
                            .size(170.dp)
                            .graphicsLayer {
                                if (errorMessage != null) {
                                    val scale = 1f - (errorAlpha * 0.05f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                            },
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Access Code Input
                    androidx.compose.material3.OutlinedTextField(
                        value = telegramId,
                        onValueChange = { input -> if (input.all { char -> char.isDigit() }) telegramId = input },
                        label = { androidx.compose.material3.Text("Código de Acceso") },
                        isError = errorMessage != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.7f),
                            focusedBorderColor = if (errorMessage != null) Color(0xFFFF5252) else Gold,
                            unfocusedBorderColor = if (errorMessage != null) Color(0xFFFF5252).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                            errorBorderColor = Color(0xFFFF5252),
                            focusedLabelColor = if (errorMessage != null) Color(0xFFFF5252) else Gold,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Gold
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 2.sp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Login Button
                    val buttonScale by animateFloatAsState(if (isLoading) 0.95f else 1f)

                    Surface(
                        onClick = {
                            if (telegramId.isNotBlank()) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val user = SupabaseManager.findUserByAuthCode(telegramId)
                                    if (user != null) {
                                        if (!SupabaseManager.isCodeExpired(user.authCodeExpiresAt)) {
                                            val isMember = TelegramManager.checkGroupMembership(user.telegramId.toString())
                                            if (isMember) {
                                                // Register/fetch user in Supabase
                                                val savedUser = SupabaseManager.syncUser(context, user)
                                                if (savedUser != null) {
                                                    // Clear the code after successful login
                                                    SupabaseManager.clearAuthCode(user.telegramId)
                                                    
                                                    DataCache.saveSession(context, savedUser)
                                                    onLoginSuccess()
                                                } else {
                                                    errorMessage = "Error al sincronizar datos del servidor."
                                                }
                                            } else {
                                                errorMessage = "Debes unirte al grupo @Cine_3Estrellas para ingresar."
                                            }
                                        } else {
                                            errorMessage = "El código ha expirado. Genera uno nuevo en el bot."
                                        }
                                    } else {
                                        errorMessage = "Código de acceso inválido."
                                    }
                                    isLoading = false
                                }
                            } else {
                                errorMessage = "Por favor, ingresa tu Código de Acceso."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .focusRequester(buttonFocusRequester)
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (errorMessage != null) Color(0xFFFF5252).copy(alpha = 0.2f) else Gold,
                            focusedContainerColor = if (errorMessage != null) Color(0xFFFF5252) else Color.White,
                            contentColor = if (errorMessage != null) Color.White else Color.Black,
                            focusedContentColor = Color.Black
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                        glow = ClickableSurfaceDefaults.glow(
                            focusedGlow = Glow(if (errorMessage != null) Color(0xFFFF5252).copy(alpha = 0.5f) else Gold.copy(alpha = 0.5f), 20.dp)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isLoading) {
                                androidx.compose.material3.CircularProgressIndicator(color = if (errorMessage != null) Color.White else Color.Black, modifier = Modifier.size(24.dp))
                            } else {
                                Text("INGRESAR", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "¿No tienes tu código? Envía /start al bot para obtenerlo al instante.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Floating Error Notification
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = SurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A).copy(alpha = 0.9f)),
                border = Border(BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))),
                glow = Glow(Color(0xFFFF5252).copy(alpha = 0.2f), 15.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).background(Color(0xFFFF5252), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun InstructionStepModern(number: Int, text: String, isLast: Boolean) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Intensified Glow effect (Halo)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Gold.copy(alpha = 0.5f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Gold.copy(alpha = 0.7f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )

                // The solid gold circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Gold, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier.weight(1f).width(12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Glow for the vertical line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .blur(4.dp)
                            .background(Gold.copy(alpha = 0.3f))
                    )
                    // Main solid vertical line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Gold, Gold, Gold.copy(alpha = 0.3f))
                                )
                            )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Clean glass card with a subtle top-left highlight
        Box(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.2f),
                        0.4f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun LoginScreenPreview() {
    Cine3EstrellasTheme {
        LoginScreen(onLoginSuccess = {})
    }
}
