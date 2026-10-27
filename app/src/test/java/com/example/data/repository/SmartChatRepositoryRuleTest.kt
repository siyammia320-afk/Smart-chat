package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SmartChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileAndSendTextMessage_authenticatedUser_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = SmartChatRepository(firestore, auth)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile(customUsername = "Alice")
        }
        assertTrue("Profile creation should succeed: ${profileResult.exceptionOrNull()}", profileResult.isSuccess)
        val profile = profileResult.getOrThrow()

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendTextMessage(profile, "আসসালামু আলাইকুম সবাইকে!")
        }
        assertTrue("Message send should succeed: ${msgResult.exceptionOrNull()}", msgResult.isSuccess)
        val messageId = msgResult.getOrThrow()

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeGroupMessages().first { list -> list.any { it.id == messageId } }
        }
        assertTrue(messages.any { it.id == messageId && it.text == "আসসালামু আলাইকুম সবাইকে!" })
    }

    @Test
    fun deleteMessage_crossUserFreshMessage_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = SmartChatRepository(firestore, auth)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(customUsername = "Alice").getOrThrow()
        }
        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendTextMessage(aliceProfile, "Alice's message").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SmartChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile(customUsername = "Bob").getOrThrow()
        }
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.deleteMessage(msgId)
        }
        assertTrue("Cross-user delete must fail", deleteResult.isFailure)
        val firestoreEx = deleteResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun setUserBlockStatus_nonAdminUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SmartChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(customUsername = "Alice").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SmartChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile(customUsername = "Bob").getOrThrow()
        }

        val blockResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.setUserBlockStatus(aliceUid, true)
        }
        assertTrue("Non-admin block attempt must fail", blockResult.isFailure)
        val firestoreEx = blockResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeGroupMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = SmartChatRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeGroupMessages().first()
            }
            fail("Expected exception when observing messages unauthenticated")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@smartchat.test"
        const val BOB_EMAIL = "bob@smartchat.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
