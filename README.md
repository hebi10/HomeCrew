# HomeCrew

집안일 분담, 반복 일정 및 완료율을 관리하기 위한 Android 앱입니다.

## 현재 단계 — 1-D 집안일 등록·반복·담당·상세 화면

Kotlin + Jetpack Compose 기반으로 **홈·일정·집안일·통계** 네 화면을 구현했습니다. 기존 1-A 디자인 시스템과 라이트·다크 테마를 유지합니다.

- **홈:** 오늘 진행률, 현재 보는 구성원, 빠른 완료/취소, 테마 전환
- **일정:** 월·주·일 전환, 날짜 선택 및 이동, 일자별 완료 상태
- **집안일:** 이름 검색, 카테고리·상태 필터, 반복 작업 목록
- **통계:** 주·월 완료율, 완료 개수, 배정/완료 난이도, 주간 차트, 구성원 비교
- **1-D 하위 화면:** 집안일 등록·수정, 반복 6종, 담당 방식 3종, 상세·완료·삭제 확인

**주의:** 1-D에서는 집안일 등록·수정·삭제와 반복·담당자 배정을 앱 실행 중 **시연 데이터**로 동작시킵니다. 재시작 후 남는 것은 1-C의 집·구성원 설정뿐이며, 집안일 자체와 완료 이력은 Room 2-A까지 영구 보관되지 않습니다. Firebase 로그인·초대·공유도 미구현입니다.

- [1-A 공통 디자인 시스템](docs/design-system-1a.md)
- [1-B 화면별 구현/검증 기준](docs/screen-design-1b.md)
- [1-D 집안일 등록·반복·담당·상세 화면](docs/screen-design-1d.md)
- [1-B 시안 이미지](docs/design/02-homecrew-main-screens.png)
- [HomeCrew Google Drive](https://drive.google.com/drive/folders/16xQAcSuTapJ-D8sUdJBUJvfCtBIdo_Tx)

## 소스 구조

```text
app/src/main/java/com/hebi/homecrew/
├── MainActivity.kt                  # 4개 화면 진입점 및 1-A 갤러리
└── ui/
    ├── theme/                       # 1-A 공통 토큰과 테마
    ├── components/                  # 재사용 Compose 컴포넌트
    ├── gallery/                     # 1-A 디자인 시스템 미리보기
    ├── model/                       # 공통 Preview 모델
    └── screens/
        ├── HomeCrewMainScreens.kt   # 탭/공유 예시 상태
        ├── HomeCrewHomeScreen.kt
        ├── HomeCrewScheduleScreen.kt
        ├── HomeCrewChoresScreen.kt
        ├── HomeCrewStatsScreen.kt
        ├── HomeCrewScreenParts.kt
        ├── DemoChore.kt
        ├── DemoChoreViewModel.kt
        ├── ChoreEditing.kt
        ├── HomeCrewChoreEditorScreens.kt
        ├── HomeCrewChoreDetailScreen.kt
        └── DateLabels.kt
```

## 개발 및 검증

- Java 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Android SDK 36
- minSdk 26(Android 8.0 이상)

Unix/macOS:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

빌드 산출물: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions `Android CI`도 PR 및 `main`에서 해당 검증을 실행하며 디버그 APK/리포트를 아티팩트로 업로드합니다.

## 변경 관리

- 코드·테스트·CI·디자인 참고 이미지는 GitHub PR로 관리합니다.
- 기획 원본과 이미지 시안은 Google Drive에 함께 보관합니다.
- 서명키와 인증정보는 GitHub에 커밋하지 않습니다.
- Firebase 연동은 별도 구현 및 검증 단계에서 진행합니다.
- Play 내부 배포 자동화와 계정 준비 상태는 [내부 등록 문서](docs/play-store/registration.md)에 정리합니다. 코드 준비와 실제 등록·게시 완료 상태를 구분합니다.
