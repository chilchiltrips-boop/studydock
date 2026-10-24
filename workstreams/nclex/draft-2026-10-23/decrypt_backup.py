#!/usr/bin/env python3
"""Recover the private, incomplete checkpoint using its separately held key."""

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import zipfile

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

HEADER = b"STUDYDOCK-NCLEX-DRAFT-AES256GCM-V1\x00"


def recover(archive, key_file, output, manifest_file):
    manifest = json.loads(manifest_file.read_text())
    key_record = json.loads(key_file.read_text())
    if key_record.get("format") != "studydock-nclex-draft-key-v1":
        raise ValueError("Unsupported recovery-key format")
    key = base64.b64decode(key_record["keyBase64"], validate=True)
    if len(key) != 32:
        raise ValueError("Recovery key must be 32 bytes")
    output = output.resolve()
    if output.exists():
        raise ValueError("Output already exists; choose a new private path")
    for parent in Path(__file__).resolve().parents:
        if (parent / ".git").exists():
            if output.is_relative_to(parent):
                raise ValueError("Recover outside the Git repository")
            break
    if archive is None:
        pieces = []
        for part in manifest["encryptedParts"]:
            name = part["file"]
            if Path(name).name != name:
                raise ValueError("Invalid checkpoint-part filename")
            piece = (manifest_file.parent / name).read_bytes()
            if len(piece) != part["bytes"] or hashlib.sha256(piece).hexdigest() != part["sha256"]:
                raise ValueError("Encrypted checkpoint-part checksum mismatch")
            pieces.append(piece)
        data = b"".join(pieces)
    else:
        data = archive.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    continuation = manifest.get("kind") == "encrypted-review-continuation"
    if digest != manifest["encryptedSHA256"] or (not continuation and digest != key_record["encrypted_sha256"]):
        raise ValueError("Encrypted checkpoint checksum mismatch")
    if len(data) != manifest["encryptedBytes"] or not data.startswith(HEADER):
        raise ValueError("Encrypted checkpoint header or length mismatch")
    nonce = data[len(HEADER):len(HEADER) + 12]
    plaintext = AESGCM(key).decrypt(nonce, data[len(HEADER) + 12:], HEADER)
    plain_digest = hashlib.sha256(plaintext).hexdigest()
    if plain_digest != manifest["plainArchiveSHA256"] or (not continuation and plain_digest != key_record["decrypted_zip_sha256"]):
        raise ValueError("Recovered checkpoint checksum mismatch")
    with zipfile.ZipFile(io.BytesIO(plaintext)) as recovered:
        bad = recovered.testzip()
        if bad:
            raise ValueError("Recovered ZIP has a damaged member")
        member_count = len(recovered.namelist())
    output.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as destination:
            destination.write(plaintext)
            destination.flush()
            os.fsync(destination.fileno())
    except BaseException:
        output.unlink(missing_ok=True)
        raise
    return {"recovered": True, "bytes": len(plaintext), "sha256": plain_digest,
            "zipMembersCRCChecked": member_count, "finalRelease": False}


def main():
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--key-file", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--archive", type=Path, help="Optional already-assembled encrypted file; otherwise load manifest parts")
    parser.add_argument("--manifest", type=Path, default=here / "draft-manifest.json")
    args = parser.parse_args()
    try:
        result = recover(args.archive, args.key_file, args.output, args.manifest)
    except InvalidTag:
        print("Recovery failed: authentication failed; no output written", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print(f"Recovery failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
