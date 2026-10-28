package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AccountSessionManager
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.OnboardingPreferences
import com.example.ui.auth.signOut
import com.example.ui.screens.SmartChatMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SmartChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(context) {
        AccountSessionManager.init(context)
        if (AccountSessionManager.currentSession == null && auth.currentUser != null) {
            auth.signOut()
        }
    }

    val accountSession by AccountSessionManager.sessionFlow.collectAsStateWithLifecycle()
    var onboardingPrefs by remember { mutableStateOf<OnboardingPreferences?>(null) }

    val activeUserId = accountSession?.userId
    if (activeUserId == null) {
        AuthScreen(
            onAuthSuccess = { prefs ->
                onboardingPrefs = prefs
            }
        )
    } else {
        val chatViewModel: SmartChatViewModel = viewModel(
            key = activeUserId,
            factory = SmartChatViewModel.factory(activeUserId)
        )
        SmartChatMainScreen(
            viewModel = chatViewModel,
            initialPrefs = onboardingPrefs,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSignOut = {
                signOut(
                    context = context,
                    credentialManager = credentialManager,
                    onSignOutComplete = {},
                    scope = scope
                )
            }
        )
    }
}
