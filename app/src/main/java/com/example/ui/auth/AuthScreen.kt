package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.AccountSessionManager
import com.example.data.model.hashEmailPassword
import com.example.data.repository.SmartChatRepository
import com.example.util.AudioHelper
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Exact HTML CSS color tokens
private val HtmlBg = Color(0xFF0B0D12)
private val HtmlPanel = Color(0xFF12151D)
private val HtmlField = Color(0xFF191D27)
private val HtmlText = Color(0xFFF4F5F9)
private val HtmlMuted = Color(0xFF939BAD)
private val HtmlAccent = Color(0xFFA78BFA)
private val HtmlBorder = Color(0xFF292E3B)
private val HtmlLogoBg = Color(0xFF242034)
private val HtmlLogoBorder = Color(0xFF48405E)
private val HtmlTabsBg = Color(0xFF0C0F15)
private val HtmlActiveTabBg = Color(0xFF29243B)
private val HtmlActiveTabText = Color(0xFFD7C8FF)
private val HtmlLabel = Color(0xFFDCE0E9)
private val HtmlPlaceholder = Color(0xFF70798C)
private val HtmlAvatarBorder = Color(0xFF414657)
private val HtmlAvatarLabel = Color(0xFFC6B5FF)
private val HtmlPrimaryBorder = Color(0xFFC4B5FD)
private val HtmlPrimaryText = Color(0xFF171123)
private val HtmlErrorText = Color(0xFFFFAAAA)
private val HtmlSuccessText = Color(0xFFA7F3D0)
private val HtmlFooterText = Color(0xFF727B8E)

data class OnboardingPreferences(
    val username: String = "",
    val avatarData: String = "avatar_indigo",
    val statusText: String = "SMART CHAT মেম্বার",
    val autoDeleteHours: Long = 24L
)

val PRESET_AVATARS = listOf(
    "avatar_indigo" to Color(0xFFA78BFA),
    "avatar_emerald" to Color(0xFF10B981),
    "avatar_cyan" to Color(0xFF06B6D4),
    "avatar_amber" to Color(0xFFF59E0B),
    "avatar_rose" to Color(0xFFF43F5E)
)

private const val ACCOUNT_PREFS_NAME = "smart_chat_account_prefs"

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    AccountSessionManager.init(context)
    if (AccountSessionManager.currentSession != null || Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Authentication failed")
        }
    }
}

fun signOut(
    context: Context,
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    AccountSessionManager.clearSession(context)
    Firebase.auth.signOut()
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}

@Composable
fun DefaultPurpleAvatarSvg(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(HtmlLogoBg)) {
        val w = size.width
        val h = size.height
        // Matches SVG: <circle cx='50' cy='36' r='18' fill='#a78bfa'/>
        drawCircle(
            color = HtmlAccent,
            radius = w * 0.18f,
            center = Offset(w * 0.50f, h * 0.36f)
        )
        // Matches SVG: <path d='M16 95 Q18 60 50 60 Q82 60 84 95' fill='#a78bfa'/>
        val bodyPath = Path().apply {
            moveTo(w * 0.16f, h * 0.95f)
            quadraticTo(w * 0.18f, h * 0.60f, w * 0.50f, h * 0.60f)
            quadraticTo(w * 0.82f, h * 0.60f, w * 0.84f, h * 0.95f)
            close()
        }
        drawPath(path = bodyPath, color = HtmlAccent)
    }
}

@Composable
fun UserAvatarView(
    avatarData: String,
    username: String,
    sizeDp: Int = 44,
    isOnline: Boolean = false
) {
    val bitmap = remember(avatarData) { AudioHelper.decodeBase64Bitmap(avatarData) }
    val presetColor = PRESET_AVATARS.find { it.first == avatarData }?.second
    val initial = username.trim().removePrefix("@").firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    Box(modifier = Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$username profile picture",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, HtmlAvatarBorder, CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (presetColor != null && avatarData != "avatar_indigo") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(presetColor)
                    .border(1.dp, HtmlAvatarBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = HtmlPrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (sizeDp * 0.42f).sp
                )
            }
        } else {
            DefaultPurpleAvatarSvg(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, HtmlAvatarBorder, CircleShape)
            )
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size((sizeDp * 0.28f).coerceAtLeast(10f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .border(2.dp, HtmlPanel, CircleShape)
            )
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: (OnboardingPreferences) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { SmartChatRepository(context) }
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember(context) {
        context.getSharedPreferences(ACCOUNT_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isSignupMode by rememberSaveable { mutableStateOf(true) }
    var fullName by rememberSaveable { mutableStateOf(sharedPrefs.getString("full_name", "") ?: "") }
    var username by rememberSaveable { mutableStateOf(sharedPrefs.getString("username", "") ?: "") }
    var signupEmail by rememberSaveable { mutableStateOf(sharedPrefs.getString("email", "") ?: "") }
    var signupPassword by rememberSaveable { mutableStateOf("") }

    var loginEmail by rememberSaveable { mutableStateOf(sharedPrefs.getString("email", "") ?: "") }
    var loginPassword by rememberSaveable { mutableStateOf("") }

    var avatarData by rememberSaveable {
        mutableStateOf(sharedPrefs.getString("avatar_data", "avatar_indigo") ?: "avatar_indigo")
    }
    var autoDeleteHours by rememberSaveable { mutableLongStateOf(24L) }

    var isLoading by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = AudioHelper.compressGalleryImageToBase64(context, uri)
            if (encoded != null) {
                avatarData = encoded
                messageText = null
            } else {
                isSuccessMessage = false
                messageText = "Please choose a valid JPG, PNG, or WebP image."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HtmlBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // .container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = HtmlPanel,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, HtmlBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // .brand
                Box(
                    modifier = Modifier
                        .size(55.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HtmlLogoBg)
                        .border(1.dp, HtmlLogoBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✦",
                        fontSize = 25.sp,
                        color = HtmlAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = "Welcome",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = HtmlText,
                    letterSpacing = (-0.7).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create your account or sign in to continue.",
                    fontSize = 13.sp,
                    color = HtmlMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(26.dp))

                // .tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(HtmlTabsBg)
                        .border(1.dp, HtmlBorder, RoundedCornerShape(11.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSignupMode) HtmlActiveTabBg else Color.Transparent)
                            .clickable {
                                isSignupMode = true
                                messageText = null
                            }
                            .padding(vertical = 11.dp)
                            .testTag("signup_tab_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign Up",
                            fontSize = 14.sp,
                            fontWeight = if (isSignupMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSignupMode) HtmlActiveTabText else HtmlMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isSignupMode) HtmlActiveTabBg else Color.Transparent)
                            .clickable {
                                isSignupMode = false
                                messageText = null
                            }
                            .padding(vertical = 11.dp)
                            .testTag("login_tab_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Login",
                            fontSize = 14.sp,
                            fontWeight = if (!isSignupMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (!isSignupMode) HtmlActiveTabText else HtmlMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(23.dp))

                // #message
                if (!messageText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(HtmlField)
                            .border(1.dp, HtmlBorder, RoundedCornerShape(9.dp))
                            .padding(12.dp)
                            .testTag("auth_message_box")
                    ) {
                        Text(
                            text = messageText!!,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = if (isSuccessMessage) HtmlSuccessText else HtmlErrorText
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isSignupMode) {
                    // #signupForm
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // .avatar-wrap
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("pick_photo_avatar")
                        ) {
                            UserAvatarView(
                                avatarData = avatarData,
                                username = fullName.ifBlank { username.ifBlank { "U" } },
                                sizeDp = 82
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Choose profile picture",
                            color = HtmlAvatarLabel,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                                .testTag("pick_photo_button")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Full Name
                        HtmlFormField(
                            label = "Full Name",
                            value = fullName,
                            onValueChange = { if (it.length <= 60) fullName = it },
                            placeholder = "Enter your name",
                            testTag = "full_name_input"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Username
                        HtmlFormField(
                            label = "Username",
                            value = username,
                            onValueChange = { input ->
                                val cleaned = input.filter { it.isLetterOrDigit() || it == '_' || it == '@' }
                                if (cleaned.length <= 24) username = cleaned
                            },
                            placeholder = "@username",
                            testTag = "username_input"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Email Address
                        HtmlFormField(
                            label = "Email Address",
                            value = signupEmail,
                            onValueChange = { signupEmail = it.trim() },
                            placeholder = "you@example.com",
                            keyboardType = KeyboardType.Email,
                            testTag = "signup_email_input"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password
                        HtmlFormField(
                            label = "Password",
                            value = signupPassword,
                            onValueChange = { signupPassword = it },
                            placeholder = "At least 8 characters",
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            testTag = "signup_password_input"
                        )

                        Spacer(modifier = Modifier.height(21.dp))

                        // Primary Button: Create Account (Direct Email/Password — NO Google popup)
                        Button(
                            onClick = {
                                val trimmedName = fullName.trim()
                                val cleanUser = username.trim().removePrefix("@")
                                val trimmedEmail = signupEmail.trim().lowercase()

                                when {
                                    trimmedName.isEmpty() -> {
                                        isSuccessMessage = false
                                        messageText = "Please enter your full name."
                                    }
                                    cleanUser.length < 3 || !cleanUser.all { it.isLetterOrDigit() || it == '_' } -> {
                                        isSuccessMessage = false
                                        messageText = "Username must be 3-24 characters (letters, numbers, or _)."
                                    }
                                    !trimmedEmail.contains("@") || !trimmedEmail.contains(".") -> {
                                        isSuccessMessage = false
                                        messageText = "Please enter a valid email address."
                                    }
                                    signupPassword.length < 8 -> {
                                        isSuccessMessage = false
                                        messageText = "Password must be at least 8 characters."
                                    }
                                    else -> {
                                        messageText = null
                                        isLoading = true
                                        val passHash = hashEmailPassword(trimmedEmail, signupPassword)
                                        scope.launch {
                                            val result = repository.registerEmailAccount(
                                                fullName = trimmedName,
                                                usernameHandle = cleanUser,
                                                email = trimmedEmail,
                                                passwordHash = passHash,
                                                avatarData = avatarData,
                                                autoDeleteHours = autoDeleteHours
                                            )
                                            isLoading = false
                                            result.onSuccess { session ->
                                                AccountSessionManager.saveSession(context, session, passHash)
                                                onAuthSuccess(
                                                    OnboardingPreferences(
                                                        username = session.fullName,
                                                        avatarData = session.avatarData,
                                                        statusText = "@${session.username}",
                                                        autoDeleteHours = session.autoDeleteHours
                                                    )
                                                )
                                            }.onFailure { err ->
                                                isSuccessMessage = false
                                                messageText = err.localizedMessage ?: "Account creation failed. Please try again."
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_account_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HtmlAccent,
                                contentColor = HtmlPrimaryText,
                                disabledContainerColor = HtmlAccent.copy(alpha = 0.6f),
                                disabledContentColor = HtmlPrimaryText.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, HtmlPrimaryBorder),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = HtmlPrimaryText,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Create Account",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Already have an account? ",
                                fontSize = 13.sp,
                                color = HtmlMuted
                            )
                            Text(
                                text = "Login",
                                fontSize = 13.sp,
                                color = HtmlPrimaryBorder,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        isSignupMode = false
                                        messageText = null
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .testTag("switch_to_login_button")
                            )
                        }
                    }
                } else {
                    // #loginForm
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Email Address
                        HtmlFormField(
                            label = "Email Address",
                            value = loginEmail,
                            onValueChange = { loginEmail = it.trim() },
                            placeholder = "you@example.com",
                            keyboardType = KeyboardType.Email,
                            testTag = "login_email_input"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password
                        HtmlFormField(
                            label = "Password",
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            placeholder = "Enter your password",
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            testTag = "login_password_input"
                        )

                        Spacer(modifier = Modifier.height(21.dp))

                        // Primary Button: Login to Account (Direct Email/Password — NO Google popup)
                        Button(
                            onClick = {
                                val trimmedEmail = loginEmail.trim().lowercase()
                                when {
                                    !trimmedEmail.contains("@") || !trimmedEmail.contains(".") -> {
                                        isSuccessMessage = false
                                        messageText = "Please enter your email address."
                                    }
                                    loginPassword.isEmpty() -> {
                                        isSuccessMessage = false
                                        messageText = "Please enter your password."
                                    }
                                    else -> {
                                        messageText = null
                                        isLoading = true
                                        val passHash = hashEmailPassword(trimmedEmail, loginPassword)
                                        scope.launch {
                                            val result = repository.loginEmailAccount(
                                                email = trimmedEmail,
                                                passwordHash = passHash
                                            )
                                            isLoading = false
                                            result.onSuccess { session ->
                                                AccountSessionManager.saveSession(context, session, passHash)
                                                onAuthSuccess(
                                                    OnboardingPreferences(
                                                        username = session.fullName,
                                                        avatarData = session.avatarData,
                                                        statusText = "@${session.username}",
                                                        autoDeleteHours = session.autoDeleteHours
                                                    )
                                                )
                                            }.onFailure { err ->
                                                isSuccessMessage = false
                                                messageText = err.localizedMessage ?: "Login failed. Please check your email and password."
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("login_account_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HtmlAccent,
                                contentColor = HtmlPrimaryText,
                                disabledContainerColor = HtmlAccent.copy(alpha = 0.6f),
                                disabledContentColor = HtmlPrimaryText.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, HtmlPrimaryBorder),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = HtmlPrimaryText,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Login to Account",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "New here? ",
                                fontSize = 13.sp,
                                color = HtmlMuted
                            )
                            Text(
                                text = "Create account",
                                fontSize = 13.sp,
                                color = HtmlPrimaryBorder,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        isSignupMode = true
                                        messageText = null
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .testTag("switch_to_signup_button")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // .footer
                Text(
                    text = "Premium Account Interface\nSMART CHAT Secure Account System",
                    color = HtmlFooterText,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HtmlFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = HtmlLabel,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = HtmlPlaceholder,
                    fontSize = 14.sp
                )
            },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HtmlField,
                unfocusedContainerColor = HtmlField,
                focusedBorderColor = HtmlAccent,
                unfocusedBorderColor = HtmlBorder,
                focusedTextColor = HtmlText,
                unfocusedTextColor = HtmlText,
                cursorColor = HtmlAccent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
