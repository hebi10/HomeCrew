# HomeCrew · 2-A-02 Room Repository·ViewModel 연결 및 재실행 복원

## 목표
- 2-A-01의 Room(SQLite) 테이블을 실제 4개 탭·집안일 상세·등록 흐름의 데이터 소스로 전환합니다.
- 집안일 원본·회차·반복 규칙·담당 구성원·완료 여부 및 완료자/완료 시각을 기기에서 재시작 후 복원합니다.
- Firebase 및 다른 기기와의 동기화는 하지 않습니다.

## 실제 데이터 흐름
1. 기존 1-C `LocalHouseholdStore`의 공간·구성원 정보를 불러옵니다.
2. 최초 실행에서만 선택 템플릿을 Room에 원자적으로 생성하고 `app_state`에 `homecrew.initial_household.v1` 마커를 기록합니다.
3. `HomeCrewRoomRepository`는 정의·배정 참여자 순서·개별 회차를 하나의 `HomeCrewSnapshot`으로 조립합니다.
4. `DemoChoreViewModel.Factory`가 앱용 Repository를 주입하고 Room Flow를 관찰합니다.
5. Compose 홈·일정·집안일·통계·상세가 이 상태를 공통 데이터 소스로 사용합니다.

## 쓰기 및 오류 처리
- 등록·수정: 입력 검증 → 저장 트랜잭션 → 새 스냅샷 반영 → 상세 이동. 저장에 실패하면 폼의 편집값을 보존합니다.
- 완료·취소: 기존 회차를 수정하며 완료 시각과 실제 완료자(현재 보는 구성원)를 DB에 저장합니다. 연속 탭 요청은 ViewModel에서 중복 방지합니다.
- 반복·담당: 표시 순서 기준 순번제와 기존 업무량 기준 분담을 복원합니다. 저장된 반복 규칙에서 다음 회차를 계산합니다.
- 수정: 기존 완료 기록/오늘 이전 기록 보존, 미래 미완료 회차만 트랜잭션 교체.
- 삭제: 확인 후 정의 삭제; 외래 키로 참여자와 회차를 연쇄 삭제합니다.
- 초기 템플릿: 삭제 후 또는 목록이 비어 있어도 마커가 있으므로 다시 생성하지 않습니다.
- 읽기 실패: 재시도 화면; 액션 실패: 입력 보존 또는 오류 안내.

## 데이터 보존 정책과 범위
- `homecrew.db`는 앱의 로컬 DB입니다. 정상 앱 재시작/Activity 재생성 후 데이터가 유지됩니다.
- 기존 집 이름·구성원 설정은 1-C `SharedPreferences`를 계속 사용합니다.
- 앱 삭제·앱 데이터 지우기 시 자료가 삭제될 수 있습니다. 계정 복구·백업·기기 간 공유는 제공하지 않습니다.
- 일정 선생성 범위는 기존 1-D와 같이 현재일부터 62일입니다. 장기 자동 보충, 지나간 일정 재생성, 건너뛰기 UI, DB 스키마 migration/export는 2-A-03에서 다룹니다.
- 페이지 내 작성 중 임시 초안은 DB에 저장하지 않으며 저장 버튼으로 확정한 내용만 보존합니다.

## 구현 파일
- `data/local/HomeCrewDao.kt`: 원자적 최초 생성, 트랜잭션 스냅샷, 기존 회차 보존
- `data/local/HomeCrewRoomRepository.kt`: Room 엔티티↔화면 모델 변환, 읽기·저장·완료·삭제
- `ui/screens/DemoChoreViewModel.kt`: Room Flow 수집, 로드 오류와 중복 탭 처리
- `ui/screens/HomeCrewMainScreens.kt`: 실제 영속 저장 액션·로딩/오류 처리
- `ui/screens/HomeCrewScreenParts.kt`, `HomeCrewChoreEditorScreens.kt`, `HomeCrewChoreDetailScreen.kt`, `HomeCrewHomeScreen.kt`, `HomeCrewChoresScreen.kt`: 저장 관련 문구
- `androidTest/HomeCrewRoomRepositoryTest.kt`: 파일 DB 닫기/재열기, 템플릿 중복 방지, 일정·담당·완료·월말 회귀
- `androidTest/HomeCrewActivityRestartTest.kt`: Activity와 ViewModel 새 인스턴스에서 저장 결과 확인

## 검증
- [ ] Android CI: testDebugUnitTest, lintDebug, assembleDebug, assembleDebugAndroidTest
- [ ] API35 에뮬레이터: 새 Repository DB 디스크 재열기, 기존 Room DAO 및 4개 탭 계측
- [ ] 앱 실제 종료·재실행: 저장된 항목/완료 여부 복원
- [ ] 라이트/다크 UI 캡처 비교
- [ ] 360dp·160% 글자 및 폴더블 확장 QA (추가 단계)

## 후속 작업
2-A-03 — Room 스키마 파일·업그레이드 마이그레이션, 미래 62일 이후 일정 보충, 완료/건너뛰기 기록 정교화 및 데이터 삭제·백업 검증.
