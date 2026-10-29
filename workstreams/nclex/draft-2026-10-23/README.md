# NCLEX 2,894 — draft checkpoint, 29 October 2026

The project is incomplete. This branch stores recoverable work and review metadata.

| Measure | Actual count |
| --- | ---: |
| Source entries accounted for | 2,894 |
| Candidates individually screened | 400 |
| Explicit curation records | 400 |
| AI clinical-content reviews passed | 400 |
| Clinical-content review pending | 2494 |
| INC five-level AI mappings reviewed | 400 |
| INC mappings pending | 2494 |
| Similarity candidate pairs | 11899 |
| AI pair adjudications | 3552 |
| Independent human clinician sign-offs | 0 |
| All 12 individual AI QA checks passed | 400 |
| Retained study entries within that QA batch | 385 |
| Redundant source entries archived within that QA batch | 15 |
| Scored-quiz eligible / released | 0 |
| New image-question visual QA completed | 0 of 100 |

AI clinical-content review does not certify final release readiness. Similarity retrieval
does not establish duplicate equivalence. The final semantic unique count is unknown.
All351 prior screened entries now have complete AI QA;49 additional entries are included.
The remaining2,494 entries still need individual current-guidance review and all12 QA.
Clinical and all12 counts overlap; do not add them. This batch adds81 clinical reviews,
19 previously clinically reviewed entries and100 complete QA records. Future batches integrate
clinical review, source trace, curriculum, references, duplicate adjudication and
all 12 QA checks. No intermediate study or quiz release is authorized.

Question text, working answers and curation code are encrypted. Public files contain
IDs, status metadata, project curriculum labels, references and correction hashes.

The original four-part encrypted base checkpoint is unchanged. The new encrypted
continuation contains the current work state. Use the existing private recovery key:

```sh
python3 decrypt_backup.py --key-file /private/key.json --output /private/base.zip
python3 decrypt_backup.py --key-file /private/key.json --manifest continuation-manifest.json --output /private/continuation.zip
```

Recover outside this public repository. Extract the base into a private workspace,
then overlay the continuation there. Source figures and original PDF text caches remain
in the base. Full third-party guideline documents are referenced by URLs and hashes,
and are not redistributed in the continuation. Historical answers remain separate.

Keep this PR in draft. Main, application code and scored quizzes are outside this change.
