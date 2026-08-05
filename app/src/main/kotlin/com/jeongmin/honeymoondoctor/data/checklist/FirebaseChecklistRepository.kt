package com.jeongmin.honeymoondoctor.data.checklist

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.ChecklistCategory
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import com.jeongmin.honeymoondoctor.domain.repository.ChecklistRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val CHECKLIST_ITEMS = "checklistItems"

@Singleton
class FirebaseChecklistRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ChecklistRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(CHECKLIST_ITEMS)

    override fun observeChecklist(tripId: String): Flow<List<ChecklistItem>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toChecklistItem() } }

    override suspend fun create(tripId: String, item: ChecklistItem) {
        collection(tripId).document(item.id)
            .set(
                item.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun update(tripId: String, item: ChecklistItem) {
        collection(tripId).document(item.id)
            .set(
                item.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, itemId: String) {
        collection(tripId).document(itemId).delete().await()
    }
}

internal fun ChecklistItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "category" to category.name,
    "ownerUid" to ownerUid,
    "required" to required,
    "completed" to completed,
    "completedAt" to completedAt?.let { Timestamp(it.epochSecond, it.nano) },
    "dueAt" to dueAt?.let { Timestamp(it.epochSecond, it.nano) },
)

internal fun DocumentSnapshot.toChecklistItem(): ChecklistItem? {
    val title = getString("title") ?: return null
    return ChecklistItem(
        id = id,
        title = title,
        category = runCatching { ChecklistCategory.valueOf(getString("category").orEmpty()) }
            .getOrDefault(ChecklistCategory.ETC),
        ownerUid = getString("ownerUid"),
        required = getBoolean("required") ?: false,
        completed = getBoolean("completed") ?: false,
        completedAt = getTimestamp("completedAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
        dueAt = getTimestamp("dueAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
    )
}
