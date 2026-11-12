# HomeCrew

집안일 분담, 반복 일정 및 완료율을 관리하기 위한 Android 앱입니다.

## 현재 단계 — 2-A-02 Room 로컬 저장 및 재실행 복원

Kotlin + Jetpack Compose 기반으로 **홈·일정·집안일·통계** 네 화면을 구현했습니다. 기존 1-A 디자인 시스템과 라이트·다크 테마를 유지합니다.

- **홈:** 오늘 진행률, 현재 보는 구성원, 빠른 완료/취소, 테마 전환
- **일정:** 월·주·일 전환, 날짜 선택 및 이동, 일자별 완료 상태
- **집안일:** 이름 검색, 카테고리·상태 필터, 반복 작업 목록
- **통계:** 주·월 완료율, 완료 개수, 배정/완료 난이도, 주간 차트, 구성원 비교
- **1-D 하위 화면:** 집안일 등록·수정, 반복 6종, 담당 방식 3종, 상세·완료·삭제 확인

**데이터 보관:** 집·구성원 설정은 기존 로컬 설정에, 집안일·담당 방식·반복 회차·완료 기록은 Room(SQLite) 데이터베이스에 보관합니다. 앱 재시작 후에도 복원되지만 앱 데이터 삭제·앱 제거 시 자료를 잃을 수 있으며, 다른 기기와 동기화되지 않습니다. Firebase 로그인·초대·공유는 미구현입니다.

- [1-A 공통 디자인 시스템](docs/design-system-1a.md)
- [1-B 화면별 구현/검증 기준](docs/screen-design-1b.md)
- [1-D 집안일 등록·반복·담당·상세 화면](docs/screen-design-1d.md)
- [2-A Room 데이터 구조](docs/room-persistence-2a.md)
- [2-A-02 Repository·재실행 복원](docs/room-repository-2a02.md)
- [1-B 시안 이미지](docs/design/02-homecrew-main-screens.png)
- [HomeCrew Google Drive](https://drive.google.com/drive/folders/16xQAcSuTapJ-D8sUdJBUJvfCtBIdo_Tx)

## 소스 구조

```text
app/src/main/java/com/hebi/homecrew/
├── MainActivity.kt                  # 4개 화면 진입점 및 1-A 갤러리
├── data/local/                      # Room DB·DAO·Repository·디스크 복원
└── ui/
    ├── theme/                       # 1-A 공통 토큰과 테마
    ├── components/                  # 재사용 Compose 컴포넌트
    ├── gallery/                     # 1-A 디자인 시스템 미리보기
    ├── model/                       # 공통 Preview 모델
    └── screens/
        ├── HomeCrewMainScreens.kt   # 4탭·집안일 편집 및 DB 저장 액션
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

- Java 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Android SDK 36, Room 2.8.5
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

### Firebase 초기 연결

[HomeCrew Firebase 프로젝트](https://console.firebase.google.com/project/homecrew-1b14b/overview)의 Android 앱 `com.hebi.homecrew`용 `google-services.json`을 `app/google-services.json`에 둡니다. 설정 파일이 있는 빌드에서는 Firebase 기본 앱이 자동으로 초기화됩니다. 설정 파일이 없는 빌드는 로컬 모드로 실행됩니다. 집과 집안일 데이터는 기기에서 관리합니다.

```bash
./gradlew :app:processDebugGoogleServices :app:assembleDebug
```

GitHub Actions는 저장소 시크릿 `HOMECREW_GOOGLE_SERVICES_JSON_BASE64`에서 설정 파일을 복원합니다. 저장소 내부 PR, `main`, 내부 테스트 릴리스는 HomeCrew 설정이 누락되면 실패 처리합니다. 외부 포크 PR은 시크릿을 받지 않으므로 로컬 전용 빌드만 수행합니다. Android 5환경 계측 및 API35 화면 QA는 설정이 제공되면 `FirebaseBootstrapTest`의 실제 기본 앱 초기화 성공까지 확인합니다.

**GitHub Actions 시크릿 1회 등록 (저장소 관리자 수행)**

1. 로컬에서 HomeCrew 프로젝트에 등록된 `google-services.json`을 준비합니다.
2. Windows PowerShell에서 아래 명령어로 파일 내용을 Base64로 인코딩하고 클립보드에 복사합니다.

```powershell
$firebaseConfig = "C:\\path\\to\\google-services.json"
[Convert]::ToBase64String([System.IO.File]::ReadAllBytes($firebaseConfig)) | Set-Clipboard
```

3. [HomeCrew Actions secrets](https://github.com/hebi10/HomeCrew/settings/secrets/actions) → **New repository secret**를 선택합니다.
4. 이름은 `HOMECREW_GOOGLE_SERVICES_JSON_BASE64`, 값에는 클립보드 내용을 붙여 넣습니다.
5. PR #27의 Android CI / 화면 QA / API29·35·36 매트릭스 검증을 다시 실행합니다. 실제 설정으로 Firebase 초기화 테스트가 통과한 후에만 병합합니다.

**금지:** 설정 JSON 원문이나 Base64를 PR, 이슈 댓글, 워크플로 입력값, 저장소 파일 또는 로그에 기록하지 않습니다. Firebase 초기 연결에서는 로그인·Firestore·다중 기기 공유를 활성화하지 않으며, 현재 Room 데이터를 그대로 유지합니다.

## 변경 관리

- 코드·테스트·CI·디자인 참고 이미지는 GitHub PR로 관리합니다.
- 기획 원본과 이미지 시안은 Google Drive에 함께 보관합니다.
- 서명키와 인증정보는 GitHub에 커밋하지 않습니다.
- Firebase 연동은 별도 구현 및 검증 단계에서 진행합니다.
- Play 내부 배포 자동화와 계정 준비 상태는 [내부 등록 문서](docs/play-store/registration.md)에 정리합니다. 코드 준비와 실제 등록·게시 완료 상태를 구분합니다.
