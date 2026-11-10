# HomeCrew

가족·연인·동거인의 집안일을 분담하고 반복 일정 및 완료율을 관리하기 위한 Android 앱입니다.

## 현재 단계 — 1-A 공통 디자인 시스템

`Kotlin + Jetpack Compose` 기반의 실행 가능한 **디자인 컴포넌트 갤러리**를 제공합니다. 앱에서 시스템/라이트/다크 테마, 중립 컬러, 타이포그래피, 버튼, 필터, 입력창, 집안일 카드, 진행률 및 하단 4탭을 미리 확인할 수 있습니다.

**현재 데모 화면의 집안일은 저장되지 않습니다.** 실제 로컬 CRUD·반복 일정·분담 로직은 후속 단계이며, Firebase 로그인·초대·공유는 별도 단계에서 연결합니다.

- [1-A 디자인 및 구현 기준](docs/design-system-1a.md)
- [Drive 시안 보드](https://drive.google.com/file/d/1Hr627UN6f-MJb2iDjO_3-5rByqs5IfKa/view?usp=drivesdk)
- [Drive HomeCrew 프로젝트 폴더](https://drive.google.com/drive/folders/16xQAcSuTapJ-D8sUdJBUJvfCtBIdo_Tx)

## 구조

```text
app/src/main/java/com/hebi/homecrew/
  MainActivity.kt
  ui/theme/          # 색상, 폰트, 간격, 라이트·다크
  ui/components/     # 재사용 Compose 컴포넌트
  ui/gallery/        # 1-A 디자인 확인 화면
  ui/model/          # 갤러리 전용 프리뷰 모델
app/src/test/        # 계산 로직 단위 테스트
docs/design/         # 기준 PNG
.github/workflows/   # Android CI
```

## 개발 환경

- Java 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36
- Android 8.0 이상(minSdk 26)
- Android Studio 또는 Gradle CLI

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions `Android CI`도 PR과 main 커밋에서 같은 검증을 실행하고, 아티팩트를 저장합니다.

## 기여 및 배포 원칙

- 기능 변경은 독립 브랜치 및 PR로 관리합니다. CI 통과·화면 검수 이후 main 반영.
- 디자인 문서는 Drive와 Git `docs/`를 기준으로 추적합니다.
- 서명키, 인증 토큰, `.env`, `signing.properties`는 저장소에 등록하지 않습니다.
- Google Play 릴리스 자동화는 앱 기능 구현 및 서명/스토어 준비 이후에만 설정합니다.
