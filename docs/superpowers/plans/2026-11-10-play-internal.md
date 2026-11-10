# HomeCrew Play Internal Implementation Plan

> **For agentic workers:** Execute inline with `superpowers:executing-plans`. Steps use checkbox syntax.

**Goal:** Current verified main에서 앱 전용 서명 APK/AAB와 안전한 Play 내부 배포 경로를 제공합니다.

**Architecture:** 검증된 TripCanvas 릴리스 스크립트/테스트를 재사용하고 앱 식별자·기존 CI에 맞춥니다. 초기 내부 초안과 이후 내부 업데이트를 구분하며 비공개/프로덕션 승격을 제외합니다.

**Tech Stack:** GitHub Actions, Python 3.12 표준 라이브러리, Kotlin Gradle DSL, Android SDK 36.

**Spec:** `docs/play-store/automation-spec.md`

## Global Constraints

- package `com.hebi.homecrew`, versionName `0.1.0`.
- Java 17 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 / SDK 36 유지.
- main 전용 WIF 및 기존 앱 전용 signer 필수. 키 생성/교체 없음.
- UI·권한·기존 앱 테스트 변경 없음. 내부 트랙만 변경.

## Review Focus

- main이 빌드 도중 바뀌면 업로드 전 중단.
- 미래 Android CI job 또는 main의 screenshot QA가 실패/생략이면 중단.
- 초안/미배정 바이너리/다른 트랙의 더 높은 버전으로 충돌 방지.
- 다른 앱 인증서·부분 서명·잘못된 package/version 및 native 정렬 차단.
- API 커밋 이후 읽기 검증 실패는 완료로 보고하지 않음.

### Task 1: Release guards and signer

**Files:** `scripts/play/*.py`, `scripts/play/tests/test_*.py`, `scripts/release_signing.py`, `scripts/test_release_signing.py`, `app/build.gradle.kts`.

**Interfaces:** Consumes GitHub event/exact main CI metadata and environment signer. Produces verified source SHA/mode, code allocation, signed artifact checks and internal-only Publisher commands.

- [x] Copy/adapt template tests first, add HomeCrew future-job and optional screenshot-QA guard cases; run RED before installing production scripts.
- [x] Adapt tested source/Publisher/signing/native implementations; retain version/hash/stale inventory/first-upload protections; remove promotion support and JSON fallback.
- [x] Run both complete Python release suites; expected PASS.

### Task 2: Actions and registration

**Files:** `.github/workflows/play-internal.yml`, `play-gates.yml`, `privacy-pages.yml`, `docs/privacy-policy.html`, `docs/play-store/listing-ko.json`, `registration.md`, `.gitignore`, `README.md`.

**Interfaces:** Consumes source SHA/mode and verified runtime binary readiness from Task 1; produces immutable signed artifacts, internal-only uploads and truthful registration materials.

- [x] Add workflow binding tests for internal-only target, exact-source gates, private signer cleanup, WIF and unchanged toolchain; observe RED.
- [x] Add/adapt Actions and minimal Gradle signing. Describe actual local setup/demo limitations and pending setup/QA.
- [x] Run complete Python suites, syntax/whitespace/JSON checks and review the full diff; expected PASS.
- [x] Create branch/PR against fresh main and inspect CI. Do not merge or perform Play mutations from this task.

## Follow-up verification

- [x] Automatic-only bounded polling for missing/pending screenshot QA; manual and terminal failure checks remain immediate.
- [x] Every poll rechecks current main; pre-build and pre-upload recheck latest CI/QA including reruns.
- [x] Latest run selection validates attempt start timestamp with created-at fallback only for attempt 1, then run ID/attempt; missing/invalid rerun timestamps fail closed.
- [x] Behavioral RED→GREEN tests cover pending/missing success, timeout, terminal failure, stale main, manual behavior, one auto trigger and older-ID newer-attempt freshness.
