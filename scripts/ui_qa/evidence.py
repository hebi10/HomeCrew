"""Collect Android QA evidence without treating fixture or failed-run PNGs as verified screens."""
import argparse
import hashlib
import json
import re
import shutil
import struct
from pathlib import Path

# P01–P27 follow the 2026-11-25 v1.2 design specification, not legacy HC IDs.
PAGES = {
    "P01": ("시작 안내", ("00-setup-intro",)),
    "P02": ("집 생성", ("00-setup-household",)),
    "P03": ("초기 구성원", ("00-setup-members",)),
    "P04": ("템플릿", ("00-setup-templates",)),
    "P05": ("홈", ("01-home-light", "07-home-dark", "05-home-member-overview")),
    "P06": ("월간 일정", ("02-schedule-month-light", "02-schedule-month-dark")),
    "P07": ("주간 일정", ("02-schedule-light", "02-schedule-dark")),
    "P08": ("일간 일정", ("02-schedule-day-light", "02-schedule-day-dark")),
    "P09": ("집안일 목록", ("03-chores-light", "03-chores-dark")),
    "P10": ("집안일 등록·수정", ("08-chore-add", "12-chore-edit")),
    "P11": ("반복 설정", ("09-chore-repeat", "09-chore-repeat-weekly")),
    "P12": ("담당자 설정", ("10-chore-assignment", "10-chore-assignment-round-robin", "10-chore-assignment-balanced")),
    "P13": ("집안일 상세", ("11-chore-detail", "11-chore-detail-completed", "11-chore-detail-skipped")),
    "P14": ("통계", ("04-stats-light", "04-stats-dark")),
    "P15": ("로컬 구성원 관리", ("P15-local-members", "P15-local-members-light")),
    "P16": ("구성원 편집", ("P16-member-edit",)),
    "P17": ("공동 공간 관리", ("P17-shared-list-fake",)),
    "P18": ("초대·참여", ("P18-invite-fake",)),
    "P19": ("공동 구성원·소유권", ("P19-roster-fake", "P19-transfer-confirm-fake", "P19-member-fake")),
    "P20": ("공동 일정·작업 관리", ("P20-shared-chore-fake", "P20-offline-fake")),
    "P21": ("자료 이전 사전검사", ("P21-migration-blocked-fake", "P21-migration-ready-fake")),
    "P22": ("자료 이전 진행", ("P22-publishing-fake", "P22-rollback-confirm-fake")),
    "P23": ("백업·복원", ("13-local-backup-restore",)),
    "P24": ("계정", ("14-account-section-light",)),
    "P25": ("계정 삭제 사전 확인", ("P25-deletion-review-fake", "P25-deletion-blocked-fake")),
    "P26": ("알림", ("P26-reminder-light",)),
    "P27": ("앱 설정", ("P27-settings-hub", "13-settings-light", "13-settings-dark")),
}
CLOUD_PAGES = {f"P{i:02}" for i in range(17, 23)} | {"P25"}
BY_NAME = {stem: page for page, (_, stems) in PAGES.items() for stem in stems}


def png_dimensions(path):
    with path.open("rb") as stream:
        header = stream.read(24)
    if (len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n"
            or header[8:16] != b"\x00\x00\x00\rIHDR"):
        return None
    width, height = struct.unpack(">II", header[16:24])
    return (width, height) if width and height else None


def collect(source, destination, sha, device, font_scale, test_exit):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Expected full checkout SHA")
    destination.mkdir(parents=True, exist_ok=True)
    if any(destination.iterdir()):
        raise ValueError("Evidence destination is not empty; do not mix runs")
    records = []
    for path in sorted(source.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".png", ".xml"}:
            continue
        relative = path.relative_to(source)
        stem = path.stem
        is_fixture = "screen-qa-fake-gateway" in relative.parts or stem.endswith("-fake")
        dimensions = png_dimensions(path) if path.suffix.lower() == ".png" else None
        if test_exit or stem.startswith(("error-", "hierarchy-")) or dimensions is None:
            kind = "diagnostic"
        elif is_fixture:
            kind = "fixture"
        elif "screen-qa" in relative.parts:
            kind = "app"
        else:
            kind = "unclassified"
        group = destination if kind == "app" else destination / kind
        group.mkdir(parents=True, exist_ok=True)
        target = group / path.name
        if target.exists():
            suffix = hashlib.sha256(relative.as_posix().encode()).hexdigest()[:16]
            target = group / f"{stem}-{suffix}{path.suffix}"
            if target.exists():
                raise ValueError(f"Duplicate evidence destination: {target.name}")
        shutil.copyfile(path, target)
        page = BY_NAME.get(stem)
        records.append({
            "source": relative.as_posix(), "file": target.relative_to(destination).as_posix(),
            "kind": kind, "fixture_source": is_fixture, "page": page,
            "related_page": "P20" if stem == "P20-owner-settings-fake" else None,
            "dimensions": list(dimensions) if dimensions else None,
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        })
    coverage = []
    for page, (title, _) in PAGES.items():
        direct = [r for r in records if r["page"] == page]
        app = [r["file"] for r in direct if r["kind"] == "app"]
        fixture = [r["file"] for r in direct if r["kind"] == "fixture"]
        coverage.append({
            "id": page, "title": title, "app_evidence": app, "fixture_evidence": fixture,
            "related_only": [r["file"] for r in records if r["related_page"] == page],
            "status": ("app_capture" if app else "fixture_only" if fixture else "not_captured"),
            "requires_nonproduction_server_qa": page in CLOUD_PAGES,
            "visual_review": "pending",
        })
    verified_app_count = sum(r["kind"] == "app" for r in records)
    manifest = {
        "source_sha": sha, "device": device, "font_scale": font_scale,
        "instrumentation_exit": test_exit, "instrumentation_passed": test_exit == 0,
        "app_capture_count": verified_app_count,
        "fixture_capture_count": sum(r["kind"] == "fixture" for r in records),
        "diagnostic_count": sum(r["kind"] == "diagnostic" for r in records),
        "unclassified_count": sum(r["kind"] == "unclassified" for r in records),
        "pages": coverage, "files": records,
    }
    (destination / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--device", required=True)
    parser.add_argument("--font-scale", required=True)
    parser.add_argument("--test-exit", type=int, required=True)
    args = parser.parse_args()
    result = collect(args.source, args.destination, args.sha, args.device, args.font_scale, args.test_exit)
    print(f"App captures: {result['app_capture_count']}; fixtures: {result['fixture_capture_count']}; diagnostics: {result['diagnostic_count']}")
    print("Design page evidence inventory: qa-screenshots/manifest.json (visual review pending)")


if __name__ == "__main__":
    main()
