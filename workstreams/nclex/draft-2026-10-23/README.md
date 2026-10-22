# NCLEX 2,894 — draft review checkpoint, 23 October 2026

This preserves the work in progress. The full project is incomplete.

| Measure | Accounted progress |
| --- | ---: |
| Source entries preserved | 2,894 |
| Existing candidates individually screened | 351 |
| Explicit curation decisions | 146 |
| AI clinical-content answer/rationale reviews passed | 119 |
| Clinical-content review still pending | 2775 |
| INC five-level mappings individually reviewed by AI | 125 |
| INC five-level mapping still pending | 2769 |
| Independent human clinician sign-offs | 0 |
| All 12 final QA checks passed / scored-quiz eligible | 0 |

Of the original 351-item batch, 232 items still need clinical-content clearance.
The 2,543 entries outside that batch have not yet been individually screened.
No final XLSX, CSV, JSON, study PDF or quiz release has been produced.

Remaining checks include all 100 new image-based questions, 18 source questions
whose options need figure transcription, the source-key mapping for
`S9-C08-Q007`, 23 source-PDF trace exceptions, full semantic duplicate
adjudication and final release QA. Of 623 earlier mapping flags, 9 have AI
mapping decisions and 614 remain open. All original entries remain archived.
There are 2,893 exact normalized source stems; the final semantic unique count
is not established.

## Files and recovery

The JSON/JSONL files expose progress, question IDs, project topic mappings,
public clinical references and correction hashes. They do not expose question
stems, options, answer keys, rationales or source figures.

The four `nclex-working-checkpoint.enc.part001` through `.part004` files contain
the complete private working archive, encrypted with AES-256-GCM and split for
upload. The recovery script checks and assembles them automatically. This follows this public repository's existing
private-import policy. The recovery key is provided separately to the owner.
Keep it outside this Git repository.

```sh
python3 -m pip install -r requirements.txt
python3 verify_draft.py
python3 decrypt_backup.py \
  --key-file /private/StudyDock_NCLEX_Draft_Recovery_Key.json \
  --output /private/NCLEX_2894_RECOVERED_WORKING_NOT_FINAL.zip
```

Extract the recovered ZIP in a private directory, read its recovery README and
run `python3 nclex_project/scripts/compile_checkpoint.py`. The original textbook
PDFs are held separately; their identities and hashes are in the private
archive. All extracted working data and supporting figure files needed to
resume are preserved in the encrypted archive.

AI clinical-content review and automated schema checks do not constitute
independent human certification. The clinical candidates remain provisional
until all release checks are complete. Historical answers remain separate from
current candidate answers, and every item is excluded from scored quizzes.

This branch adds storage and review metadata only. The existing app, content
catalog, Android assets and import behavior are unchanged. No merge or
publication is requested by this checkpoint.
