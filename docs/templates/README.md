# 장소 가져오기 템플릿 (TSV / JSON)

`places_import_template.tsv`, `places_import_template.json`은 Google Maps에 저장해 둔
41개 장소를 앱의 "주변" 탭에 가져오기 위한 빈 템플릿입니다. Google Maps 저장 목록을
앱이 자동으로 읽어오지 않으므로(스펙 3장 제약), 이 템플릿에 직접 옮겨 적은 뒤
전체 탭 > 장소 가져오기·내보내기에서 불러옵니다.

장소명·평점·리뷰 수·위도·경도를 포함한 모든 값은 실제로 확인하기 전까지
임의로 채우지 않았습니다. 41행은 모두 빈 값입니다.

## 컬럼 설명

| 컬럼 | 필수 | 허용 값 |
| --- | --- | --- |
| name | 예 | 장소명 (자유 텍스트) |
| city | 아니오 | 여행 중 등록된 도시명 |
| category | 아니오 | 맛집 / 카페 / 관광 / 쇼핑 / 숙소 |
| priority | 아니오 | 꼭 가기 / 가고 싶음 / 여유 시 |
| latitude | 아니오 | 위도 (십진수, 예: 50.0875) |
| longitude | 아니오 | 경도 (십진수, 예: 14.4213) |
| mapsUrl | 아니오 | Google Maps 공유 링크 |
| preferredTime | 아니오 | 오전 / 점심 / 오후 / 저녁 / 밤 / 언제나 |
| ratingSnapshot | 아니오 | 가져온 시점의 평점 스냅샷 (예: 4.5). 실시간 수집 아님 |
| reviewCountSnapshot | 아니오 | 가져온 시점의 리뷰 수 스냅샷 |
| snapshotCheckedDate | 아니오 | 평점·리뷰 스냅샷을 확인한 날짜 (YYYY-MM-DD) |
| notes | 아니오 | 개인 메모 |

- `name`이 비어 있는 행은 가져오기 시 오류 행으로 표시됩니다(Phase 6에서 검증 로직 구현 예정).
- `latitude`/`longitude` 중 하나만 채워진 행도 오류로 처리합니다.
- 위도·경도가 없는 장소는 삭제되지 않고 "위치 미확인" 섹션에 표시됩니다.
