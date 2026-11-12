#!/usr/bin/env python3
import argparse
import base64
import binascii
import json
import os
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="Install the HomeCrew Firebase Android configuration.")
    parser.add_argument("--required", action="store_true")
    args = parser.parse_args()
    encoded = os.environ.get("HOMECREW_GOOGLE_SERVICES_JSON_BASE64")
    if not encoded:
        if args.required:
            parser.exit(1, "HomeCrew Firebase configuration is required for this build.\n")
        print("Using the local-only HomeCrew build configuration.")
        return

    try:
        raw = base64.b64decode(encoded, validate=True)
        config = json.loads(raw)
        project = config["project_info"]
        clients = [
            client for client in config["client"]
            if client["client_info"]["android_client_info"]["package_name"] == "com.hebi.homecrew"
        ]
        client, = clients
        app_id = client["client_info"]["mobilesdk_app_id"].split(":")
        if project["project_id"] != "homecrew-1b14b" or app_id[1:3] != [project["project_number"], "android"]:
            raise ValueError
        if not any(key.get("current_key") for key in client["api_key"]):
            raise ValueError
    except (AttributeError, binascii.Error, KeyError, TypeError, ValueError):
        parser.exit(1, "Firebase configuration does not match the registered HomeCrew Android app.\n")

    path = Path(__file__).resolve().parents[1] / "app" / "google-services.json"
    path.write_bytes(raw)
    print("Installed the HomeCrew Android Firebase configuration.")


if __name__ == "__main__":
    main()
