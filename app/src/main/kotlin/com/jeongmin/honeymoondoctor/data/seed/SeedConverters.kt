package com.jeongmin.honeymoondoctor.data.seed

import com.jeongmin.honeymoondoctor.domain.model.ChecklistCategory
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem

/** ownerScope=SHARED는 공용(ownerUid=null). 역할 기반 값(PARTNER_A/B)은 실제 계정 연결 전이라 공용으로 둔다. */
fun ChecklistItemSeed.toDomainChecklistItem(): ChecklistItem = ChecklistItem(
    id = checklistItemId,
    title = title,
    category = runCatching { ChecklistCategory.valueOf(category) }.getOrDefault(ChecklistCategory.ETC),
    ownerUid = null,
    required = required,
)
