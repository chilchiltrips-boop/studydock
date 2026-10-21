# NCLEX / INC topic-wise archive — PRIVATE on-device import

StudyDock can browse the user's existing NCLEX extracted bank on Android **without distributing textbook content from this public GitHub repository**.

The source set contains **2,894 entries**: 1,248 Saunders NCLEX-RN originals, 1,546 Delmar NCLEX-PN originals, and 100 image-based adaptations. A confirmed duplicate pair remains as two source-linked entries; no record is silently dropped.

The currently reconciled working master includes **451 entries with rewritten-question fields** and **2,443 historical-source-only entries**. These counts describe text fields, **not 451 completed or clinically cleared reviews**. The previous item-level QA project documented 351 processed records. Clinical final sign-off is NOT established for all questions; the local archive therefore disables scoring for every imported NCLEX entry. Historical keys are stored separately and not shown as current recommendations. INC classification still has **623 headings** flagged for human confirmation.

## Import instructions
1. On a trusted private computer, provide the three source arguments to the builder. Do not put source books, derived full-text banks or image extracts into this public repository.
2. Run:

```sh
python3 scripts/build_nclex_local_pack.py \
  --source NCLEX_2894_All_Questions_Full.json \
  --master NCLEX_2894_Master_351_Updates_NOT_FINAL.csv \
  --images-zip NCLEX_2894_All_In_One_Database.zip \
  --output StudyDock_NCLEX_2894_PRIVATE_Local_Import.zip
```

3. Copy the generated ZIP to your Android device, open StudyDock → **NCLEX archive** on Home → **Import / replace private NCLEX ZIP**.
4. Browse subject, exam (RN/PN), INC topic, ID, images and working draft rationales. A question's draft key is explicitly labeled provisional.

The importer validates all 2,894 IDs, item/asset checksums, safe paths and ZIP limits. It preserves the existing 8-part textbook library and 10-level guided lessons. Failed NCLEX imports do not intentionally replace an existing archive.

## Rights and clinical limitations
- Original textbook extracts and images may be copyrighted. Publish them only with appropriate distribution rights.
- Clinical references attached to drafts vary in scope. A reference URL does **not** imply independent clinical certification.
- This is a **read-only working archive**, not an examination or clinical-care protocol. Scored quiz inclusion requires a separate question-by-question current-guideline verification pipeline.
- Archive answer keys are kept solely for traceability and are never promoted as current-correct by the Android reader.
- No APK has been compiled or field tested by this data import alone.
