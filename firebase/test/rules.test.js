const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
} = require("firebase/firestore");

const OWNER_UID = "owner-uid";
const PARTNER_UID = "partner-uid";
const OUTSIDER_UID = "outsider-uid";
const TRIP_ID = "trip-1";
const REAL_INVITE_SECRET = "correct-horse-battery-staple";
const REAL_INVITE_HASH = crypto.createHash("sha256").update(REAL_INVITE_SECRET).digest("hex");

let testEnv;

test.before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-honeymoon-doctor",
    firestore: {
      rules: readFileSync(path.resolve(__dirname, "../../firestore.rules"), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

test.after(async () => {
  await testEnv.cleanup();
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
  // 규칙을 우회해 "이미 승인된 2인 여행"의 기준 상태를 만든다.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "trips", TRIP_ID), {
      name: "정민·찬희 신혼여행",
      ownerId: OWNER_UID,
      memberIds: [OWNER_UID, PARTNER_UID],
      inviteCodeHash: REAL_INVITE_HASH,
      defaultCurrency: "KRW",
      status: "ACTIVE",
    });
    await setDoc(doc(db, "trips", TRIP_ID, "members", OWNER_UID), { displayName: "정민", role: "OWNER" });
    await setDoc(doc(db, "trips", TRIP_ID, "members", PARTNER_UID), { displayName: "찬희", role: "MEMBER" });
    await setDoc(doc(db, "trips", TRIP_ID, "itinerary", "seed-item"), { title: "인천 → 프라하" });
  });
});

test("비인증 사용자는 여행 문서를 읽을 수 없다", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "trips", TRIP_ID)));
});

test("구성원은 여행 문서와 하위 컬렉션을 읽고 쓸 수 있다", async () => {
  const db = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertSucceeds(getDoc(doc(db, "trips", TRIP_ID)));
  await assertSucceeds(
    setDoc(doc(db, "trips", TRIP_ID, "expenses", "e1"), { amountMinor: 1000, category: "식비" }),
  );
});

test("비구성원은 여행 문서도, 하위 컬렉션도 읽을 수 없다", async () => {
  const db = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(getDoc(doc(db, "trips", TRIP_ID)));
  await assertFails(getDoc(doc(db, "trips", TRIP_ID, "itinerary", "seed-item")));
});

test("승인 전 참여 신청자는 여행 데이터를 읽을 수 없다", async () => {
  const applicantDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  // 신청자는 아직 memberIds에 없으므로 참여 요청을 만들어도 여행 본문은 여전히 못 읽는다.
  await assertSucceeds(
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", "req-1"), {
      applicantUid: OUTSIDER_UID,
      status: "PENDING",
      inviteCodeHash: REAL_INVITE_HASH,
    }),
  );
  await assertFails(getDoc(doc(applicantDb, "trips", TRIP_ID)));
  await assertFails(getDoc(doc(applicantDb, "trips", TRIP_ID, "itinerary", "seed-item")));
});

test("초대코드 해시가 틀리면 참여 요청 생성이 거부된다", async () => {
  const applicantDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", "req-bad"), {
      applicantUid: OUTSIDER_UID,
      status: "PENDING",
      inviteCodeHash: "wrong-hash",
    }),
  );
});

test("본인 UID가 아닌 applicantUid로는 참여 요청을 생성할 수 없다", async () => {
  const applicantDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", "req-spoof"), {
      applicantUid: PARTNER_UID,
      status: "PENDING",
      inviteCodeHash: REAL_INVITE_HASH,
    }),
  );
});

test("소유자가 아닌 구성원은 참여 요청을 승인할 수 없다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "trips", TRIP_ID, "joinRequests", "req-1"), {
      applicantUid: OUTSIDER_UID,
      status: "PENDING",
      inviteCodeHash: REAL_INVITE_HASH,
    });
  });
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(
    updateDoc(doc(partnerDb, "trips", TRIP_ID, "joinRequests", "req-1"), { status: "APPROVED" }),
  );
});

test("이미 구성원이 2명인 여행에 3번째 구성원을 추가하면 차단된다", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(
    updateDoc(doc(ownerDb, "trips", TRIP_ID), {
      memberIds: [OWNER_UID, PARTNER_UID, OUTSIDER_UID],
    }),
  );
});

test("소유자를 구성원 목록에서 제거하는 갱신은 차단된다", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(
    updateDoc(doc(ownerDb, "trips", TRIP_ID), { memberIds: [PARTNER_UID] }),
  );
});

test("일반 구성원은 자신을 소유자로 승격할 수 없다", async () => {
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(
    updateDoc(doc(partnerDb, "trips", TRIP_ID), { ownerId: PARTNER_UID }),
  );
});

test("소유자만 여행을 삭제할 수 있다", async () => {
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(deleteDoc(doc(partnerDb, "trips", TRIP_ID)));

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(deleteDoc(doc(ownerDb, "trips", TRIP_ID)));
});

test("필터 없는 전체 trips 목록 조회는 차단되고, memberIds 필터 조회만 허용된다", async () => {
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(getDocs(collection(partnerDb, "trips")));
  await assertSucceeds(
    getDocs(query(collection(partnerDb, "trips"), where("memberIds", "array-contains", PARTNER_UID))),
  );
});
