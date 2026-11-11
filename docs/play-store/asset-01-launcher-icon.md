# ASSET-01 — 평면형 앱 아이콘 및 Android 런처 연결

- 제작일: 2026-11-11
- 대상 앱: HomeCrew (`com.hebi.homecrew`)
- 디자인: 흰색 배경, 차콜·중성 회색의 평면형 집·체크리스트·청소 도구 심볼
- 생성 이미지 기반으로 배경 정리·그레이스케일 색상 단순화·여백/투명도 최적화 (외부 폰트·로고 없음)

## 이미지 원본

| 경로 | 규격 | SHA-256 |
|---|---|---|
| `assets/play-store/ko-KR/app-icon.png` | 512×512 PNG | `d6f388e1a299d6617759fc991e2822ab72004e290bc961b51905f0f9dcfbe5b1` |
| `app/src/main/res/drawable-nodpi/homecrew_launcher_foreground.png` | 432×432 투명 PNG | `482d7f36ea90ec0cb793745651328faca49a37f80d8a81502f2adc694c803dab` |

## Android 런처 적용

- `AndroidManifest.xml`의 `android:icon` → `@mipmap/ic_launcher`, `android:roundIcon` → `@mipmap/ic_launcher_round`
- Android 8.0(API 26) 이상: 흰 배경, 안전영역 중앙 투명 전경의 적응형 아이콘
- Android 13(API 33) 이상: 시스템 테마 아이콘을 위한 `monochrome` 레이어
- 라이트·다크, 원형·정사각형 런처 마스크 및 48dp 축소 시 실기기 확인 필요

## 범위 및 출시

- 화면·테마·기능·Room·서명·버전·기존 Play 배포 워크플로는 변경하지 않음
- Google Play 스토어용 512px 원본은 저장소에 보관하나, 이번 변경만으로 콘솔 등록정보가 업데이트되지는 않음
- **Google Play Console 등록정보 아이콘은 아직 업로드하지 않음**: 스토어 등록정보 API 권한 검증과 변경은 별도 단계로 수행
- Android 런처 아이콘 변경은 이를 포함하는 새 AAB를 배포한 뒤 설치 앱에 적용됨

## 검증

- PNG 크기·투명도·색상과 SHA-256 사전 확인
- Android CI: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`
- Screenshot QA: 기존 4탭 회귀 검증. 실제 런처 아이콘의 원형·테마 마스크는 별도 실기기 확인
