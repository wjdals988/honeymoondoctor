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
      name: "테스트 여행",
      ownerId: OWNER_UID,
      memberIds: [OWNER_UID, PARTNER_UID],
      inviteCodeHash: REAL_INVITE_HASH,
      defaultCurrency: "KRW",
      status: "ACTIVE",
    });
    await setDoc(doc(db, "trips", TRIP_ID, "members", OWNER_UID), { displayName: "소유자", role: "OWNER" });
    await setDoc(doc(db, "trips", TRIP_ID, "members", PARTNER_UID), { displayName: "구성원", role: "MEMBER" });
    await setDoc(doc(db, "trips", TRIP_ID, "itinerary", "seed-item"), { title: "샘플 일정" });
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
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", OUTSIDER_UID), {
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
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", OUTSIDER_UID), {
      applicantUid: OUTSIDER_UID,
      status: "PENDING",
      inviteCodeHash: "wrong-hash",
    }),
  );
});

test("본인 UID가 아닌 applicantUid로는 참여 요청을 생성할 수 없다", async () => {
  const applicantDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", OUTSIDER_UID), {
      applicantUid: PARTNER_UID,
      status: "PENDING",
      inviteCodeHash: REAL_INVITE_HASH,
    }),
  );
});

test("소유자가 아닌 구성원은 참여 요청을 승인할 수 없다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "trips", TRIP_ID, "joinRequests", OUTSIDER_UID), {
      applicantUid: OUTSIDER_UID,
      status: "PENDING",
      inviteCodeHash: REAL_INVITE_HASH,
    });
  });
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(
    updateDoc(doc(partnerDb, "trips", TRIP_ID, "joinRequests", OUTSIDER_UID), { status: "APPROVED" }),
  );
});

test("본인 참여 요청 문서 ID로 만들지 않으면 생성이 거부된다(회귀 방지)", async () => {
  const applicantDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    setDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", "some-other-id"), {
      applicantUid: OUTSIDER_UID,
      status: "PENDING",
      inviteCodeHash: REAL_INVITE_HASH,
    }),
  );
});

test("신청자 본인은 문서 ID를 알지 못해도 자기 UID로 참여 요청 상태를 조회할 수 있다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "trips", TRIP_ID, "joinRequests", OUTSIDER_UID), {
      applicantUid: OUTSIDER_UID,
      status: "REJECTED",
      inviteCodeHash: REAL_INVITE_HASH,
    });
  });
  const applicantDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertSucceeds(getDoc(doc(applicantDb, "trips", TRIP_ID, "joinRequests", OUTSIDER_UID)));
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

// FirebaseTripRepository.createTripWithSeed()가 실제로 쓰는 두 단계 쓰기 패턴을 그대로
// 재현한다. 트립 문서와 구성원+시드 데이터를 하나의 트랜잭션에 함께 넣으면, 규칙의
// isTripOwner()/isTripMember()가 get()으로 트립 문서를 다시 읽을 때 "트랜잭션 시작 시점"
// 상태(트립 문서 없음)를 보게 되어 프로덕션에서 항상 PERMISSION_DENIED가 난다. 이 회귀
// 테스트가 없어서 실제 Firebase 연동 전까지 이 버그가 발견되지 못했다.
test("신규 여행 생성: 트립 문서와 시드 데이터를 한 트랜잭션에 함께 쓰면 거부된다(회귀 방지)", async () => {
  const newTripId = "new-trip-single-tx";
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const { runTransaction, doc: docRef } = require("firebase/firestore");
  await assertFails(
    runTransaction(ownerDb, async (transaction) => {
      transaction.set(docRef(ownerDb, "trips", newTripId), {
        ownerId: OWNER_UID,
        memberIds: [OWNER_UID],
      });
      transaction.set(docRef(ownerDb, "trips", newTripId, "members", OWNER_UID), {
        displayName: "정민",
        role: "OWNER",
      });
    }),
  );
});

test("신규 여행 생성: 트립 문서를 먼저 커밋한 뒤 구성원+시드를 별도 트랜잭션으로 쓰면 성공한다", async () => {
  const newTripId = "new-trip-two-step";
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const { runTransaction, doc: docRef } = require("firebase/firestore");
  await assertSucceeds(
    setDoc(doc(ownerDb, "trips", newTripId), {
      ownerId: OWNER_UID,
      memberIds: [OWNER_UID],
    }),
  );
  await assertSucceeds(
    runTransaction(ownerDb, async (transaction) => {
      transaction.set(docRef(ownerDb, "trips", newTripId, "members", OWNER_UID), {
        displayName: "정민",
        role: "OWNER",
      });
      transaction.set(docRef(ownerDb, "trips", newTripId, "itinerary", "seed-item"), {
        title: "인천 → 프라하",
      });
    }),
  );
});

test("완료된 여행은 구성원도 하위 컬렉션을 쓸 수 없다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "trips", TRIP_ID), { status: "COMPLETED" });
  });
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(
    setDoc(doc(partnerDb, "trips", TRIP_ID, "expenses", "e-after-complete"), { amountMinor: 1000 }),
  );
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(
    updateDoc(doc(ownerDb, "trips", TRIP_ID, "itinerary", "seed-item"), { title: "수정 시도" }),
  );
});

test("완료된 여행도 구성원은 계속 읽을 수 있다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "trips", TRIP_ID), { status: "COMPLETED" });
  });
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertSucceeds(getDoc(doc(partnerDb, "trips", TRIP_ID, "itinerary", "seed-item")));
});

test("소유자는 여행을 완료 처리하고 다시 활성화할 수 있다", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(updateDoc(doc(ownerDb, "trips", TRIP_ID), { status: "COMPLETED" }));
  await assertSucceeds(updateDoc(doc(ownerDb, "trips", TRIP_ID), { status: "ACTIVE" }));
});

test("공개는 완료된 여행에서만 켤 수 있다", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  // 아직 ACTIVE인 상태에서 공개를 켜면 거부된다.
  await assertFails(updateDoc(doc(ownerDb, "trips", TRIP_ID), { isPublic: true }));
});

test("초대코드 해시가 남아있으면 공개를 켤 수 없다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "trips", TRIP_ID), { status: "COMPLETED" });
  });
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  // inviteCodeHash를 지우지 않고 공개만 켜면 거부된다.
  await assertFails(updateDoc(doc(ownerDb, "trips", TRIP_ID), { isPublic: true }));
});

test("완료 + 초대코드 해시 제거 상태에서는 공개를 켤 수 있다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "trips", TRIP_ID), { status: "COMPLETED" });
  });
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(
    updateDoc(doc(ownerDb, "trips", TRIP_ID), { isPublic: true, inviteCodeHash: null }),
  );
});

test("공개 중인 여행에서는 초대코드를 재발급할 수 없다(회귀 방지)", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "trips", TRIP_ID), {
      status: "COMPLETED",
      isPublic: true,
      inviteCodeHash: null,
    });
  });
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  // isPublic을 함께 내리지 않고 inviteCodeHash만 다시 채우면 거부된다 — 클라이언트 앱은
  // 이 상태를 감지해 UI에서 재발급 버튼 자체를 숨기고, 앱이 PERMISSION_DENIED로 죽지 않게
  // 예외를 처리해야 한다(TripInfoScreen/TripInfoViewModel 참고).
  await assertFails(updateDoc(doc(ownerDb, "trips", TRIP_ID), { inviteCodeHash: "new-hash" }));
});

test("공개 중인 여행은 삭제할 수 없다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "trips", TRIP_ID), {
      status: "COMPLETED",
      isPublic: true,
      inviteCodeHash: null,
    });
  });
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(deleteDoc(doc(ownerDb, "trips", TRIP_ID)));
});

test("publicTrips는 로그인한 사용자라면 누구나 읽을 수 있지만, 비인증 사용자는 읽을 수 없다", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "publicTrips", TRIP_ID), { name: "공개된 여행" });
    await setDoc(doc(db, "publicTrips", TRIP_ID, "itinerary", "pub-item"), { title: "공개 일정" });
  });
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertSucceeds(getDoc(doc(outsiderDb, "publicTrips", TRIP_ID)));
  await assertSucceeds(getDoc(doc(outsiderDb, "publicTrips", TRIP_ID, "itinerary", "pub-item")));

  const anonDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anonDb, "publicTrips", TRIP_ID)));
});

test("publicTrips는 원본 여행의 소유자만 쓸 수 있다", async () => {
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(setDoc(doc(outsiderDb, "publicTrips", TRIP_ID), { name: "위조된 공개 여행" }));

  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(setDoc(doc(partnerDb, "publicTrips", TRIP_ID), { name: "구성원이 공개 시도" }));

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "publicTrips", TRIP_ID), { name: "정상 공개" }));
});

test("소유자가 아닌 구성원은 여행 상태를 바꿀 수 없다", async () => {
  const partnerDb = testEnv.authenticatedContext(PARTNER_UID).firestore();
  await assertFails(updateDoc(doc(partnerDb, "trips", TRIP_ID), { status: "COMPLETED" }));
});
