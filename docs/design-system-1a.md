# HomeCrew · 1-A 공통 디자인 시스템

상태: 디자인 시안 + Kotlin/Jetpack Compose 컴포넌트 갤러리. 집안일 CRUD 및 Firebase 미구현.

## 시안
- [Google Drive 최종 모노크롬 디자인 보드](https://drive.google.com/file/d/1Hr627UN6f-MJb2iDjO_3-5rByqs5IfKa/view?usp=drivesdk)
- [Google Drive 전체 화면별 기획서](https://docs.google.com/document/d/1QMyyRrFGWFSVRaeYM1VAlOGlav008wuCIGxb-Vmp1wo/edit?usp=drivesdk)
- 저장소 이미지: `docs/design/01-homecrew-design-system.png`

초기 생성 과정의 보라색·일러스트 중심 초안 대신 위 링크의 블랙·그레이 시안을 구현 기준으로 사용합니다.

## 주요 토큰

| 구분 | 라이트 | 다크 |
|---|---|---|
| Ink (primary text/action) | `#171717` | `#F5F5F5` |
| Surface | `#FFFFFF` | `#1E1E1E` |
| Background | `#FAFAFA` | `#121212` |
| Subtle | `#F5F5F5` | `#262626` |
| Border | `#E5E5E5` | `#404040` |
| Muted text | `#717171` | `#A3A3A3` |

상태 색은 성공·기한 경고·오류에만 사용하며 아이콘과 문구를 병행합니다. Material You 동적 컬러는 이 단계에서 사용하지 않습니다.

## 타이포그래피·간격

- 제목 24sp SemiBold, 섹션 18sp SemiBold, 본문 16sp, 보조 14sp, 캡션 12sp.
- Spacing: 4 / 8 / 12 / 16 / 20 / 24 / 32dp. 화면 좌우 여백 20dp.
- 주요 버튼 높이 52dp, 터치 최소 48dp, 집안일 행 64dp 이상.
- 불필요한 그림자·그라데이션·애니메이션·과도한 둥근 모서리는 제외.

## 재사용 가능 Compose 요소

| 요소 | 파일 / 함수 |
|---|---|
| 앱 전체 테마 | `ui/theme/HomeCrewTheme.kt` · `HomeCrewTheme` |
| 컬러 토큰 | `ui/theme/HomeCrewColors.kt` |
| 타이포그래피 | `ui/theme/HomeCrewTypography.kt` |
| 크기·간격 | `ui/theme/HomeCrewDimensions.kt` |
| 기본·보조 버튼 | `HomeCrewPrimaryButton`, `HomeCrewSecondaryButton` |
| 상태 칩 | `HomeCrewStatusChip` |
| 카테고리 선택 | `HomeCrewFilterChip` |
| 텍스트 입력 | `HomeCrewTextField` |
| 집안일 행 | `HomeCrewTaskRow` |
| 진행률 | `HomeCrewProgressCard` |
| 빈 상태 | `HomeCrewEmptyState` |
| 하단 탐색 | `HomeCrewBottomBar` |

## 미리보기

앱 진입점은 `HomeCrewDesignSystemGallery`입니다. 시스템/라이트/다크를 전환하고, 필터·입력·집안일 완료 체크·탭의 선택 상태를 확인할 수 있습니다. 모든 데이터는 화면 내 일시적인 데모 상태이며 기기에 저장하지 않습니다.

Android Studio에서 `PreviewLightGallery` 및 `PreviewDarkGallery` Compose Preview를 이용하거나 디버그 APK를 실행합니다.

## 범위 제외

- 실제 집안일 등록/편집·반복 일정·분담 로직
- Room DB 및 사용자 데이터 저장
- 로그인, 가족 초대, Firebase, 실시간 동기화, FCM
- Google Play 자동 배포·서명키

## 1-A 완료 기준

- [x] 공통 컬러·간격·서체와 라이트/다크 팔레트 정의
- [x] 4개 탭 스타일 및 공통 UI 요소 구현
- [x] 미리보기 화면과 두 테마 Preview 제공
- [x] 진행률 계산 단위 테스트 추가
- [ ] CI에서 `testDebugUnitTest lintDebug assembleDebug` 확인
- [ ] 실기기/에뮬레이터 캡처와 디자인 보드 대조

코드 작성 상태와 CI/기기 검증 상태는 별도로 보고합니다.
