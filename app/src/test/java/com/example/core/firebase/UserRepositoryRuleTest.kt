package com.example.core.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfileForAuth_createsOwnerProfileAndUsernameLookup() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val firebaseUser = checkNotNull(auth.currentUser)
        val repository = UserRepository(firestore, auth)
        val handle = "alice_${UUID.randomUUID().toString().take(6)}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfileForAuth(
                firebaseUser = firebaseUser,
                preferredDisplayName = "Alice RM",
                preferredUsername = handle,
            )
        }

        assertTrue("Profile creation failed: ${result.exceptionOrNull()}", result.isSuccess)
        val created = result.getOrThrow()
        assertEquals(uid, created.uid)
        assertEquals("Alice RM", created.displayName)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCurrentUser().first { it != null }
        }
        assertNotNull(observed)
        assertEquals(created.username, observed?.username)
    }

    @Test
    fun addContact_viaUsernameLookup_persistsIsolatedContact() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        signInTestUser("bob_$suffix@test.com")
        val bobUser = checkNotNull(auth.currentUser)
        val bobRepo = UserRepository(firestore, auth)
        val bobHandle = "bob_$suffix"
        val bobProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfileForAuth(
                firebaseUser = bobUser,
                preferredDisplayName = "Bob RM",
                preferredUsername = bobHandle,
            ).getOrThrow()
        }

        signInTestUser("carol_$suffix@test.com")
        val carolUser = checkNotNull(auth.currentUser)
        val carolRepo = UserRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            carolRepo.ensureUserProfileForAuth(
                firebaseUser = carolUser,
                preferredDisplayName = "Carol RM",
                preferredUsername = "carol_$suffix",
            ).getOrThrow()
        }

        val lookup = withTimeout(DEFAULT_TIMEOUT_MS) {
            carolRepo.lookupByUsername(bobProfile.username).getOrThrow()
        }
        assertNotNull(lookup)
        assertEquals(bobProfile.uid, lookup?.userId)

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            carolRepo.addContact(checkNotNull(lookup))
        }
        assertTrue(addResult.isSuccess)

        val contacts = withTimeout(FLOW_TIMEOUT_MS) {
            carolRepo.observeContacts().first { list -> list.any { it.contactUid == bobProfile.uid } }
        }
        assertTrue(contacts.any { it.username == bobProfile.username })
    }

    @Test
    fun crossUserRead_onPrivateUsersDoc_failsWithPermissionDenied() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("owner_$suffix@test.com")
        val aliceRepo = UserRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfileForAuth(
                firebaseUser = checkNotNull(auth.currentUser),
                preferredDisplayName = "Owner User",
                preferredUsername = "owner_$suffix",
            ).getOrThrow()
        }

        signInTestUser("intruder_$suffix@test.com")
        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertNotNull(caught)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun observeCurrentUser_unauthenticated_failsFastWithoutHanging() = runBlocking {
        auth.signOut()
        val repo = UserRepository(firestore, auth)

        var caught: IllegalStateException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCurrentUser().first()
            }
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertNotNull(caught)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
