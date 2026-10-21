import csv
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "scripts" / "build_nclex_local_pack.py"


class NclexPackTests(unittest.TestCase):
    """Entirely synthetic fixtures: no textbook content in public source."""
    def test_build_private_archive_and_gate_all_scores(self):
        with tempfile.TemporaryDirectory() as directory:
            d = Path(directory)
            source = d / "source.json"
            master = d / "master.csv"
            images = d / "images.zip"
            pack = d / "private.zip"
            items = []
            with master.open("w", newline="", encoding="utf-8") as f:
                columns = ["Question ID", "Current Rewritten Question", "Current Rewritten Options JSON",
                    "Rewritten Answer (Not Historical)", "Rewritten Rationale", "INC Subject", "INC Unit",
                    "INC Topic Heading", "INC Question Heading", "INC Mapping Needs Human Review",
                    "Rewrite QA Status", "Clinical Reference URL"]
                writer = csv.DictWriter(f, fieldnames=columns)
                writer.writeheader()
                for i in range(2894):
                    key = "SYNTH-%04d" % i
                    items.append({
                        "id": key, "exam": "NCLEX-RN" if i < 1348 else "NCLEX-PN",
                        "question": "Synthetic example number " + str(i),
                        "options": [{"value": "1", "text": "Example A"}, {"value": "2", "text": "Example B"}],
                        "answer_values": ["1"], "rationale": "Synthetic example only", "source_pdf_page": 1,
                    })
                    draft = i < 3
                    writer.writerow({
                        "Question ID": key,
                        "Current Rewritten Question": "Revised synthetic case" if draft else "",
                        "Current Rewritten Options JSON": json.dumps([
                            {"value": "1", "text": "Current A"}, {"value": "2", "text": "Current B"}]) if draft else "",
                        "Rewritten Answer (Not Historical)": "2" if draft else "",
                        "Rewritten Rationale": "Synthetic updated logic" if draft else "",
                        "INC Subject": "Nursing Foundations", "INC Unit": "Practice", "INC Topic Heading": "Safety",
                        "INC Question Heading": "Safety — decision making",
                        "INC Mapping Needs Human Review": "NO", "Rewrite QA Status": "PENDING",
                    })
            source.write_text(json.dumps({"questions": items}), encoding="utf-8")
            with zipfile.ZipFile(images, "w") as z:
                z.writestr("README", "No images in this synthetic fixture")
            subprocess.run([sys.executable, str(BUILD), "--source", str(source),
                "--master", str(master), "--images-zip", str(images), "--output", str(pack)],
                check=True, capture_output=True, text=True)
            with zipfile.ZipFile(pack) as z:
                m = json.loads(z.read("nclex/manifest.json"))
                self.assertEqual(m["count"], 2894)
                self.assertEqual(m["modes"], {"working_rewrite": 3, "historical_source": 2891})
                self.assertEqual(m["clinicalFinalQaPassCount"], 0)
                self.assertFalse(m["releaseEligible"])
                self.assertEqual(sum(s["count"] for s in m["shards"]), 2894)
                for shard in m["shards"]:
                    data = z.read(shard["path"])
                    self.assertEqual(hashlib.sha256(data).hexdigest(), shard["sha256"])
                first = json.loads(z.read(m["shards"][0]["path"]))
                self.assertEqual(first[0]["answer"], ["2"])
                self.assertEqual(first[3]["answer"], [])
                self.assertFalse(any(q["eligibleForScoredQuiz"] for q in first))
                self.assertEqual(first[3]["historical"]["answer"], ["1"])


if __name__ == "__main__":
    unittest.main()
