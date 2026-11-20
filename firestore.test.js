const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "shopkeeper_alice";
const BOB_UID = "shopkeeper_bob";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProductPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    itemCode: "PVC-101",
    name: "Astral PVC Pipe",
    brand: "Astral",
    sizeSpec: "1 inch (25mm)",
    category: "Pipes & Plumbing",
    unit: "Piece",
    purchasePrice: 190,
    averageCost: 190,
    sellingPrice: 245,
    stockQty: 45,
    lowStockLimit: 10,
    description: "Rack A-2",
    imageDataUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
    archived: false,
    isDemo: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated access is completely denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").get());
  await assertFails(unauthDb.collection("purchases").get());
  await assertFails(unauthDb.collection("sales").get());
  await assertFails(unauthDb.collection("stock_adjustments").get());
  await assertFails(unauthDb.collection("purchases").doc("tx_anyone_1").get());
  await assertFails(
    unauthDb.collection("products").doc("p1").set(validProductPayload(ALICE_UID))
  );
});

test("2. Product ownership, validation, archive, and non-existent product get protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Valid create
  await assertSucceeds(
    aliceDb.collection("products").doc("prod_1").set(validProductPayload(ALICE_UID))
  );

  // Non-existent product get must fail (no resource == null exception on products)
  await assertFails(aliceDb.collection("products").doc("non_existent_prod").get());

  // Reject ghost 'id', negative stock, video URL, or oversized image > 175000 chars
  await assertFails(
    aliceDb.collection("products").doc("prod_ghost").set(validProductPayload(ALICE_UID, { id: "prod_ghost" }))
  );
  await assertFails(
    aliceDb.collection("products").doc("prod_neg").set(validProductPayload(ALICE_UID, { stockQty: -1 }))
  );
  await assertFails(
    aliceDb.collection("products").doc("prod_vid").set(
      validProductPayload(ALICE_UID, { imageDataUrl: "data:video/mp4;base64,AAAA" })
    )
  );
  await assertFails(
    aliceDb.collection("products").doc("prod_big").set(
      validProductPayload(ALICE_UID, {
        imageDataUrl: "data:image/jpeg;base64," + "A".repeat(180000),
      })
    )
  );

  // Filtered query succeeds, unfiltered query fails
  await assertSucceeds(aliceDb.collection("products").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("products").get());

  // Cross-user access denied
  await assertFails(bobDb.collection("products").doc("prod_1").get());
  await assertFails(
    bobDb.collection("products").doc("prod_1").update({
      sellingPrice: 999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ordinary Edit Product succeeds when stockQty is unchanged
  await assertSucceeds(
    aliceDb.collection("products").doc("prod_1").update({
      sellingPrice: 255,
      brand: "Finolex",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ordinary Edit Product CANNOT silently change stockQty alongside metadata fields
  await assertFails(
    aliceDb.collection("products").doc("prod_1").update({
      name: "Changed Name",
      stockQty: 999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Archive product succeeds
  await assertSucceeds(
    aliceDb.collection("products").doc("prod_1").update({
      archived: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Real (non-demo) product cannot be deleted
  await assertFails(aliceDb.collection("products").doc("prod_1").delete());
});

test("3. Idempotent get checks, cross-user tx probing rejection, and duplicate prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("products").doc("prod_1").set(
      validProductPayload(ALICE_UID, { stockQty: 10, averageCost: 100, purchasePrice: 100 })
    )
  );

  // Missing transaction doc get succeeds ONLY for user's own tx_<uid>_... ID
  const aliceTxId = `tx_${ALICE_UID}_pur_1`;
  await assertSucceeds(aliceDb.collection("purchases").doc(aliceTxId).get());

  // Bob cannot probe Alice's transaction ID or arbitrary non-tx IDs
  await assertFails(bobDb.collection("purchases").doc(aliceTxId).get());
  await assertFails(bobDb.collection("purchases").doc("random_id_123").get());

  // Purchase transaction succeeds
  await assertSucceeds(
    aliceDb.runTransaction(async (tx) => {
      const pRef = aliceDb.collection("products").doc("prod_1");
      const purRef = aliceDb.collection("purchases").doc(aliceTxId);
      const existingPur = await tx.get(purRef);
      if (existingPur.exists) return;
      await tx.get(pRef);
      tx.update(pRef, {
        stockQty: 20,
        purchasePrice: 120,
        averageCost: 110,
        updatedAt: new Date(Date.now() - 1000),
      });
      tx.set(purRef, {
        userId: ALICE_UID,
        productId: "prod_1",
        productName: "Astral PVC Pipe",
        itemCode: "PVC-101",
        unit: "Piece",
        quantity: 10,
        unitPrice: 120,
        totalAmount: 1200,
        partyName: "Shree Plumbing",
        paymentMode: "UPI",
        transactionAt: now,
        isDemo: false,
        createdAt: now,
      });
    })
  );

  // Existing purchase doc readable by owner, denied to Bob
  await assertSucceeds(aliceDb.collection("purchases").doc(aliceTxId).get());
  await assertFails(bobDb.collection("purchases").doc(aliceTxId).get());

  // Duplicate transaction check via tx.get sees existingPur.exists == true
  await assertSucceeds(
    aliceDb.runTransaction(async (tx) => {
      const purRef = aliceDb.collection("purchases").doc(aliceTxId);
      const existingPur = await tx.get(purRef);
      if (existingPur.exists) return; // Idempotent exit!
      throw new Error("Should have detected duplicate transaction");
    })
  );

  // Bob cannot reference Alice's product in a purchase or sale
  const bobTxId = `tx_${BOB_UID}_hijack_1`;
  await assertFails(
    bobDb.collection("purchases").doc(bobTxId).set({
      userId: BOB_UID,
      productId: "prod_1", // Owned by Alice!
      productName: "Astral PVC Pipe",
      itemCode: "PVC-101",
      unit: "Piece",
      quantity: 5,
      unitPrice: 100,
      totalAmount: 500,
      paymentMode: "Cash",
      transactionAt: now,
      isDemo: false,
      createdAt: now,
    })
  );
});

test("4. Multi-product atomic sale transaction, stock adjustments, and demo-only deletion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Seed 4 products for multi-product sale
  for (let i = 1; i <= 4; i++) {
    await assertSucceeds(
      aliceDb.collection("products").doc(`prod_${i}`).set(
        validProductPayload(ALICE_UID, {
          itemCode: `CODE-10${i}`,
          name: `Item ${i}`,
          stockQty: 20,
          averageCost: 100,
          purchasePrice: 100,
        })
      )
    );
  }

  // Multi-product atomic sale across 4 products in a single runTransaction
  await assertSucceeds(
    aliceDb.runTransaction(async (tx) => {
      const txRefs = [1, 2, 3, 4].map((i) =>
        aliceDb.collection("sales").doc(`tx_${ALICE_UID}_multi_${i}`)
      );
      const prodRefs = [1, 2, 3, 4].map((i) =>
        aliceDb.collection("products").doc(`prod_${i}`)
      );

      // Read all before writing
      for (const ref of txRefs) {
        const snap = await tx.get(ref);
        if (snap.exists) return;
      }
      const prodSnaps = [];
      for (const ref of prodRefs) {
        prodSnaps.push(await tx.get(ref));
      }

      // Write all updates and sale docs
      prodRefs.forEach((pRef, idx) => {
        const snap = prodSnaps[idx];
        const qty = idx + 1;
        tx.update(pRef, {
          stockQty: snap.data().stockQty - qty,
          updatedAt: new Date(Date.now() - 1000),
        });
        tx.set(txRefs[idx], {
          userId: ALICE_UID,
          productId: `prod_${idx + 1}`,
          productName: `Item ${idx + 1}`,
          itemCode: `CODE-10${idx + 1}`,
          unit: "Piece",
          quantity: qty,
          unitSellingPrice: 150,
          unitCostAtSale: 100,
          totalAmount: qty * 150,
          estimatedProfit: qty * 50,
          paymentMode: "UPI",
          billGroupId: `bill_${ALICE_UID}_1`,
          transactionAt: now,
          isDemo: false,
          createdAt: now,
        });
      });
    })
  );

  // Stock adjustment transaction
  const adjId = `tx_${ALICE_UID}_adj_1`;
  await assertSucceeds(
    aliceDb.runTransaction(async (tx) => {
      const adjRef = aliceDb.collection("stock_adjustments").doc(adjId);
      const pRef = aliceDb.collection("products").doc("prod_1");
      const existingAdj = await tx.get(adjRef);
      if (existingAdj.exists) return;
      const pSnap = await tx.get(pRef);
      const prev = pSnap.data().stockQty;
      const delta = -2;
      tx.update(pRef, {
        stockQty: prev + delta,
        updatedAt: new Date(Date.now() - 500),
      });
      tx.set(adjRef, {
        userId: ALICE_UID,
        productId: "prod_1",
        productName: "Item 1",
        itemCode: "CODE-101",
        unit: "Piece",
        previousQty: prev,
        adjustmentDelta: delta,
        resultingQty: prev + delta,
        reason: "Damaged Goods",
        notes: "Cracked during unloading",
        transactionAt: now,
        isDemo: false,
        createdAt: now,
      });
    })
  );

  // Non-demo records cannot be deleted
  await assertFails(aliceDb.collection("sales").doc(`tx_${ALICE_UID}_multi_1`).delete());
  await assertFails(aliceDb.collection("stock_adjustments").doc(adjId).delete());

  // Demo records CAN be deleted
  await assertSucceeds(
    aliceDb.collection("products").doc("demo_p1").set(
      validProductPayload(ALICE_UID, { isDemo: true, demoBatchId: "batch_1" })
    )
  );
  await assertSucceeds(aliceDb.collection("products").doc("demo_p1").delete());
});
