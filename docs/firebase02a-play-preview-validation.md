# HomeCrew FIREBASE-02-A — Play 앱 서명 실기기 검증 절차

검증 단계: PR #28 Draft · Google 로그인 사전 테스트 · 정식 배포 아님.

## 보안과 빌드 경계

1. PR #28의 `.github/workflows/firebase-02-a-signed-preview.yml`은 Firebase OAuth 구성이 포함된 **서명 전 AAB**만 생성합니다. 업로드 키와 Play 배포 서비스 계정 권한은 이 빌드에 제공하지 않습니다.
2. `main`의 보호된 `play-internal.yml`만 기존 업로드 키와 Google Play API를 사용합니다. 별도 승인된 PR #28의 SHA/Actions 실행 ID와 정확히 일치하는 아티팩트만 사용합니다.
3. 승인된 리비전의 Android CI·UI 스크린샷 QA·5환경 계측·Play 소스 검사·unsigned preview가 모두 통과해야 하며, 최신 PR HEAD가 바뀌면 업로드를 차단합니다.
4. 승인 명령어는 HomeCrew 저장소 소유자가 PR #28 댓글로 게시하는 다음 형식입니다. 두 값은 GitHub 실행에서 실제 확인한 값으로 대체합니다.

```text
/publish-homecrew-firebase02a-v14 <40자리-PR28-HEAD-SHA> <성공한-unsigned-preview-실행-ID>
```

5. 서명 작업은 PR 소스의 Gradle·Python·Shell 스크립트를 실행하지 않습니다. 신뢰된 `main`의 검증 도구로 번들 정보를 확인한 뒤 기존 업로드 키로 서명하고 내부 테스트 트랙만 변경합니다.
6. Play에 이미 `versionCode 14` 또는 더 높은 버전이 있으면 업로드하지 않고 실패 처리합니다. 무조건 버전을 올려 다시 제출하지 않습니다.
7. 배포 자동화 추가를 위한 `main` 병합 커밋에는 `[skip-play]` 마커를 사용해 기존 앱의 불필요한 자동 재배포를 방지합니다. 일반 앱 기능 변경의 자동 릴리스는 유지합니다.

## 예상 검증 순서

- [ ] PR #28 unsigned preview AAB 빌드 성공, 아티팩트 생성
- [ ] PR #28 동일 HEAD의 Android CI·API35 화면 QA·5개 API 매트릭스·Play exact-source 성공
- [ ] 소유자 승인 댓글 게시 후 보호된 `main` 배포 워크플로 실행
- [ ] 업로드 키 서명, APK용 Play 앱 서명과 구분, AAB 패키지/버전/서명 검증 성공
- [ ] `internal` 트랙에 `0.1.0 (14)` API commit 및 fresh readback 성공
- [ ] 기존 Play 설치본에서 제거 없이 14버전으로 업데이트
- [ ] 설정 > Google 로그인 > 실제 Google 계정 선택 및 성공 메시지 확인
- [ ] Firebase Console > Authentication > Users에서 테스트 계정 등록과 Firebase UID 확인(UID 자체는 비공개)
- [ ] 로그인 전후 Room 집 이름·구성원·집안일·완료 이력 비교로 로컬 자료 보존 검증
- [ ] 실패·취소·네트워크 끊김 시 기존 로컬 앱 기능이 유지됨을 확인

## 완료 조건 및 제외

FIREBASE-02-A 완료 판정에는 실기기 Google 로그인과 Firebase 사용자 UID 생성 증빙이 필요합니다. Android CI·에뮬레이터·Play API 성공만으로는 완료 처리하지 않습니다.

Google Play 'completed' API 응답은 테스터 설치 가능 여부나 Console 검토 상태를 보장하지 않습니다. 내부 테스트만 사용하고 비공개 테스트 승격, Firestore 실시간 공유, 가족 초대, 계정 삭제 기능은 이 단계에서 구현 또는 출시하지 않습니다.

기존 `main` 앱은 Room 기반이며, Preview에서 로그인하더라도 기존 로컬 집안일 데이터를 Firebase로 자동 업로드하지 않습니다.

## 확인 링크

- PR #28: https://github.com/hebi10/HomeCrew/pull/28
- 내부 테스트 설치: https://play.google.com/apps/internaltest/4700649023758805691
- Firebase 인증 사용자: https://console.firebase.google.com/project/homecrew-1b14b/authentication/users
