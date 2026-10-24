# NCLEX 2,894 — draft checkpoint, 24 October 2026

The project is incomplete. This branch stores recoverable work and review metadata.

| Measure | Actual count |
| --- | ---: |
| Source entries accounted for | 2,894 |
| Prior candidates individually screened | 351 |
| Explicit curation records | 240 |
| AI clinical-content reviews passed | 219 |
| Clinical-content review pending | 2675 |
| INC five-level AI mappings reviewed | 219 |
| INC mappings pending | 2675 |
| Similarity candidate pairs | 11333 |
| AI pair adjudications | 4 |
| Independent human clinician sign-offs | 0 |
| All 12 final QA checks passed / scored-quiz eligible | 0 |
| New image-question visual QA completed | 0 of 100 |

AI clinical-content review does not certify final release readiness. Similarity retrieval
does not establish duplicate equivalence. The final semantic unique count is unknown.
The remaining 2,543 questions outside the prior batch have not been individually screened.

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
