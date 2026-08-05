package com.jeongmin.honeymoondoctor.data.voucher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.jeongmin.honeymoondoctor.data.local.db.VoucherMetadataDao
import com.jeongmin.honeymoondoctor.data.local.db.VoucherMetadataEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 기기 전용 바우처 저장소(스펙 7-4). 파일 원본·파일명·메타데이터는 이 기기의
 * 내부 저장소(files/vouchers)와 Room에만 존재하고 Firestore에는 어떤 형태로도 올라가지 않는다.
 * 검증: 확장자·MIME 모두 화이트리스트, 파일당 15MB, 기기 총합 100MB.
 */
@Singleton
class VoucherStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VoucherMetadataDao,
) {
    companion object {
        const val MAX_FILE_BYTES = 15L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 100L * 1024 * 1024
        val ALLOWED_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "webp")
        val ALLOWED_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")
    }

    private val voucherDir: File get() = File(context.filesDir, "vouchers")

    fun observeForReservation(reservationId: String): Flow<List<VoucherMetadataEntity>> =
        dao.observeForReservation(reservationId)

    /** SAF/포토피커에서 받은 content Uri를 검증 후 내부 저장소로 복사한다. 실패 사유는 메시지로 반환. */
    suspend fun attach(reservationId: String, uri: Uri): Result<VoucherMetadataEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)
                    ?: throw IllegalArgumentException("파일 형식을 확인할 수 없습니다.")
                require(mimeType in ALLOWED_MIME_TYPES) {
                    "허용되지 않는 파일 형식입니다: $mimeType (PDF/JPG/PNG/WEBP만 가능)"
                }

                var displayName = "voucher"
                var declaredSize = -1L
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
                    }
                }
                val extension = displayName.substringAfterLast('.', "").lowercase()
                require(extension in ALLOWED_EXTENSIONS) {
                    "허용되지 않는 확장자입니다: .$extension (pdf/jpg/jpeg/png/webp만 가능)"
                }
                if (declaredSize > MAX_FILE_BYTES) {
                    throw IllegalArgumentException("파일이 15MB를 초과합니다.")
                }

                voucherDir.mkdirs()
                val target = File(voucherDir, "${UUID.randomUUID()}.$extension")
                var copied = 0L
                try {
                    resolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "파일을 열 수 없습니다." }
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                copied += read
                                // 선언된 크기를 믿지 않고 실제 복사량으로 다시 검증한다
                                if (copied > MAX_FILE_BYTES) {
                                    throw IllegalArgumentException("파일이 15MB를 초과합니다.")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (dao.totalSizeBytes() + copied > MAX_TOTAL_BYTES) {
                        throw IllegalArgumentException("기기 바우처 총 용량(100MB)을 초과합니다.")
                    }
                } catch (e: Exception) {
                    target.delete()
                    throw e
                }

                val entity = VoucherMetadataEntity(
                    id = "voucher-${UUID.randomUUID()}",
                    reservationId = reservationId,
                    fileName = displayName,
                    mimeType = mimeType,
                    sizeBytes = copied,
                    internalFilePath = target.absolutePath,
                    createdAtEpochMillis = Instant.now().toEpochMilli(),
                )
                dao.insert(entity)
                entity
            }
        }

    suspend fun remove(entity: VoucherMetadataEntity) = withContext(Dispatchers.IO) {
        File(entity.internalFilePath).delete()
        dao.delete(entity)
    }

    /** 예약 삭제 시 "바우처도 삭제" 확인을 받은 경우에만 호출한다(스펙 4장). */
    suspend fun removeAllForReservation(reservationId: String) = withContext(Dispatchers.IO) {
        dao.listForReservation(reservationId).forEach { remove(it) }
    }

    /** FileProvider Uri로 외부 뷰어를 여는 Intent. 파일 시스템 경로는 절대 노출하지 않는다. */
    fun buildOpenIntent(entity: VoucherMetadataEntity): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(entity.internalFilePath),
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entity.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
