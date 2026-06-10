#!/usr/bin/env python3
"""merge_enrich.py: 将 enrich_kb.py 生成的增量文件合并回 accounting_knowledge_points.json

用法: python tools/merge_enrich.py [--dry-run]
"""
import json
import sys
from pathlib import Path
from copy import deepcopy

KB_PATH = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "json" / "accounting_knowledge_points.json"
ENRICH_DIR = Path(__file__).parent / ".tmp" / "enrich"

def merge(dry_run: bool = False, enrich_dir: Path = None):
    if enrich_dir is None:
        enrich_dir = ENRICH_DIR
    kb_data = json.loads(KB_PATH.read_text(encoding="utf-8"))
    kps = kb_data["knowledgePoints"]
    kp_by_id = {kp["id"]: kp for kp in kps}

    total_kw = 0
    total_pit = 0
    total_desc = 0
    chapters_done = 0

    for enrich_path in sorted(enrich_dir.glob("ch*.json")):
        enrich = json.loads(enrich_path.read_text(encoding="utf-8"))
        if "error" in enrich:
            print(f"  ⚠️  {enrich_path.name}: {enrich['error']}，跳过")
            continue

        ch_id = enrich["chapterId"]
        ch_kw = 0
        ch_pit = 0

        for e in enrich.get("enrichments", []):
            kp_id = e["id"]
            if kp_id not in kp_by_id:
                print(f"  ⚠️  KP[{kp_id}] 不在知识库中，跳过")
                continue

            kp = kp_by_id[kp_id]

            # 添加关键词
            new_kws = e.get("addKeywords", [])
            existing_kws = set(kp.get("keywords", []))
            for kw in new_kws:
                if kw not in existing_kws:
                    kp["keywords"].append(kw)
                    ch_kw += 1

            # 添加易错点
            new_pits = e.get("addPitfalls", [])
            existing_pits = set(kp.get("commonPitfalls", []))
            for p in new_pits:
                if p not in existing_pits:
                    kp.setdefault("commonPitfalls", []).append(p)
                    ch_pit += 1

            # 更新描述（如果有）
            desc_update = e.get("descriptionUpdate")
            if desc_update and desc_update != kp.get("description", ""):
                if dry_run:
                    print(f"  📝 KP[{kp_id}] 描述更新: {desc_update[:60]}...")
                else:
                    kp["description"] = desc_update
                total_desc += 1

        total_kw += ch_kw
        total_pit += ch_pit
        chapters_done += 1
        print(f"  ✅ Ch{ch_id:02d}: +{ch_kw} 关键词, +{ch_pit} 易错点, {enrich.get('_kp_count', '?')} KP")

    if not dry_run:
        KB_PATH.write_text(json.dumps(kb_data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n✅ 已写入 {KB_PATH}")
    else:
        print(f"\n🔍 DRY RUN — 未修改文件")

    print(f"📊 总计: {chapters_done} 章, +{total_kw} 关键词, +{total_pit} 易错点, {total_desc} 描述更新")

if __name__ == "__main__":
    dry = "--dry-run" in sys.argv
    enrich_dir = None
    for a in sys.argv:
        if a.startswith("--enrich-dir="):
            enrich_dir = Path(a.split("=", 1)[1])
    merge(dry_run=dry, enrich_dir=enrich_dir)
