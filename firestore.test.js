const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_siyam";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfile(uid, username, isBlocked = false, isAdminBadge = false) {
  const now = new Date();
  return {
    userId: uid,
    groupId: "smart_chat_main",
    username: username,
    avatarData: "avatar_1",
    statusText: "Active on SMART CHAT",
    isBlocked: isBlocked,
    isOnline: true,
    isAdminBadge: isAdminBadge,
    autoDeleteHours: 24,
    createdAt: now,
    updatedAt: now,
  };
}

function validMessage(senderId, senderName, text = "Hello group") {
  const now = new Date();
  return {
    groupId: "smart_chat_main",
    senderId: senderId,
    senderName: senderName,
    senderAvatar: "avatar_1",
    senderIsAdmin: false,
    type: "text",
    text: text,
    audioBase64: "",
    audioDurationSec: 0,
    replyToId: "",
    replyToSender: "",
    replyToPreview: "",
    ttlHours: 24,
    createdAt: now,
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot list or read messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("messages").where("groupId", "==", "smart_chat_main").get()
  );
});

test("2. Authenticated user fails query without groupId filter (Query Enforcer)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("messages").get());
});

test("3. Authenticated user can create own profile and send message when not blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, "Alice"))
  );
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_1").set(validMessage(ALICE_UID, "Alice"))
  );
});

test("4. Standard user cannot self-assign isAdminBadge on profile creation", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set(validProfile(BOB_UID, "Bob", false, true))
  );
});

test("5. Email spoofing attack (email_verified: false) is rejected for admin actions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(validProfile(BOB_UID, "Bob"));
  });

  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "siyammia320@gmail.com",
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofedAdminDb.collection("users").doc(BOB_UID).update({
      isBlocked: true,
      updatedAt: new Date(),
    })
  );
});

test("6. Verified Admin (siyammia320@gmail.com) can block and unblock any user", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(validProfile(BOB_UID, "Bob"));
  });

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "siyammia320@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      isBlocked: true,
      updatedAt: new Date(),
    })
  );
});

test("7. Blocked user cannot send messages or unblock themselves", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(validProfile(BOB_UID, "Bob", true));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("messages").doc("msg_blocked").set(validMessage(BOB_UID, "Bob"))
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      isBlocked: false,
      updatedAt: new Date(),
    })
  );
});

test("8. Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, "Alice"))
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "Alice2",
      ghostField: true,
      updatedAt: new Date(),
    })
  );
});

test("9. Standard user cannot delete another user's fresh message, but Admin can", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, "Alice"));
    await ctx.firestore().collection("messages").doc("msg_alice").set(validMessage(ALICE_UID, "Alice"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("messages").doc("msg_alice").delete());

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "siyammia320@gmail.com",
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(adminDb.collection("messages").doc("msg_alice").delete());
});
