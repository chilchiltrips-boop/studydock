# NCLEX 2,894 — draft checkpoint, 26 October 2026

The project is incomplete. This branch stores recoverable work and review metadata.

| Measure | Actual count |
| --- | ---: |
| Source entries accounted for | 2,894 |
| Prior candidates individually screened | 351 |
| Explicit curation records | 324 |
| AI clinical-content reviews passed | 319 |
| Clinical-content review pending | 2575 |
| INC five-level AI mappings reviewed | 322 |
| INC mappings pending | 2572 |
| Similarity candidate pairs | 11603 |
| AI pair adjudications | 2655 |
| Independent human clinician sign-offs | 0 |
| All 12 individual AI QA checks passed | 300 |
| Retained study entries within that QA batch | 291 |
| Redundant source entries archived within that QA batch | 9 |
| Scored-quiz eligible / released | 0 |
| New image-question visual QA completed | 0 of 100 |

AI clinical-content review does not certify final release readiness. Similarity retrieval
does not establish duplicate equivalence. The final semantic unique count is unknown.
The remaining 2,543 questions outside the prior batch have not been individually screened.
The 300 full-QA entries were already included in the 319 clinical-content reviews;
they are not counted as 300 additional clinical reviews. Future batches integrate
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
