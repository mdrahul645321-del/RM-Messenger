package com.example.core.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.core.firebase.AdminRepository
import com.example.core.firebase.CallRepository
import com.example.core.firebase.ChatRepository
import com.example.core.firebase.GoogleAuthHelper
import com.example.core.firebase.UserRepository
import com.example.core.firebase.authStateFlow
import com.example.core.model.CallType
import com.example.core.preferences.AppPreferencesRepository
import com.example.features.admin.AdminPanelScreen
import com.example.features.admin.AdminViewModel
import com.example.features.auth.AuthViewModel
import com.example.features.auth.LoginScreen
import com.example.features.auth.RegisterScreen
import com.example.features.call.ActiveCallScreen
import com.example.features.call.CallCoordinatorViewModel
import com.example.features.call.CallUiPhase
import com.example.features.call.IncomingCallScreen
import com.example.features.chat.ChatScreen
import com.example.features.chat.ChatViewModel
import com.example.features.home.HomeScreen
import com.example.features.home.HomeViewModel
import com.example.features.profile.ProfileScreen
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.features.splash.SplashScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object RmRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ADMIN_PANEL = "admin_panel"
    const val CHAT = "chat/{conversationId}"

    fun chatRoute(conversationId: String): String = "chat/$conversationId"
}

private sealed class PendingCallAction {
    data class Outgoing(
        val peerUid: String,
        val peerName: String,
        val peerUsername: String,
        val callType: CallType,
    ) : PendingCallAction()

    data class AcceptIncoming(val isVideo: Boolean) : PendingCallAction()
}

@Composable
fun RmRootApp(
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val preferencesRepository = remember(appContext) {
        AppPreferencesRepository(appContext)
    }
    val userRepository = remember(appContext) {
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        UserRepository(db = db, auth = Firebase.auth)
    }
    val chatRepository = remember(appContext) {
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        ChatRepository(db = db, auth = Firebase.auth)
    }
    val callRepository = remember(appContext) {
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        CallRepository(db = db, auth = Firebase.auth)
    }
    val adminRepository = remember(appContext) {
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        AdminRepository(db = db, auth = Firebase.auth)
    }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var splashFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            val credentialManager = CredentialManager.create(context)
            GoogleAuthHelper.attemptSilentSignIn(
                context = context,
                credentialManager = credentialManager,
                userRepository = userRepository,
            )
        }
    }

    if (!splashFinished) {
        SplashScreen(
            isAuthenticated = currentUser != null,
            onNavigateToLogin = { splashFinished = true },
            onNavigateToHome = { splashFinished = true },
        )
        return
    }

    val activeUser = currentUser
    if (activeUser == null) {
        // Strict Auth Gate: Zero Firestore listeners or feature ViewModels exist while unauthenticated
        AuthNavHost(userRepository = userRepository)
    } else {
        // Key by UID so account switching / sign-out cleanly resets ViewModels and listeners
        key(activeUser.uid) {
            AuthenticatedNavHost(
                currentUserId = activeUser.uid,
                userRepository = userRepository,
                chatRepository = chatRepository,
                callRepository = callRepository,
                adminRepository = adminRepository,
                preferencesRepository = preferencesRepository,
                settingsViewModel = settingsViewModel,
            )
        }
    }
}

@Composable
private fun AuthNavHost(
    userRepository: UserRepository,
    navController: NavHostController = rememberNavController(),
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(userRepository),
    )

    NavHost(
        navController = navController,
        startDestination = RmRoutes.LOGIN,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(RmRoutes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(RmRoutes.REGISTER) },
            )
        }
        composable(RmRoutes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun AuthenticatedNavHost(
    currentUserId: String,
    userRepository: UserRepository,
    chatRepository: ChatRepository,
    callRepository: CallRepository,
    adminRepository: AdminRepository,
    preferencesRepository: AppPreferencesRepository,
    settingsViewModel: SettingsViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = viewModel(
        key = currentUserId,
        factory = HomeViewModel.Factory(
            userRepository = userRepository,
            chatRepository = chatRepository,
            currentUserId = currentUserId,
            callRepository = callRepository,
            adminRepository = adminRepository,
        ),
    )
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    if (homeState.currentUserAccount.isSuspended) {
        PlatformGateScreen(
            isSuspended = true,
            title = "Account Suspended",
            message = "Your RM Messenger account has been temporarily suspended by an administrator. Contact platform support if you believe this is an error.",
            onSignOut = {
                val credentialManager = CredentialManager.create(context)
                GoogleAuthHelper.signOut(credentialManager, userRepository, scope)
            },
        )
        return
    }

    if (homeState.platformConfig.maintenanceMode && !homeState.currentUserAccount.isAdmin) {
        PlatformGateScreen(
            isSuspended = false,
            title = "Scheduled Maintenance",
            message = homeState.platformConfig.maintenanceMessage.ifBlank {
                "RM Messenger is temporarily undergoing scheduled maintenance. Please check back shortly."
            },
            onSignOut = {
                val credentialManager = CredentialManager.create(context)
                GoogleAuthHelper.signOut(credentialManager, userRepository, scope)
            },
        )
        return
    }

    val callViewModel: CallCoordinatorViewModel = viewModel(
        key = "call_$currentUserId",
        factory = CallCoordinatorViewModel.Factory(currentUserId, callRepository, appContext),
    )
    val callState by callViewModel.uiState.collectAsStateWithLifecycle()
    val localVideoTrack by callViewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by callViewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val eglContext by callViewModel.eglContext.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCallAction by remember { mutableStateOf<PendingCallAction?>(null) }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val action = pendingCallAction
        pendingCallAction = null
        val isVideo = when (action) {
            is PendingCallAction.Outgoing -> action.callType == CallType.VIDEO
            is PendingCallAction.AcceptIncoming -> action.isVideo
            null -> false
        }
        val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !isVideo || results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (audioGranted && cameraGranted) {
            when (action) {
                is PendingCallAction.Outgoing -> {
                    callViewModel.startOutgoingCall(
                        caller = homeState.currentUserAccount,
                        calleeUid = action.peerUid,
                        calleeName = action.peerName,
                        calleeUsername = action.peerUsername,
                        callType = action.callType,
                    )
                }
                is PendingCallAction.AcceptIncoming -> {
                    callViewModel.acceptIncomingCall()
                }
                null -> Unit
            }
        } else {
            callViewModel.onPermissionsDenied(isVideo = isVideo)
        }
    }

    val startCallWithPermissionCheck: (String, String, String, CallType) -> Unit =
        { peerUid, peerName, peerUsername, callType ->
            if (!homeState.platformConfig.callingEnabled) {
                scope.launch {
                    snackbarHostState.showSnackbar("Audio and video calling is temporarily paused by administrator.")
                }
            } else {
                val isVideo = callType == CallType.VIDEO
                val needed = buildList {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.CAMERA)
                    }
                }
                if (needed.isEmpty()) {
                    callViewModel.startOutgoingCall(
                        caller = homeState.currentUserAccount,
                        calleeUid = peerUid,
                        calleeName = peerName,
                        calleeUsername = peerUsername,
                        callType = callType,
                    )
                } else {
                    pendingCallAction = PendingCallAction.Outgoing(peerUid, peerName, peerUsername, callType)
                    callPermissionsLauncher.launch(needed.toTypedArray())
                }
            }
        }

    val acceptIncomingWithPermissionCheck: (Boolean) -> Unit = { isVideo ->
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (needed.isEmpty()) {
            callViewModel.acceptIncomingCall()
        } else {
            pendingCallAction = PendingCallAction.AcceptIncoming(isVideo)
            callPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(callState.statusBanner) {
        val banner = callState.statusBanner
        if (!banner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(banner)
            callViewModel.clearBanner()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = RmRoutes.HOME,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(RmRoutes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenProfile = { navController.navigate(RmRoutes.PROFILE) },
                    onOpenSettings = { navController.navigate(RmRoutes.SETTINGS) },
                    onOpenConversation = { conversationId ->
                        navController.navigate(RmRoutes.chatRoute(conversationId))
                    },
                    onStartCall = startCallWithPermissionCheck,
                )
            }
            composable(
                route = RmRoutes.CHAT,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
                val chatViewModel: ChatViewModel = viewModel(
                    key = "$currentUserId:$conversationId",
                    factory = ChatViewModel.Factory(
                        conversationId = conversationId,
                        currentUserId = currentUserId,
                        chatRepository = chatRepository,
                        userRepository = userRepository,
                        preferencesRepository = preferencesRepository,
                        appContext = appContext,
                    ),
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    currentUserId = currentUserId,
                    onNavigateBack = { navController.popBackStack() },
                    onStartAudioCall = { peerUid, peerName, peerUsername ->
                        startCallWithPermissionCheck(peerUid, peerName, peerUsername, CallType.AUDIO)
                    },
                    onStartVideoCall = { peerUid, peerName, peerUsername ->
                        startCallWithPermissionCheck(peerUid, peerName, peerUsername, CallType.VIDEO)
                    },
                    onReportChat = { targetUid, targetName, targetType, targetId, reason, details ->
                        scope.launch {
                            val res = adminRepository.submitReport(
                                reporterName = homeState.currentUserAccount.displayName.ifBlank { "RM User" },
                                targetUid = targetUid,
                                targetName = targetName,
                                targetType = targetType,
                                targetId = targetId,
                                reason = reason,
                                details = details,
                            )
                            snackbarHostState.showSnackbar(
                                res.fold(
                                    onSuccess = { "Report submitted for moderator review." },
                                    onFailure = { err -> err.localizedMessage ?: "Failed to submit report." },
                                ),
                            )
                        }
                    },
                    callingEnabled = homeState.platformConfig.callingEnabled,
                )
            }
            composable(RmRoutes.PROFILE) {
                ProfileScreen(
                    homeViewModel = homeViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(RmRoutes.SETTINGS) },
                )
            }
            composable(RmRoutes.SETTINGS) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    userRepository = userRepository,
                    blockedContacts = homeState.blockedContacts,
                    onUnblockContact = { contactUid ->
                        homeViewModel.setContactBlocked(contactUid, blocked = false)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    currentUserRole = homeState.currentUserAccount.role,
                    onOpenAdminPanel = { navController.navigate(RmRoutes.ADMIN_PANEL) },
                )
            }
            composable(RmRoutes.ADMIN_PANEL) {
                val adminViewModel: AdminViewModel = viewModel(
                    key = "admin_${currentUserId}_${homeState.currentUserAccount.role}",
                    factory = AdminViewModel.Factory(
                        currentUserId = currentUserId,
                        currentUserName = homeState.currentUserAccount.displayName.ifBlank { "RM Admin" },
                        currentUserRole = homeState.currentUserAccount.role,
                        adminRepository = adminRepository,
                    ),
                )
                AdminPanelScreen(
                    viewModel = adminViewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }

        if (callState.activeCall.phase != CallUiPhase.IDLE) {
            ActiveCallScreen(
                callState = callState.activeCall,
                eglContext = eglContext,
                localVideoTrack = localVideoTrack,
                remoteVideoTrack = remoteVideoTrack,
                onToggleMute = callViewModel::toggleMute,
                onToggleSpeaker = callViewModel::toggleSpeaker,
                onToggleCamera = callViewModel::toggleCamera,
                onSwitchCamera = callViewModel::switchCamera,
                onEndCall = callViewModel::endCurrentCall,
            )
        } else {
            val incoming = callState.incomingCall
            if (incoming != null) {
                IncomingCallScreen(
                    incomingCall = incoming,
                    onAccept = {
                        acceptIncomingWithPermissionCheck(incoming.callType == "video")
                    },
                    onReject = callViewModel::rejectIncomingCall,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun PlatformGateScreen(
    isSuspended: Boolean,
    title: String,
    message: String,
    onSignOut: () -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (isSuspended) Icons.Rounded.Block else Icons.Rounded.Build,
                contentDescription = null,
                tint = if (isSuspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}


