package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.ChecklistRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.DecisionRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * 여행 전체를 JSON 한 파일로 만든다(백로그 1-11). 계정을 잃으면 서버 데이터도 함께
 * 사라지는 구조라, 사용자 손에 쥐어 줄 수 있는 백업이 필요하다.
 *
 * org.json으로 손수 조립하는 이유: kotlinx.serialization @Serializable DTO는 R8
 * 규칙이 어긋나면 릴리스에서만 조용히 깨진 적이 있어(업데이트 확인·환율에서 같은 선택),
 * 이 앱의 네트워크·파일 직렬화는 전부 org.json으로 통일한다.
 *
 * 사람이 읽을 수 있는 형식이 목표다 — 가져오기(복원) 기능이 생기기 전에도 이 파일만
 * 있으면 수기로라도 복구할 수 있어야 한다.
 */
class TripBackupBuilder @Inject constructor(
    private val cityRepository: CityRepository,
    private val itineraryRepository: ItineraryRepository,
    private val reservationRepository: ReservationRepository,
    private val checklistRepository: ChecklistRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val placeRepository: PlaceRepository,
    private val decisionRepository: DecisionRepository,
) {

    suspend fun build(trip: Trip, appVersion: String): String {
        val root = JSONObject()
        root.put("format", "donghaeng-ilgi-backup")
        root.put("formatVersion", 1)
        root.put("exportedAt", Instant.now().toString())
        root.put("appVersion", appVersion)

        root.put(
            "trip",
            JSONObject()
                .put("name", trip.name)
                .put("startDate", trip.startDate)
                .put("endDate", trip.endDate)
                .put("defaultCurrency", trip.defaultCurrency)
                .put("status", trip.status.name),
        )

        root.put(
            "cities",
            JSONArray(
                cityRepository.observeCities(trip.id).first().map { city ->
                    JSONObject()
                        .put("id", city.id)
                        .put("displayName", city.displayName)
                        .put("countryCode", city.countryCode)
                        .put("timeZoneId", city.timeZoneId)
                        .putOpt("startDate", city.startDate)
                        .putOpt("endDate", city.endDate)
                },
            ),
        )

        root.put(
            "itinerary",
            JSONArray(
                itineraryRepository.observeItinerary(trip.id).first().map { item ->
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("type", item.type.name)
                        .put("startAt", item.startAt.toString())
                        .putOpt("endAt", item.endAt?.toString())
                        .put("allDay", item.allDay)
                        .put("timeZone", item.timeZone)
                        .putOpt("endTimeZone", item.endTimeZone)
                        .putOpt("cityId", item.cityId)
                        .putOpt("location", item.location)
                        .putOpt("address", item.address)
                        .put("status", item.status.name)
                        .putOpt("estimatedKrw", item.estimatedKrw)
                        .putOpt("notes", item.notes)
                },
            ),
        )

        root.put(
            "reservations",
            JSONArray(
                reservationRepository.observeReservations(trip.id).first().map { reservation ->
                    JSONObject()
                        .put("id", reservation.id)
                        .put("title", reservation.title)
                        .put("vendor", reservation.vendor)
                        .put("type", reservation.type.name)
                        .put("status", reservation.status.name)
                        .putOpt("confirmationCode", reservation.confirmationCode)
                        .putOpt("pin", reservation.pin)
                        .putOpt("startAt", reservation.startAt?.toString())
                        .putOpt("endAt", reservation.endAt?.toString())
                        .put("timeZone", reservation.timeZone)
                        .putOpt("estimatedKrw", reservation.estimatedKrw)
                },
            ),
        )

        root.put(
            "checklist",
            JSONArray(
                checklistRepository.observeChecklist(trip.id).first().map { item ->
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("category", item.category.name)
                        .put("required", item.required)
                        .put("completed", item.completed)
                        .putOpt("ownerUid", item.ownerUid)
                        .putOpt("dueAt", item.dueAt?.toString())
                },
            ),
        )

        root.put(
            "expenses",
            JSONArray(
                expenseRepository.observeExpenses(trip.id).first().map { expense ->
                    JSONObject()
                        .put("id", expense.id)
                        .put("amountMinor", expense.amountMinor)
                        .put("currency", expense.currency.name)
                        .put("fxRateToKrw", expense.fxRateToKrw)
                        .put("amountKrw", expense.amountKrw)
                        .put("category", expense.category.name)
                        .put("shared", expense.shared)
                        .putOpt("paidByUid", expense.paidByUid)
                        .putOpt("cityId", expense.cityId)
                        .put("spentAt", expense.spentAt.toString())
                        .putOpt("memo", expense.memo)
                },
            ),
        )

        root.put(
            "budgets",
            JSONArray(
                budgetRepository.observeBudgets(trip.id).first().map { budget ->
                    JSONObject()
                        .put("id", budget.id)
                        .put("budgetKrw", budget.budgetKrw)
                        .putOpt("cityId", budget.cityId)
                        .putOpt("category", budget.category?.name)
                },
            ),
        )

        root.put(
            "places",
            JSONArray(
                placeRepository.observePlaces(trip.id).first().map { place ->
                    JSONObject()
                        .put("id", place.id)
                        .put("name", place.name)
                        .put("category", place.category.name)
                        .put("priority", place.priority.name)
                        .putOpt("cityId", place.cityId)
                        .putOpt("latitude", place.latitude)
                        .putOpt("longitude", place.longitude)
                        .putOpt("mapsUrl", place.mapsUrl)
                        .put("visited", place.visited)
                        .putOpt("notes", place.notes)
                },
            ),
        )

        root.put(
            "decisions",
            JSONArray(
                decisionRepository.observeDecisions(trip.id).first().map { decision ->
                    JSONObject()
                        .put("id", decision.id)
                        .put("title", decision.title)
                        .put("status", decision.status.name)
                        .putOpt("notes", decision.notes)
                },
            ),
        )

        return root.toString(2)
    }
}
