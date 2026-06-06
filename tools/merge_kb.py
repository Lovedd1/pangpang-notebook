#!/usr/bin/env python3
"""merge_kb.py: 合并 30 个章节 JSON 为单个 knowledge_points.json

用法：
  python merge_kb.py <input_dir> <output_file>

输出格式：
{
  "version": 1,
  "knowledgePoints": [
    {"id": 1, "chapterId": 1, "name": "...", ...},
    ...
  ]
}
"""
import json
import sys
from pathlib import Path

def main():
    if len(sys.argv) != 3:
        print("用法: python merge_kb.py <input_dir> <output_file>")
        sys.exit(1)
    in_dir, out_file = Path(sys.argv[1]), Path(sys.argv[2])
    merged = []
    for ch_file in sorted(in_dir.glob("ch*.json")):
        merged.extend(json.loads(ch_file.read_text(encoding="utf-8")))
    for i, item in enumerate(merged, 1):
        item["id"] = i
    out = {"version": 1, "knowledgePoints": merged}
    out_file.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"合并 {len(merged)} 个知识点到 {out_file}")

if __name__ == "__main__":
    main()
