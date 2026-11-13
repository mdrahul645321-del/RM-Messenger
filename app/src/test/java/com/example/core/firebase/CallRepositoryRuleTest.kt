package com.example.core.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.model.CallType
import com.example.core.model.UserAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun callSignaling_outgoingOffer_iceExchange_accept_andEnd_workThroughRules(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("call_alice_$suffix@test.com")
        val aliceUser = UserAccount(uid = aliceUid, displayName = "Alice RM", username = "alice_$suffix")

        val bobUid = signInTestUser("call_bob_$suffix@test.com")
        val bobUser = UserAccount(uid = bobUid, displayName = "Bob RM", username = "bob_$suffix")

        val charlieUid = signInTestUser("call_charlie_$suffix@test.com")

        // Switch back to Alice to place an outgoing video call to Bob
        signInTestUser("call_alice_$suffix@test.com")
        val aliceCallRepo = CallRepository(firestore, auth)
        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.createOutgoingCall(
                caller = aliceUser,
                calleeUid = bobUid,
                calleeName = bobUser.displayName,
                calleeUsername = bobUser.username,
                callType = CallType.VIDEO,
            )
        }
        assertTrue("createOutgoingCall failed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val callId = createRes.getOrThrow()

        val offerRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.updateOfferSdp(callId, "v=0\r\no=alice 123 1 IN IP4 127.0.0.1\r\n")
        }
        assertTrue("updateOfferSdp failed: ${offerRes.exceptionOrNull()}", offerRes.isSuccess)

        // Caller Alice must NOT be allowed to accept her own outgoing call
        val selfAcceptRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.acceptCall(callId, "v=0\r\no=spoof 123 1 IN IP4 127.0.0.1\r\n")
        }
        assertTrue("Caller should be denied acceptCall", selfAcceptRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            selfAcceptRes.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )

        // Caller adds an ICE candidate
        val aliceIceRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.addIceCandidate(callId, "0", 0, "candidate:1 1 UDP 2122252543 10.0.0.2 54321 typ host")
        }
        assertTrue("Alice addIceCandidate failed: ${aliceIceRes.exceptionOrNull()}", aliceIceRes.isSuccess)

        // Outsider Charlie must be denied reading the call or adding ICE candidates
        signInTestUser("call_charlie_$suffix@test.com")
        val charlieCallRepo = CallRepository(firestore, auth)
        val charlieIceRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieCallRepo.addIceCandidate(callId, "0", 0, "candidate:99 1 UDP 1 10.0.0.9 1234 typ host")
        }
        assertTrue("Non-participant should be denied ICE write ($charlieUid)", charlieIceRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            charlieIceRes.exceptionOrNull()?.unwrapFirestoreException()?.code,
        )

        // Switch to Callee Bob: observe incoming ringing call, accept with answer SDP, exchange ICE, and end call
        signInTestUser("call_bob_$suffix@test.com")
        val bobCallRepo = CallRepository(firestore, auth)
        val incoming = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeIncomingRingingCalls().first { it?.id == callId }!!
        }
        assertEquals("ringing", incoming.status)
        assertEquals("video", incoming.callType)
        assertEquals(aliceUid, incoming.callerId)

        val acceptRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.acceptCall(callId, "v=0\r\no=bob 456 1 IN IP4 127.0.0.1\r\n")
        }
        assertTrue("Bob acceptCall failed: ${acceptRes.exceptionOrNull()}", acceptRes.isSuccess)

        val bobIceRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.addIceCandidate(callId, "0", 0, "candidate:2 1 UDP 2122252543 10.0.0.3 54322 typ host")
        }
        assertTrue("Bob addIceCandidate failed: ${bobIceRes.exceptionOrNull()}", bobIceRes.isSuccess)

        val candidates = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeIceCandidates(callId).first { it.size >= 2 }
        }
        assertEquals(2, candidates.size)

        val endRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.endCall(callId, durationSeconds = 35L, markAsMissed = false)
        }
        assertTrue("endCall failed: ${endRes.exceptionOrNull()}", endRes.isSuccess)

        val endedDoc = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeCall(callId).first { it?.status == "ended" }!!
        }
        assertEquals(35L, endedDoc.durationSeconds)
    }

    @Test
    fun callSignaling_rejectAndMissedTransitions_enforceRules(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("call_rej_alice_$suffix@test.com")
        val aliceUser = UserAccount(uid = aliceUid, displayName = "Alice R", username = "ar_$suffix")

        val bobUid = signInTestUser("call_rej_bob_$suffix@test.com")

        // 1. Bob rejects Alice's audio call
        signInTestUser("call_rej_alice_$suffix@test.com")
        val aliceCallRepo = CallRepository(firestore, auth)
        val rejectedCallId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.createOutgoingCall(
                caller = aliceUser,
                calleeUid = bobUid,
                calleeName = "Bob R",
                calleeUsername = "br_$suffix",
                callType = CallType.AUDIO,
            ).getOrThrow()
        }

        signInTestUser("call_rej_bob_$suffix@test.com")
        val bobCallRepo = CallRepository(firestore, auth)
        val rejectRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobCallRepo.rejectCall(rejectedCallId)
        }
        assertTrue("rejectCall failed: ${rejectRes.exceptionOrNull()}", rejectRes.isSuccess)
        val rejectedDoc = withTimeout(FLOW_TIMEOUT_MS) {
            bobCallRepo.observeCall(rejectedCallId).first { it?.status == "rejected" }!!
        }
        assertEquals("rejected", rejectedDoc.status)

        // 2. Alice marks unanswered call as missed
        signInTestUser("call_rej_alice_$suffix@test.com")
        val missedCallId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.createOutgoingCall(
                caller = aliceUser,
                calleeUid = bobUid,
                calleeName = "Bob R",
                calleeUsername = "br_$suffix",
                callType = CallType.AUDIO,
            ).getOrThrow()
        }
        val missedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceCallRepo.endCall(missedCallId, durationSeconds = 0L, markAsMissed = true)
        }
        assertTrue("missed endCall failed: ${missedRes.exceptionOrNull()}", missedRes.isSuccess)

        val history = withTimeout(FLOW_TIMEOUT_MS) {
            aliceCallRepo.observeCallHistory().first { list ->
                list.any { it.id == missedCallId && it.status == "missed" }
            }
        }
        assertTrue(history.any { it.id == missedCallId && it.status == "missed" })
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
