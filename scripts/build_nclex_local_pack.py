#!/usr/bin/env python3
"""Build a PRIVATE NCLEX ZIP from user-owned local extracts. Never commit the output.
Requires the 2,894-question combined JSON, 2,894-row INC working master CSV,
and the combined ZIP with referenced source images.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import zipfile

def digest(data):
    return hashlib.sha256(data).hexdigest()

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--source", type=Path, required=True)
    p.add_argument("--master", type=Path, required=True)
    p.add_argument("--images-zip", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    with a.master.open(encoding="utf-8-sig", newline="") as f:
        master = list(csv.DictReader(f))
    original = json.loads(a.source.read_text(encoding="utf-8"))["questions"]
    lookup = {r["Question ID"]: r for r in master}
    if len(lookup) != 2894 or len(master) != 2894 or len(original) != 2894:
        raise ValueError("Expected 2,894 unique master and source entries")
    if set(lookup) != {r["id"] for r in original}:
        raise ValueError("Master/source ID reconciliation failed")
    out = []
    for old in original:
        row = lookup[old["id"]]
        draft = row.get("Current Rewritten Question", "").strip()
        def options(items):
            return [{"id": str(x.get("value", x.get("id", ""))),
                     "text": str(x.get("text", x.get("label", "")))} for x in items]
        old_options = options(old.get("options", []))
        current = options(json.loads(row["Current Rewritten Options JSON"])) if draft else old_options
        answer = [x.strip() for x in re.split("[,;|]+", row.get("Rewritten Answer (Not Historical)", ""))
                  if x.strip()] if draft else []
        if draft and (not answer or not set(answer) <= {v["id"] for v in current}):
            raise ValueError("Invalid draft answer/options: " + old["id"])
        image = old.get("image_path") or ""
        out.append({
            "id": old["id"], "exam": old.get("exam", ""), "subject": row.get("INC Subject", ""),
            "unit": row.get("INC Unit", ""), "topic": row.get("INC Topic Heading", ""),
            "heading": row.get("INC Question Heading", ""),
            "mappingNeedsReview": row.get("INC Mapping Needs Human Review") == "YES",
            "mode": "working_rewrite" if draft else "historical_source",
            "qaStatus": row.get("Rewrite QA Status", ""),
            "clinicalSignoff": False, "eligibleForScoredQuiz": False,
            "stem": draft or old["question"], "options": current, "answer": answer,
            "rationale": row.get("Rewritten Rationale", "") if draft else "",
            "reference": row.get("Clinical Reference URL", ""),
            "sourcePage": old.get("source_pdf_page"), "sourceId": old.get("source_question_id", ""),
            "imagePaths": [image] if image else [],
            "historical": {"stem": old["question"], "options": old_options,
                "answer": [str(v) for v in old.get("answer_values", [])],
                "rationale": old.get("rationale", ""),
                "notice": "Source-era answer; not a verified current clinical recommendation"}
        })
    chunks = {}
    descriptors = []
    for i in range(0, len(out), 100):
        name = "nclex/records/%03d.json" % (i // 100)
        data = json.dumps(out[i:i+100], ensure_ascii=False, separators=(",", ":")).encode()
        chunks[name] = data
        descriptors.append({"path": name, "sha256": digest(data), "count": len(out[i:i+100])})
    assets = []
    paths = sorted({path for item in out for path in item["imagePaths"]})
    with zipfile.ZipFile(a.images_zip) as source:
        for name in paths:
            if not name.startswith("images/") or ".." in Path(name).parts:
                raise ValueError("Unsafe image path: " + name)
            data = source.read(name)
            chunks[name] = data
            assets.append({"path": name, "sha256": digest(data), "bytes": len(data)})
    manifest = {
        "schemaVersion": 1, "kind": "studydock-nclex-private-local",
        "count": 2894, "rnOriginal": 1248, "pnOriginal": 1546, "newImageBased": 100,
        "shards": descriptors, "assets": assets,
        "modes": {"working_rewrite": sum(x["mode"] == "working_rewrite" for x in out),
                  "historical_source": sum(x["mode"] == "historical_source" for x in out)},
        "clinicalFinalQaPassCount": 0, "releaseEligible": False,
        "notice": "Private source and draft archive only. NOT a clinical QA-cleared quiz bank."
    }
    chunks["nclex/manifest.json"] = json.dumps(manifest, indent=2).encode()
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        for name, data in sorted(chunks.items()):
            target.writestr(name, data)
    print("Private pack:", a.output, "records:", len(out),
          "working drafts:", manifest["modes"]["working_rewrite"], "images:", len(assets))

if __name__ == "__main__":
    main()
