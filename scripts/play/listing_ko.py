#!/usr/bin/env python3
"""HomeCrew Korean Google Play listing text-only read/apply helper."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from publisher import HttpTransport, Publisher, ReleaseError, PACKAGE

LOCALE = "ko-KR"
DEFAULT = Path(__file__).resolve().parents[2] / "docs/play-store/listing-ko.json"
LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000, "releaseNotes": 500}


def load_listing(path=DEFAULT):
    data = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if not isinstance(data, dict) or data.get("language") != LOCALE or data.get("title") != "HomeCrew":
        raise ValueError("Expected HomeCrew ko-KR listing")
    for field, maximum in LIMITS.items():
        value = data.get(field)
        if not isinstance(value, str) or not value.strip() or len(value) > maximum:
            raise ValueError(f"Invalid listing field: {field}")
    if any(phrase in data["fullDescription"] for phrase in
           ("실행 중 메모리에만", "프로세스 종료 후 초기화", "영구 저장은 아직 제공하지")):
        raise ValueError("Outdated memory-only claims")
    return data


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def other_locales_equal(first, second):
    def others(payload):
        return sorted([v for v in payload.get("listings", []) if v.get("language") != LOCALE],
                      key=canonical)
    return canonical(others(first)) == canonical(others(second))


def text_matches(remote, target):
    return all(remote.get(k) == target[k] for k in ("shortDescription", "fullDescription"))


def run(mode, path=DEFAULT, token=None):
    target = load_listing(path)
    result = {
        "package": PACKAGE,
        "locale": LOCALE,
        "mode": mode,
        "title": target["title"],
        "shortDescriptionLength": len(target["shortDescription"]),
        "fullDescriptionLength": len(target["fullDescription"]),
        "sourceSHA256": hashlib.sha256(Path(path).read_bytes()).hexdigest(),
        "changedReleaseOrImages": False
    }
    if mode == "validate":
        result["status"] = "source_validated_no_play_access"
        return result
    if mode not in ("check", "apply"):
        raise ValueError("Unsupported mode")
    if mode == "apply" and os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ReleaseError("Only the trusted main workflow may apply a listing")
    transport = HttpTransport(token if token is not None else os.environ.get("PLAY_ACCESS_TOKEN", ""))
    publisher = Publisher(transport)
    committed = False
    with publisher.edit() as edit:
        current = transport("GET", edit + "/listings/" + LOCALE)
        if current.get("language") != LOCALE:
            raise ReleaseError("Google Play listing language mismatch")
        before_listings = transport("GET", edit + "/listings")
        before_tracks = publisher.inventory(edit)
        result["currentShortDescription"] = current.get("shortDescription")
        result["matchesRequestedText"] = text_matches(current, target)
        result["preserveTitle"] = current.get("title")
        result["preserveVideo"] = bool(current.get("video"))
        if mode == "check":
            result["status"] = "read_only_listing_verified"
            return result
        if text_matches(current, target):
            result["status"] = "already_matches_no_commit"
            return result
        transport("PATCH", edit + "/listings/" + LOCALE,
                  {"shortDescription": target["shortDescription"],
                   "fullDescription": target["fullDescription"]})
        staged = transport("GET", edit + "/listings/" + LOCALE)
        if not text_matches(staged, target):
            raise ReleaseError("Staged Play listing text differs from intended content")
        for key in ("language", "title", "video"):
            if staged.get(key) != current.get(key):
                raise ReleaseError("Unexpected title, language or video edit")
        if not other_locales_equal(before_listings, transport("GET", edit + "/listings")):
            raise ReleaseError("Other language listing modified")
        if canonical(before_tracks) != canonical(publisher.inventory(edit)):
            raise ReleaseError("Play tracks or binary inventory modified")
        transport("POST", edit + ":validate", {})
        transport("POST", edit + ":commit?changesNotSentForReview=true&changesInReviewBehavior=ERROR_IF_IN_REVIEW", {})
        committed = True
    try:
        with publisher.edit() as edit:
            latest = transport("GET", edit + "/listings/" + LOCALE)
            if not text_matches(latest, target):
                raise ReleaseError("Fresh Play listing readback differs")
            for key in ("language", "title", "video"):
                if latest.get(key) != current.get(key):
                    raise ReleaseError("Protected listing fields differ after commit")
            if not other_locales_equal(before_listings, transport("GET", edit + "/listings")):
                raise ReleaseError("Other language differs after commit")
            if canonical(before_tracks) != canonical(publisher.inventory(edit)):
                raise ReleaseError("Play tracks or binaries differ after commit")
    except ReleaseError as error:
        if committed:
            raise ReleaseError("Play edit committed but fresh readback failed; inspect Console before retrying") from error
        raise
    result.update({"status": "api_committed_and_fresh_readback_verified",
                   "matchesRequestedText": True,
                   "reviewStatus": "requires_console_confirmation",
                   "commitMode": "not_sent_for_review"})
    return result


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--mode", choices=("validate", "check", "apply"), default="validate")
    p.add_argument("--report", default="output/homecrew-listing-ko-asset02.json")
    args = p.parse_args()
    try:
        report = run(args.mode)
    except (ValueError, OSError, ReleaseError, KeyError, TypeError) as error:
        print(f"HomeCrew listing operation blocked: {type(error).__name__}: {error}", file=sys.stderr)
        return 1
    path = Path(args.report)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
