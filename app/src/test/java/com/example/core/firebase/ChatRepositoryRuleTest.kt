package com.example.core.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.media.PreparedAttachment
import com.example.core.model.ContactSummary
import com.example.core.model.MessageType
import com.example.core.model.UserAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun directChat_sendMessage_reaction_andReadReceipt_workThroughRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("chat_alice_$suffix@test.com")
        val aliceUser = UserAccount(uid = aliceUid, displayName = "Alice RM", username = "alice_$suffix")

        val bobUid = signInTestUser("chat_bob_$suffix@test.com")
        val bobUser = UserAccount(uid = bobUid, displayName = "Bob RM", username = "bob_$suffix")

        // Switch back to Alice to create DM and send message
        signInTestUser("chat_alice_$suffix@test.com")
        val aliceChatRepo = ChatRepository(firestore, auth)
        val convResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.getOrCreateDirectConversation(
                currentUser = aliceUser,
                peerUid = bobUid,
                peerDisplayName = bobUser.displayName,
                peerUsername = bobUser.username,
            )
        }
        assertTrue("DM creation failed: ${convResult.exceptionOrNull()}", convResult.isSuccess)
        val convId = convResult.getOrThrow()

        val convDoc = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeConversation(convId).first { it != null }!!
        }
        val sendResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.sendMessage(
                conversation = convDoc,
                senderName = aliceUser.displayName,
                rawText = "Salam Bob! Ready for Phase 3?",
            )
        }
        assertTrue("Send message failed: ${sendResult.exceptionOrNull()}", sendResult.isSuccess)

        // Switch to Bob: observe conversation, messages, react, and mark read
        signInTestUser("chat_bob_$suffix@test.com")
        val bobChatRepo = ChatRepository(firestore, auth)
        val bobMessages = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeMessages(convId).first { it.isNotEmpty() }
        }
        assertEquals(1, bobMessages.size)
        val msg = bobMessages.first()
        assertEquals("Salam Bob! Ready for Phase 3?", msg.text)

        val reactRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.toggleReaction(convId, msg.id, "❤️", msg.reactions)
        }
        assertTrue("Reaction failed: ${reactRes.exceptionOrNull()}", reactRes.isSuccess)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.markConversationAsRead(convId, bobMessages, sendReadReceipt = true)
        }

        val updatedMsgs = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeMessages(convId).first {
                it.firstOrNull()?.reactions?.get(bobUid) == "❤️" &&
                    it.firstOrNull()?.readByUids?.contains(bobUid) == true
            }
        }
        assertEquals("❤️", updatedMsgs.first().reactions[bobUid])
        assertTrue(updatedMsgs.first().readByUids.contains(bobUid))
    }

    @Test
    fun groupChat_enforcesAdminPermissions_nonMemberIsolation_andDeleteRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("grp_alice_$suffix@test.com")
        val aliceUser = UserAccount(uid = aliceUid, displayName = "Alice Admin", username = "galice_$suffix")

        val bobUid = signInTestUser("grp_bob_$suffix@test.com")
        val bobContact = ContactSummary(
            uid = bobUid,
            displayName = "Bob Member",
            username = "gbob_$suffix",
            phoneNumber = "",
            about = "",
            isOnline = true,
        )

        // Alice creates group
        signInTestUser("grp_alice_$suffix@test.com")
        val aliceChatRepo = ChatRepository(firestore, auth)
        val grpResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.createGroupConversation(
                currentUser = aliceUser,
                title = "Kuwait & BD Team",
                description = "Official group",
                selectedMembers = listOf(bobContact),
            )
        }
        assertTrue("Group creation failed: ${grpResult.exceptionOrNull()}", grpResult.isSuccess)
        val grpId = grpResult.getOrThrow()

        val grpDoc = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeConversation(grpId).first { it != null }!!
        }
        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.sendMessage(grpDoc, aliceUser.displayName, "Group rules active!").getOrThrow()
        }

        // Bob (non-admin) cannot rename group or delete Alice's message for everyone
        signInTestUser("grp_bob_$suffix@test.com")
        val bobChatRepo = ChatRepository(firestore, auth)
        val renameAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.updateGroupInfo(grpId, "Hijacked Group", "")
        }
        assertTrue(renameAttempt.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            renameAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )

        val deleteAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.deleteMessageForEveryone(grpId, msgId)
        }
        assertTrue(deleteAttempt.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deleteAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )

        // Charlie (non-member) cannot read group messages
        signInTestUser("grp_charlie_$suffix@test.com")
        val charlieChatRepo = ChatRepository(firestore, auth)
        var deniedException: FirebaseFirestoreException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieChatRepo.observeMessages(grpId).first()
            }
        } catch (e: Exception) {
            deniedException = e.unwrapFirestoreException()
        }
        assertNotNull(deniedException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deniedException?.code)

        // Bob leaves group cleanly
        signInTestUser("grp_bob_$suffix@test.com")
        val latestGrp = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeConversation(grpId).first { it != null }!!
        }
        val leaveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.leaveGroup(latestGrp)
        }
        assertTrue("Leave group failed: ${leaveRes.exceptionOrNull()}", leaveRes.isSuccess)

        // After leaving, Bob's conversation query no longer contains grpId
        val bobConvs = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeConversations().first()
        }
        assertFalse(bobConvs.any { it.id == grpId })
    }

    @Test
    fun sendMediaMessage_voiceAndDocument_persistAndDeleteMediaDataCleanly() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("media_alice_$suffix@test.com")
        val aliceUser = UserAccount(uid = aliceUid, displayName = "Alice Media", username = "malice_$suffix")

        val bobUid = signInTestUser("media_bob_$suffix@test.com")
        val bobUser = UserAccount(uid = bobUid, displayName = "Bob Media", username = "mbob_$suffix")

        signInTestUser("media_alice_$suffix@test.com")
        val aliceChatRepo = ChatRepository(firestore, auth)
        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.getOrCreateDirectConversation(
                currentUser = aliceUser,
                peerUid = bobUid,
                peerDisplayName = bobUser.displayName,
                peerUsername = bobUser.username,
            ).getOrThrow()
        }
        val convDoc = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeConversation(convId).first { it != null }!!
        }

        val voiceAttachment = PreparedAttachment(
            messageType = MessageType.VOICE,
            fileName = "voice_note_1.m4a",
            mimeType = "audio/mp4",
            fileSizeBytes = 1280L,
            durationSeconds = 4,
            waveform = listOf(18, 42, 76, 54, 30, 88),
            base64Data = "UklGRgAAAABXQVZFZm10IBAAAAABAAEA",
        )
        val voiceRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.sendMediaMessage(
                conversation = convDoc,
                senderName = aliceUser.displayName,
                attachment = voiceAttachment,
            )
        }
        assertTrue("Voice send failed: ${voiceRes.exceptionOrNull()}", voiceRes.isSuccess)
        val voiceMsgId = voiceRes.getOrThrow()

        val docAttachment = PreparedAttachment(
            messageType = MessageType.DOCUMENT,
            fileName = "project_spec.pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 2048L,
            base64Data = "JVBERi0xLjQKJcOkw7zDtsOfCjIgMCBvYmo=",
        )
        val docRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.sendMediaMessage(
                conversation = convDoc,
                senderName = aliceUser.displayName,
                attachment = docAttachment,
            )
        }
        assertTrue("Document send failed: ${docRes.exceptionOrNull()}", docRes.isSuccess)

        // Bob reads both attachments cleanly
        signInTestUser("media_bob_$suffix@test.com")
        val bobChatRepo = ChatRepository(firestore, auth)
        val msgsForBob = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeMessages(convId).first { it.size == 2 }
        }
        val receivedVoice = msgsForBob.first { it.id == voiceMsgId }
        assertEquals("voice", receivedVoice.messageType)
        assertEquals(4L, receivedVoice.durationSeconds)
        assertEquals(6, receivedVoice.waveform?.size)
        assertEquals("UklGRgAAAABXQVZFZm10IBAAAAABAAEA", receivedVoice.mediaData)

        // Alice deletes her voice message for everyone, wiping mediaData per Phase 4 rules
        signInTestUser("media_alice_$suffix@test.com")
        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChatRepo.deleteMessageForEveryone(convId, voiceMsgId)
        }
        assertTrue("Delete voice for everyone failed: ${deleteRes.exceptionOrNull()}", deleteRes.isSuccess)

        val refreshedMsgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeMessages(convId).first { list ->
                list.any { it.id == voiceMsgId && it.isDeletedForEveryone }
            }
        }
        val deletedVoice = refreshedMsgs.first { it.id == voiceMsgId }
        assertTrue(deletedVoice.isDeletedForEveryone)
        assertEquals("This message was deleted", deletedVoice.text)
        assertNull(deletedVoice.mediaData)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
