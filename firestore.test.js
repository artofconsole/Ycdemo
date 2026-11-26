const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "parentA";
const BOB_UID = "parentB";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validVideoPayload(uid, offsetMs = 1000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    title: "Sevimli Tren Şarkısı",
    sourceType: "YOUTUBE",
    videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    thumbnailUrl: "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    categoryId: "music",
    favorite: false,
    durationMs: 120000,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("unauthenticated user cannot read or write any video", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("videos").doc("vid1").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("videos").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("vid1")
      .set(validVideoPayload(ALICE_UID))
  );
});

test("owner can create, list, get, partial update, and delete own CloudVideo", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const videoRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("videos")
    .doc("yt_abc123");

  await assertSucceeds(videoRef.set(validVideoPayload(ALICE_UID, 10000)));
  await assertSucceeds(videoRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("videos").get()
  );

  await assertSucceeds(
    videoRef.update({
      title: "Yeni Başlık",
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(videoRef.delete());
});

test("cross-user read and write are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("vid1")
      .set(validVideoPayload(ALICE_UID, 10000));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("videos").doc("vid1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("videos").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("vid2")
      .set(validVideoPayload(BOB_UID))
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("vid1")
      .update({ favorite: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("videos").doc("vid1").delete()
  );
});

test("invalid sourceType, empty title, or extra unknown/@DocumentId fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const base = validVideoPayload(ALICE_UID);

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("bad1")
      .set({
        ...base,
        sourceType: "LOCAL_FILE",
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("bad2")
      .set({
        ...base,
        title: "",
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("bad3")
      .set({
        ...base,
        id: "bad3",
      })
  );
});

test("createdAt cannot be modified on update and future timestamps are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("videos")
    .doc("vid1");
  await assertSucceeds(ref.set(validVideoPayload(ALICE_UID, 10000)));

  await assertFails(
    ref.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("videos")
      .doc("future1")
      .set({
        ...validVideoPayload(ALICE_UID),
        createdAt: new Date(Date.now() + 3600 * 1000),
      })
  );
});
