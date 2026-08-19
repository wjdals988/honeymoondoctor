package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.TripNote
import kotlinx.coroutines.flow.Flow

interface TripNoteRepository {
    /** 여행의 전체 쪽지. 보낸 시각 오름차순(대화 화면이 위→아래로 읽히는 순서). */
    fun observeNotes(tripId: String): Flow<List<TripNote>>

    /** note.id는 호출자가 미리 발급한다(데모/Firestore 동일 동작을 위해 — 다른 리포지토리와 같은 규약). */
    suspend fun send(tripId: String, note: TripNote)

    /** 받은 쪽지를 확인 처리한다. 보안 규칙상 수신자만 할 수 있다. */
    suspend fun markRead(tripId: String, noteId: String)

    /** 보낸 쪽지 삭제. 보안 규칙상 보낸 사람만 지울 수 있다. */
    suspend fun delete(tripId: String, noteId: String)
}
