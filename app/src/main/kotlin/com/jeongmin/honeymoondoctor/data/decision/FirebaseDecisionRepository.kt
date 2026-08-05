package com.jeongmin.honeymoondoctor.data.decision

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.Decision
import com.jeongmin.honeymoondoctor.domain.model.DecisionCategory
import com.jeongmin.honeymoondoctor.domain.model.DecisionOption
import com.jeongmin.honeymoondoctor.domain.model.DecisionStatus
import com.jeongmin.honeymoondoctor.domain.repository.DecisionRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val DECISIONS = "decisions"

@Singleton
class FirebaseDecisionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DecisionRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(DECISIONS)

    override fun observeDecisions(tripId: String): Flow<List<Decision>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toDecision() } }

    override suspend fun create(tripId: String, decision: Decision) {
        collection(tripId).document(decision.id)
            .set(
                decision.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun update(tripId: String, decision: Decision) {
        collection(tripId).document(decision.id)
            .set(
                decision.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, decisionId: String) {
        collection(tripId).document(decisionId).delete().await()
    }
}

internal fun Decision.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "category" to category.name,
    "status" to status.name,
    // 옵션은 [{id, label}] 형태의 배열로 저장한다(스펙 8장 options)
    "options" to options.map { mapOf("id" to it.id, "label" to it.label) },
    "selectedOptionId" to selectedOptionId,
    "dueAt" to dueAt?.let { Timestamp(it.epochSecond, it.nano) },
    "notes" to notes,
)

internal fun DocumentSnapshot.toDecision(): Decision? {
    val title = getString("title") ?: return null
    @Suppress("UNCHECKED_CAST")
    val rawOptions = (get("options") as? List<Map<String, Any?>>).orEmpty()
    return Decision(
        id = id,
        title = title,
        category = runCatching { DecisionCategory.valueOf(getString("category").orEmpty()) }
            .getOrDefault(DecisionCategory.ETC),
        status = runCatching { DecisionStatus.valueOf(getString("status").orEmpty()) }
            .getOrDefault(DecisionStatus.NEEDS_DECISION),
        options = rawOptions.mapNotNull { option ->
            val optionId = option["id"] as? String ?: return@mapNotNull null
            val label = option["label"] as? String ?: return@mapNotNull null
            DecisionOption(optionId, label)
        },
        selectedOptionId = getString("selectedOptionId"),
        dueAt = getTimestamp("dueAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
        notes = getString("notes"),
    )
}
