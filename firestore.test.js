const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, username = "alice_rm") {
  const now = new Date();
  return {
    uid,
    displayName: "Alice RM",
    username,
    email: "alice@example.com",
    phoneNumber: "+96550012345",
    about: "Available on RM Messenger",
    onlineStatus: "online",
    role: "user",
    accountStatus: "active",
    createdAt: now,
    updatedAt: now,
  };
}

function validGroupConversation(creatorUid, memberUids, adminUids) {
  const now = new Date();
  return {
    type: "group",
    memberUids,
    adminUids,
    title: "RM Launch Team",
    description: "Phase 3 group chat",
    participantNames: { [ALICE_UID]: "Alice RM", [BOB_UID]: "Bob RM" },
    participantUsernames: { [ALICE_UID]: "alice_rm", [BOB_UID]: "bob_rm" },
    lastMessageText: "",
    lastMessageSenderId: "",
    lastMessageSenderName: "",
    lastMessageAt: now,
    unreadCounts: {},
    pinnedBy: [],
    mutedBy: [],
    typingUids: [],
    createdBy: creatorUid,
    createdAt: now,
    updatedAt: now,
  };
}

function validMessage(senderId, senderName, text) {
  const now = new Date();
  return {
    senderId,
    senderName,
    text,
    messageType: "text",
    forwardedFrom: false,
    reactions: {},
    readByUids: [senderId],
    deliveredToUids: [senderId],
    deletedForUids: [],
    isDeletedForEveryone: false,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated caller cannot read or write /users or /usernames or /conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("usernames").doc("alice_rm").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertFails(
    unauthDb.collection("conversations").doc("grp_1").set(validGroupConversation(ALICE_UID, [ALICE_UID], [ALICE_UID]))
  );
});

test("Owner can create and read own profile, but cannot read another user's PII profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Privilege escalation and shadow field injection are rejected on /users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminPayload = { ...validUserPayload(ALICE_UID), role: "super_admin" };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(adminPayload));

  const ghostPayload = { ...validUserPayload(ALICE_UID), isVerified: true };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(ghostPayload));
});

test("Exact username lookup allows get without PII, blocks list scraping, and blocks hijack", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_rm").set({
      userId: ALICE_UID,
      username: "alice_rm",
      displayName: "Alice RM",
      about: "Hello",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(bobDb.collection("usernames").doc("alice_rm").get());
  await assertFails(bobDb.collection("usernames").get());
  await assertFails(
    bobDb.collection("usernames").doc("alice_rm").update({
      displayName: "Hijacked",
      updatedAt: new Date(),
    })
  );
});

test("User can manage own contacts subcollection, isolated from other users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).set({
      contactUid: BOB_UID,
      displayName: "Bob RM",
      username: "bob_rm",
      about: "Hey there",
      isBlocked: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").get());
});

test("Conversation and messages isolate non-members, protect reactions, and restrict admin/delete actions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const convId = "grp_team_1";
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set(
      validGroupConversation(ALICE_UID, [ALICE_UID, BOB_UID], [ALICE_UID])
    )
  );

  // Members can query their conversations; non-member Charlie cannot read convId
  await assertSucceeds(bobDb.collection("conversations").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("conversations").doc(convId).get());

  // Non-admin Bob cannot rename the group; admin Alice can
  await assertFails(bobDb.collection("conversations").doc(convId).update({ title: "Hijacked Name", updatedAt: new Date() }));
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).update({ title: "RM Engineering", updatedAt: new Date() }));

  // Alice sends message; Bob reads and adds ONLY his own reaction + read receipt
  const msgRef = aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1");
  await assertSucceeds(msgRef.set(validMessage(ALICE_UID, "Alice RM", "Welcome to Phase 3!")));

  // Charlie (non-member) cannot read or send messages
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").get());
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_2").set(
      validMessage(CHARLIE_UID, "Charlie", "Sneaking in")
    )
  );

  // Bob can add his own reaction and readByUids, but CANNOT overwrite Alice's reaction or delete Alice's message for everyone
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      [`reactions.${BOB_UID}`]: "👍",
      readByUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      [`reactions.${ALICE_UID}`]: "😡",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      isDeletedForEveryone: true,
      text: "This message was deleted",
      updatedAt: new Date(),
    })
  );

  // Sender Alice can delete her own message for everyone
  await assertSucceeds(
    msgRef.update({
      isDeletedForEveryone: true,
      text: "This message was deleted",
      reactions: {},
      updatedAt: new Date(),
    })
  );

  // Non-admin Bob can leave the group cleanly
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).update({
      memberUids: [ALICE_UID],
      adminUids: [ALICE_UID],
      updatedAt: new Date(),
    })
  );
  // Once removed, Bob can no longer read messages
  await assertFails(bobDb.collection("conversations").doc(convId).collection("messages").get());
});

test("Voice and document messages validate metadata bounds and enforce member isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const convId = "dm_alice_bob";
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      type: "direct",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      title: "",
      description: "",
      participantNames: { [ALICE_UID]: "Alice RM", [BOB_UID]: "Bob RM" },
      participantUsernames: { [ALICE_UID]: "alice_rm", [BOB_UID]: "bob_rm" },
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      lastMessageAt: new Date(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      pinnedBy: [],
      mutedBy: [],
      typingUids: [],
      createdBy: ALICE_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Valid voice message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("voice_1").set({
      ...validMessage(ALICE_UID, "Alice RM", "Voice message (0:06)"),
      messageType: "voice",
      fileName: "voice_note.m4a",
      mimeType: "audio/mp4",
      fileSizeBytes: 18432,
      durationSeconds: 6,
      waveform: [15, 42, 78, 64, 30, 55],
      mediaData: "UklGRg==",
    })
  );

  // Oversized file payload (> 950KB) must be rejected by rules
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("doc_huge").set({
      ...validMessage(BOB_UID, "Bob RM", "huge.pdf"),
      messageType: "document",
      fileName: "huge.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 2500000,
    })
  );

  // Non-member Charlie cannot read voice messages
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").get());
});

test("Calls and ICE candidates isolate non-participants, enforce caller/callee transitions, and validate SDP bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const callId = "call_alice_bob_1";
  const now = new Date();
  const validCall = {
    callerId: ALICE_UID,
    callerName: "Alice RM",
    callerUsername: "alice_rm",
    calleeId: BOB_UID,
    calleeName: "Bob RM",
    calleeUsername: "bob_rm",
    participantUids: [ALICE_UID, BOB_UID],
    callType: "video",
    status: "ringing",
    offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
    durationSeconds: 0,
    createdAt: now,
    updatedAt: now,
  };

  // Caller cannot forge someone else's callerId
  await assertFails(
    charlieDb.collection("calls").doc("fake_call").set({ ...validCall, participantUids: [ALICE_UID, BOB_UID] })
  );

  // Alice starts ringing call to Bob
  await assertSucceeds(aliceDb.collection("calls").doc(callId).set(validCall));

  // Non-participant Charlie cannot read call or add ICE candidate
  await assertFails(charlieDb.collection("calls").doc(callId).get());
  await assertFails(
    charlieDb.collection("calls").doc(callId).collection("candidates").doc("c_charlie").set({
      senderId: CHARLIE_UID,
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:1 1 UDP 2122252543 192.168.1.2 50000 typ host",
      createdAt: new Date(),
    })
  );

  // Caller Alice cannot accept her own call; only Callee Bob can accept
  await assertFails(
    aliceDb.collection("calls").doc(callId).update({
      status: "accepted",
      answerSdp: "v=0\r\n",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "accepted",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      updatedAt: new Date(),
    })
  );

  // Both participants can exchange ICE candidates, and either can end the call
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).collection("candidates").doc("c_alice").set({
      senderId: ALICE_UID,
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:1 1 UDP 2122252543 192.168.1.3 50001 typ host",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "ended",
      durationSeconds: 42,
      endedAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Super Admin RBAC enforces privilege isolation, user suspension, announcements, moderation reports, and platform config", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ownerUid = "super_admin_owner";
  const ownerDb = testEnv.authenticatedContext(ownerUid).firestore();

  // Seed regular user Alice + backend-provisioned Super Admin account
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice_rm"));
    await db.collection("users").doc(ownerUid).set({
      ...validUserPayload(ownerUid, "rm_owner"),
      role: "super_admin",
    });
  });

  // 1. Self-promotion via create or update is rejected
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validUserPayload(BOB_UID, "bob_rm"),
      role: "super_admin",
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: now,
    })
  );

  // 2. Regular user cannot list /users, publish announcements, or modify platform config
  await assertFails(aliceDb.collection("users").get());
  await assertFails(
    aliceDb.collection("announcements").doc("ann_spoof").set({
      title: "Fake Notice",
      body: "Unauthorized",
      priority: "info",
      isActive: true,
      authorUid: ALICE_UID,
      authorName: "Alice",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("app_config").doc("platform").set({
      maintenanceMode: true,
      maintenanceMessage: "Hacked",
      allowNewRegistrations: false,
      callingEnabled: false,
      voiceMessagesEnabled: false,
      mediaSharingEnabled: false,
      updatedByUid: ALICE_UID,
      updatedAt: now,
    })
  );

  // 3. Super Admin can list users, suspend/promote Alice, publish announcements, and update platform config
  await assertSucceeds(ownerDb.collection("users").get());
  await assertSucceeds(
    ownerDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "suspended",
      role: "admin",
      updatedAt: now,
    })
  );
  // Super Admin cannot assign 'super_admin' via client write or suspend self
  await assertFails(
    ownerDb.collection("users").doc(ALICE_UID).update({
      role: "super_admin",
      updatedAt: now,
    })
  );
  await assertFails(
    ownerDb.collection("users").doc(ownerUid).update({
      accountStatus: "suspended",
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("announcements").doc("ann_1").set({
      title: "RM Messenger v1.0",
      body: "Welcome to RM Messenger Phase 6!",
      priority: "important",
      isActive: true,
      authorUid: ownerUid,
      authorName: "Owner",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("announcements").get());

  await assertSucceeds(
    ownerDb.collection("app_config").doc("platform").set({
      maintenanceMode: false,
      maintenanceMessage: "Scheduled maintenance soon.",
      allowNewRegistrations: true,
      callingEnabled: true,
      voiceMessagesEnabled: true,
      mediaSharingEnabled: true,
      updatedByUid: ownerUid,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("app_config").doc("platform").get());

  // 4. Regular user can submit moderation report; cannot resolve it; Super Admin resolves it
  await assertSucceeds(
    aliceDb.collection("moderation_reports").doc("rep_1").set({
      reporterUid: ALICE_UID,
      reporterName: "Alice RM",
      targetUid: BOB_UID,
      targetName: "Bob RM",
      targetType: "user",
      targetId: BOB_UID,
      reason: "Spam",
      details: "Sending unsolicited links",
      status: "open",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("moderation_reports").doc("rep_1").update({
      status: "resolved",
      resolutionNote: "Self resolved",
      reviewedByUid: ALICE_UID,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ownerDb.collection("moderation_reports").doc("rep_1").update({
      status: "resolved",
      resolutionNote: "Warning issued.",
      reviewedByUid: ownerUid,
      updatedAt: now,
    })
  );
});

