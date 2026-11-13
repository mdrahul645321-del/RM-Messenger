package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.firebase.ChatRepository
import com.example.core.firebase.UserRepository
import com.example.features.auth.AuthViewModel
import com.example.features.auth.LoginScreen
import com.example.features.home.HomeScreen
import com.example.features.home.HomeViewModel
import com.example.ui.theme.RmMessengerTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RmMessengerPhase1Test {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createIsolatedRepos(): Pair<UserRepository, ChatRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbId = context.getString(R.string.firestore_database_id)
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == UI_TEST_APP_NAME }
            ?: run {
                val created = FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId("1:123456789012:android:1234567890abcdef")
                        .setProjectId("demo-no-project")
                        .setApiKey("fake-api-key-for-ui-test")
                        .build(),
                    UI_TEST_APP_NAME,
                )
                val fs = FirebaseFirestore.getInstance(created, dbId)
                fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
                created
            }
        val firestore = FirebaseFirestore.getInstance(app, dbId)
        val auth = FirebaseAuth.getInstance(app)
        return UserRepository(firestore, auth) to ChatRepository(firestore, auth)
    }

    @Test
    fun loginScreen_validatesPhoneAndDisplaysGoogleSignInButton() {
        val (userRepo, _) = createIsolatedRepos()
        val authViewModel = AuthViewModel(userRepo)

        composeRule.setContent {
            RmMessengerTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = {},
                )
            }
        }

        composeRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("123")
        composeRule.onNodeWithTag("send_otp_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Enter a valid 8-digit Kuwait number.").assertExists()
    }

    @Test
    fun homeScreen_switchesBetweenAllFourPrimaryTabs() {
        val (userRepo, chatRepo) = createIsolatedRepos()
        val homeViewModel = HomeViewModel(
            userRepository = userRepo,
            chatRepository = chatRepo,
            currentUserId = "test_uid",
        )

        composeRule.setContent {
            RmMessengerTheme {
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenProfile = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("No conversations yet").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_calls").performClick()
        composeRule.onNodeWithText("No recent calls").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_contacts").performClick()
        composeRule.onNodeWithText("No contacts saved yet").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_groups").performClick()
        composeRule.onNodeWithText("No groups joined").assertIsDisplayed()
    }

    private companion object {
        const val UI_TEST_APP_NAME = "ui-test-app"
    }
}
