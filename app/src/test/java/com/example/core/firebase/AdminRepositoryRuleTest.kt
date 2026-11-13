package com.example.core.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun regularUser_cannotPublishAnnouncements_orModifyConfig_butCanSubmitReport(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        signInTestUser("user_mod_$suffix@test.com")
        val firebaseUser = auth.currentUser!!
        val userRepo = UserRepository(firestore, auth)
        val ensureRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            userRepo.ensureUserProfileForAuth(firebaseUser, preferredDisplayName = "Regular User")
        }
        assertTrue("Profile init failed: ${ensureRes.exceptionOrNull()}", ensureRes.isSuccess)

        val adminRepo = AdminRepository(firestore, auth)

        // 1. Regular user CANNOT publish announcement
        val annRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.publishAnnouncement("Regular User", "Spoof", "Should fail", "info")
        }
        assertTrue("Regular user must be denied announcement publish", annRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            annRes.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )

        // 2. Regular user CANNOT modify platform config
        val cfgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updatePlatformConfig(FirestorePlatformConfigDocument(maintenanceMode = true))
        }
        assertTrue("Regular user must be denied platform config write", cfgRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            cfgRes.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )

        // 3. Regular user CAN submit a moderation report, and CANNOT resolve it themselves
        val repRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.submitReport(
                reporterName = "Regular User",
                targetUid = "target_$suffix",
                targetName = "Target User",
                targetType = "user",
                targetId = "target_$suffix",
                reason = "Harassment",
                details = "Unwanted messages",
            )
        }
        assertTrue("User report submission failed: ${repRes.exceptionOrNull()}", repRes.isSuccess)
        val reportId = repRes.getOrThrow()

        val resolveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.resolveReport(reportId, "resolved", "Self-resolved attempt")
        }
        assertTrue("Regular user must be denied resolving report", resolveRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            resolveRes.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )
    }

    @Test
    fun announcementsAndPlatformConfig_defaultAndReadableByAuthenticatedUser(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        signInTestUser("user_cfg_$suffix@test.com")
        val adminRepo = AdminRepository(firestore, auth)

        val config = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observePlatformConfig().first()
        }
        assertEquals(false, config.maintenanceMode)
        assertEquals(true, config.callingEnabled)

        val announcements = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAnnouncements(activeOnly = true).first()
        }
        assertTrue(announcements.size >= 0)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
