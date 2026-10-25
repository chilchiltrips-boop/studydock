#!/usr/bin/env python3
"""Validate draft metadata and encrypted-file integrity, not clinical clearance."""

from collections import Counter
import hashlib
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
PASS = {"CLINICAL_REVIEW_PASS", "CORRECTED_AND_REVIEWED"}
MAPPED = "AI_INC_FIVE_LEVEL_MAPPING_REVIEW_PASS"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(name):
    return json.loads((HERE / name).read_text())


def verify():
    progress = load("review-progress.json")
    manifest = load("draft-manifest.json")
    rows = [json.loads(line) for line in (HERE / "question-review-ledger.jsonl").read_text().splitlines()]
    ids = [row["id"] for row in rows]
    require(len(rows) == len(set(ids)) == progress["sourceEntries"] == 2894, "Question ID count mismatch")
    indexed = {row["id"]: row for row in rows}
    require(Counter(row["dataset"] for row in rows) == {
        "SAUNDERS_RN_9": 1248, "DELMAR_PN_2": 1546, "SAUNDERS_IMAGE_100": 100,
    }, "Dataset counts mismatch")
    require(Counter(row["exam"] for row in rows) == {"NCLEX-RN": 1348, "NCLEX-PN": 1546}, "Exam counts mismatch")
    ledger_keys = {
        "id", "exam", "dataset", "sourceRecordSHA256", "prior351", "candidateDraftPresent",
        "individuallyScreened", "clinicalStatus", "reviewerType", "humanExpertSignoff",
        "incMappingStatus", "officialINCUnitVerified", "previousINCMappingFlag", "duplicateGroup",
        "semanticDuplicateReview", "imageRequired", "sourceStructuralIssues", "unresolved",
        "eligibleForScoredQuiz", "all12FinalQAPass",
    }
    for row in rows:
        require(set(row) == ledger_keys, "Unexpected public ledger field")
        require(bool(re.fullmatch(r"[a-f0-9]{64}", row["sourceRecordSHA256"])), "Invalid source hash")
        require(row["clinicalStatus"] in PASS | {"CLINICAL_REVIEW_PENDING"}, "Unknown clinical status")
        require(row["reviewerType"] in (None, "AI"), "Unexpected reviewer type")
        require(not row["humanExpertSignoff"] and not row["eligibleForScoredQuiz"] , "Draft release exclusion violated")
        require(row["officialINCUnitVerified"] == (row["incMappingStatus"] == MAPPED), "INC status mismatch")
        if row["clinicalStatus"] in PASS:
            require(row["reviewerType"] == "AI" and row["individuallyScreened"], "Unsupported review-pass metadata")
    totals = {
        "priorProcessedBatch": sum(row["prior351"] for row in rows),
        "individuallyScreenedCandidates": sum(row["individuallyScreened"] for row in rows),
        "aiClinicalContentPass": sum(row["clinicalStatus"] in PASS for row in rows),
        "clinicalContentPending": sum(row["clinicalStatus"] not in PASS for row in rows),
        "priorBatchClinicalContentPending": sum(row["prior351"] and row["clinicalStatus"] not in PASS for row in rows),
        "outsidePriorBatchNotIndividuallyScreened": sum(not row["prior351"] and not row["individuallyScreened"] for row in rows),
        "reviewedINCFiveLevelMappings": sum(row["incMappingStatus"] == MAPPED for row in rows),
        "incFiveLevelMappingsPending": sum(row["incMappingStatus"] != MAPPED for row in rows),
        "previousQuestionableMappings": sum(row["previousINCMappingFlag"] == "YES" for row in rows),
        "previousMappingFlagsResolvedByAI": sum(row["previousINCMappingFlag"] == "YES" and row["incMappingStatus"] == MAPPED for row in rows),
    }
    for field, total in totals.items():
        require(progress[field] == total, f"Progress count mismatch: {field}")
    require(progress["previousMappingFlagsPending"] == totals["previousQuestionableMappings"] - totals["previousMappingFlagsResolvedByAI"], "Flag count mismatch")
    require(all(progress[field] == 0 for field in ["independentHumanClinicianSignoffs", "eligibleForScoredQuiz", "newImageQuestionVisualQAComplete"]), "Unsupported clearance claim")
    require(progress["finalProjectComplete"] is False and manifest["finalRelease"] is False, "Final release flag set")
    mappings = load("reviewed-inc-mappings.json")
    mapped_ids = {row["id"] for row in rows if row["incMappingStatus"] == MAPPED}
    require(len(mappings) == len(mapped_ids) == progress["reviewedINCFiveLevelMappings"] and {row["id"] for row in mappings} == mapped_ids, "INC mapping ID mismatch")
    for item in mappings:
        require(all(item.get(field) for field in ["subject", "unit", "main_topic", "subtopic", "specific_learning_objective"]), "Incomplete INC hierarchy")
        require(item["official_unit_verified"] and item["reviewer_type"] == "AI" and not item["human_expert_signoff"], "Invalid mapping evidence flags")
        require(item["curriculum_evidence"]["pdf_pages"] and item["curriculum_evidence"]["evidence"]["url"].startswith("https://indiannursingcouncil.org/"), "Missing official INC locator")
    references = load("clinical-reference-registry.json")
    reference_ids = {item["id"] for item in references}
    require(len(reference_ids) == len(references), "Reference registry ID mismatch")
    for item in references:
        require(all(item.get(field) for field in ["id", "title", "url", "accessed", "support_location"]), "Missing reference provenance")
    history = load("correction-history.json")
    require(len(history) == len({item["id"] for item in history}) == progress["explicitCurationDecisions"], "Curation history mismatch")
    for item in history:
        require(item["id"] in indexed and item["clinical_status"] == indexed[item["id"]]["clinicalStatus"], "Curation status mismatch")
        require(item["reviewer_type"] == "AI" and not item["human_signoff"], "Invalid review attribution")
        require(set(item["sources"]).issubset(reference_ids), "Unresolved reference ID")
        if item["clinical_status"] in PASS:
            require(bool(item["sources"]), "Reviewed item lacks reference IDs")
    groups = load("exact-duplicate-groups.json")
    require(len(groups) == 1 and set(groups[0]["ids"]) == {"PN2-CH13-Q027", "PN2-CH19-Q021"}, "Known duplicate pair missing")
    for item in groups:
        require(len({indexed[qid]["duplicateGroup"] for qid in item["ids"]}) == 1 and all(indexed[qid]["duplicateGroup"] for qid in item["ids"]), "Duplicate group link mismatch")
    decisions = load("duplicate-pair-decisions.json")
    require(len(decisions) == 4, "Preserved known-pair decision count mismatch")
    for item in decisions:
        require(item["reviewer_type"] == "AI" and not item["human_expert_signoff"], "Invalid pair attribution")
        require(all(indexed[qid]["duplicateGroup"] == item["group_id"] for qid in item["ids"]), "Pair group mismatch")
    finalqa = load("final-qa-100-summary.json")
    qa_ids = {x["id"] for x in finalqa["items"]}
    require(len(qa_ids) == len(finalqa["items"]) == progress["all12FinalQAPass"] == 100, "Final AI QA item count mismatch")
    require(qa_ids == {r["id"] for r in rows if r["all12FinalQAPass"]}, "Final AI QA ledger mismatch")
    require(len(finalqa["pairDecisions"]) == progress["aiPairAdjudications"] == 721, "Individual relationship decision count mismatch")
    require(len({tuple(d["ids"]) for d in finalqa["pairDecisions"]}) == 721, "Repeated relationship decisions")
    for x in finalqa["items"]:
        row = indexed[x["id"]]
        require(x["checksPassed"] == list(range(1,13)) and x["reviewerType"] == "AI" and not x["humanSignoff"], "Unsupported final QA attribution")
        require(row["clinicalStatus"] in PASS and row["officialINCUnitVerified"] and not row["unresolved"] and not row["imageRequired"], "Unresolved item assigned full QA")
        require(x["sourceRecordSHA256"] == row["sourceRecordSHA256"] and len(x["sourcePDFSHA256"]) == 64 and set(x["referenceIDs"]) <= reference_ids, "Final QA evidence mapping mismatch")
    require(progress["all12FinalQAPending"] == 2894-len(qa_ids), "Final QA pending count mismatch")
    require(progress["finalQARetainedStudyEntries"] == 95 and progress["finalQARedundantArchiveEntries"] == 5, "Final QA retention count mismatch")
    for d in finalqa["pairDecisions"]:
        require(set(d["ids"]) & qa_ids and d["reviewerType"] == "AI" and not d["humanSignoff"], "Invalid relationship evidence")
    relationship_groups = load("duplicate-relationship-groups.json")
    require(len(relationship_groups) == 24, "Relationship group count mismatch")
    for g in relationship_groups:
        require(all(indexed[q]["duplicateGroup"] == g["group_id"] for q in g["ids"]), "Relationship group link mismatch")
        require(not g["human_expert_signoff"] and not g["final_release_authorized"], "Unsupported group certification")
    forbidden = {"keyBase64", "original_question", "original_key", "question", "stem", "options", "correct_answer", "rationale", "candidate_stem", "candidate_options"}
    def check_public(value):
        if isinstance(value, dict):
            require(not set(value).intersection(forbidden), "Private content field in public metadata")
            for child in value.values():
                check_public(child)
        elif isinstance(value, list):
            for child in value:
                check_public(child)
    for file in HERE.glob("*.json"):
        check_public(json.loads(file.read_text()))
    check_public(rows)
    parts = manifest["encryptedParts"]
    require(len(parts) == len({part["file"] for part in parts}) == 4, "Encrypted part list mismatch")
    pieces = []
    for part in parts:
        require(Path(part["file"]).name == part["file"], "Invalid part filename")
        data = (HERE / part["file"]).read_bytes()
        require(len(data) == part["bytes"] and hashlib.sha256(data).hexdigest() == part["sha256"], "Encrypted part checksum mismatch")
        pieces.append(data)
    data = b"".join(pieces)
    require(len(data) == manifest["encryptedBytes"], "Encrypted size mismatch")
    require(hashlib.sha256(data).hexdigest() == manifest["encryptedSHA256"], "Encrypted checksum mismatch")
    require(data[:35] == b"STUDYDOCK-NCLEX-DRAFT-AES256GCM-V1\x00", "Encryption header mismatch")
    continuation = load("continuation-manifest.json")
    require(continuation["kind"] == "encrypted-review-continuation" and not continuation["finalRelease"], "Invalid continuation type")
    parts = continuation["encryptedParts"]
    require(len(parts) >= 1 and len(parts) == len({p["file"] for p in parts}), "Invalid continuation parts")
    pieces = []
    for part in parts:
        require(Path(part["file"]).name == part["file"], "Invalid continuation filename")
        data = (HERE / part["file"]).read_bytes()
        require(len(data) == part["bytes"] and hashlib.sha256(data).hexdigest() == part["sha256"], "Continuation part mismatch")
        pieces.append(data)
    data = b"".join(pieces)
    require(len(data) == continuation["encryptedBytes"] and hashlib.sha256(data).hexdigest() == continuation["encryptedSHA256"], "Continuation checksum mismatch")
    require(data[:35] == b"STUDYDOCK-NCLEX-DRAFT-AES256GCM-V1\x00", "Continuation header mismatch")
    return {"structuralIntegrityPass": True, "entries": len(rows), "aiClinicalContentPass": progress["aiClinicalContentPass"],
            "reviewedINCMappings": len(mappings), "explicitCurationDecisions": len(history), "references": len(references),
            "humanSignoffs": 0, "all12FinalQAPass": progress["all12FinalQAPass"], "quizEligible": 0,
            "encryptedSHA256": manifest["encryptedSHA256"], "continuationSHA256":continuation["encryptedSHA256"], "clinicalCertification": False}


if __name__ == "__main__":
    print(json.dumps(verify(), indent=2))
