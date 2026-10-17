"""Validate public online lesson paths; use only the Python standard library."""
from pathlib import Path
import json
root = Path(__file__).resolve().parents[1]
data = json.loads((root / "content/learning/v1.json").read_text(encoding="utf8"))
assert data["schemaVersion"] == 1 and data["revision"] >= 1
assert data["publisher"] == "ZEBJUS LLP" and data["kind"] == "original-concept-learning"
assert data["passingPercent"] == 80
levels = data["levels"]
assert len(levels) == 10
ids = set()
for i,level in enumerate(levels,1):
    assert level["number"] == i
    assert len(level["concepts"]) == 3 and len(level["quiz"]) == 5
    assert all(1 <= p <= 150 for p in level["sourcePages"])
    for concept in level["concepts"]:
        assert all(concept[k].strip() for k in ("title","body","example"))
    for q in level["quiz"]:
        assert q["id"] not in ids
        ids.add(q["id"])
        assert len(q["options"]) == 4 and len(set(q["options"])) == 4
        assert 0 <= q["correctIndex"] <= 3
        assert q["prompt"] and q["explanation"]
code = (root / "android/app/src/main/java/com/studydock/learning/GuidedLearning.kt").read_text()
assert "original-concept-learning" in code and ">= 80" in code
print(f"Validated {len(levels)} learning levels, 30 concepts and {len(ids)} authored questions")
