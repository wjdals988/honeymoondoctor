package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 도시 삭제(백로그 1-3). 일정·경비·장소가 cityId로 이 도시를 가리키므로, 스펙 4장
 * 규칙(자동 연쇄 삭제 금지 — 참조만 해제)대로 참조를 null로 풀고 도시를 지운다.
 * 일정 삭제가 예약 링크를 해제하는 것과 같은 패턴이다.
 *
 * 참조 해제이지 데이터 삭제가 아니다: 도시를 잘못 만들어 지워도 그 도시에 묶어 둔
 * 일정·지출·장소는 "도시 없음"으로 남는다.
 */
class DeleteCityWithReferences @Inject constructor(
    private val cityRepository: CityRepository,
    private val itineraryRepository: ItineraryRepository,
    private val expenseRepository: ExpenseRepository,
    private val placeRepository: PlaceRepository,
) {

    /** 삭제 전 확인 다이얼로그에 보여줄 참조 개수. */
    suspend fun countReferences(tripId: String, cityId: String): Int {
        val itinerary = itineraryRepository.observeItinerary(tripId).first().count { it.cityId == cityId }
        val expenses = expenseRepository.observeExpenses(tripId).first().count { it.cityId == cityId }
        val places = placeRepository.observePlaces(tripId).first().count { it.cityId == cityId }
        return itinerary + expenses + places
    }

    suspend operator fun invoke(tripId: String, cityId: String) {
        itineraryRepository.observeItinerary(tripId).first()
            .filter { it.cityId == cityId }
            .forEach { itineraryRepository.update(tripId, it.copy(cityId = null)) }
        expenseRepository.observeExpenses(tripId).first()
            .filter { it.cityId == cityId }
            .forEach { expenseRepository.update(tripId, it.copy(cityId = null)) }
        placeRepository.observePlaces(tripId).first()
            .filter { it.cityId == cityId }
            .forEach { placeRepository.update(tripId, it.copy(cityId = null)) }
        cityRepository.delete(tripId, cityId)
    }
}
