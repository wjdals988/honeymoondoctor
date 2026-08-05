package com.jeongmin.honeymoondoctor.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 바우처 원본 파일은 이 기기의 내부 저장소(files/vouchers)에만 존재한다.
 * 이 엔티티와 파일 자체는 절대 Firestore에 올라가지 않으며, 다른 기기에는
 * "이 기기에 저장된 바우처 없음"으로만 보인다.
 */
@Entity(tableName = "voucher_metadata")
data class VoucherMetadataEntity(
    @PrimaryKey val id: String,
    val reservationId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val internalFilePath: String,
    val createdAtEpochMillis: Long,
)
