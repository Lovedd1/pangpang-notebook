#!/usr/bin/env python3
"""enrich_kb.py: 用考点纯享 PDF 文本增强会计 RAG 知识库

流程：
1. 读取 tools/.tmp/kaodian/ch{XX}.txt（考点文本）
2. 读取 accounting_knowledge_points.json 中该章节的现有 KP
3. 调 DeepSeek：对比考点内容 → 输出关键词补充 / 易错点补充 / 描述优化
4. 输出 tools/.tmp/enrich/ch{XX}.json（增量数据）
5. 用 tools/merge_enrich.py 合并回 JSON

用法：
  DEEPSEEK_KEY=sk-xxx python tools/enrich_kb.py 6          # 单章
  DEEPSEEK_KEY=sk-xxx python tools/enrich_kb.py --all       # 全部 30 章（断点续跑）
"""
import json
import os
import re
import sys
import time
from pathlib import Path
from openai import OpenAI

KB_PATH = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "json" / "accounting_knowledge_points.json"
KAODIAN_DIR = Path(__file__).parent / ".tmp" / "kaodian"
OUT_DIR = Path(__file__).parent / ".tmp" / "enrich"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# 全局可覆盖（--input-dir / --out-dir）
_input_dir = KAODIAN_DIR
_output_dir = OUT_DIR

def load_kb():
    return json.loads(KB_PATH.read_text(encoding="utf-8"))["knowledgePoints"]

def build_prompt(chapter_id: int, chapter_name: str, existing_kps: list, kaodian_text: str) -> str:
    """构建增强 prompt——重点是关键词补充和易错点提取"""
    kp_list = "\n".join([
        f"  KP[{kp['id']}] {kp['name']}\n"
        f"    当前关键词 ({len(kp.get('keywords', []))}): {', '.join(kp.get('keywords', []))}\n"
        f"    当前易错点 ({len(kp.get('commonPitfalls', []))}): {'; '.join(kp.get('commonPitfalls', []))}"
        for kp in existing_kps
    ])
    return f"""你是 CPA 会计老师。下面是第{chapter_id}章（{chapter_name}）的考点讲义和当前知识库的知识点。

任务：根据**考点讲义中的新内容**，补充当前知识点的**关键词**和**易错点**。

⚠️ 规则：
- 只添加考点讲义中有但当前关键词中**没有**的关键词
- 不要删除或修改现有关键词
- 新增关键词应是 CPA 真题题干中可能出现的术语/短语（2-8 字）
- 每个 KP 新增 3-8 个关键词（考点讲义中找不到的就少加）
- 易错点来自考点讲义中的【提示】/易错标记/注意事项
- 描述可以微调（比如考点讲义有更精确的定义），但不要大幅重写

当前知识点：
{kp_list}

考点讲义内容（前 4000 字）：
{kaodian_text[:4000]}

请用以下 JSON 输出（**只输出 JSON，不要其他内容**）：
{{
  "chapterId": {chapter_id},
  "enrichments": [
    {{
      "id": <KP id>,
      "addKeywords": ["新增关键词1", ...],
      "addPitfalls": ["新增易错点1", ...],
      "descriptionUpdate": null  // 或 "微调后的完整描述"
    }}
  ]
}}"""

def enrich_chapter(chapter_id: int, api_key: str, input_dir: Path = None, max_retries: int = 2) -> dict:
    """增强单个章节"""
    if input_dir is None:
        input_dir = _input_dir
    # 1. 读取考点文本
    kaodian_path = input_dir / f"ch{chapter_id:02d}.txt"
    if not kaodian_path.exists():
        return {"chapterId": chapter_id, "error": f"考点文本不存在: {kaodian_path}"}
    kaodian_text = kaodian_path.read_text(encoding="utf-8")

    # 2. 读取现有 KP
    all_kps = load_kb()
    existing = [kp for kp in all_kps if kp["chapterId"] == chapter_id]
    if not existing:
        return {"chapterId": chapter_id, "error": f"知识库中第{chapter_id}章无知识点"}

    # 章节名
    ch_names = {
        1: "总论", 2: "存货", 3: "固定资产", 4: "无形资产", 5: "投资性房地产",
        6: "长期股权投资与合营安排", 7: "资产减值", 8: "负债", 9: "职工薪酬",
        10: "股份支付", 11: "借款费用", 12: "或有事项", 13: "金融工具", 14: "租赁",
        15: "持有待售和终止经营", 16: "所有者权益", 17: "收入、费用和利润", 18: "政府补助",
        19: "所得税", 20: "非货币性资产交换", 21: "债务重组", 22: "外币折算",
        23: "财务报告", 24: "会计政策、会计估计变更和差错更正", 25: "资产负债表日后事项",
        26: "企业合并", 27: "合并财务报表", 28: "每股收益", 29: "公允价值计量",
        30: "政府及民间非营利组织会计"
    }
    ch_name = ch_names.get(chapter_id, f"第{chapter_id}章")

    # 3. DeepSeek
    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")
    prompt = build_prompt(chapter_id, ch_name, existing, kaodian_text)

    for attempt in range(max_retries + 1):
        try:
            resp = client.chat.completions.create(
                model="deepseek-chat",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
            )
            raw = resp.choices[0].message.content
            m = re.search(r"\{[\s\S]*\}", raw)
            if not m:
                raise ValueError(f"非 JSON: {raw[:200]}")
            result = json.loads(m.group(0))
            result["_chapter_name"] = ch_name
            result["_kp_count"] = len(existing)
            result["_kaodian_chars"] = len(kaodian_text)
            return result
        except Exception as e:
            print(f"  ⚠️  第 {attempt+1}/{max_retries+1} 次失败: {e}")
            if attempt < max_retries:
                time.sleep(2 ** attempt)
    return {"chapterId": chapter_id, "error": f"重试 {max_retries+1} 次均失败"}

def main():
    api_key = os.environ.get("DEEPSEEK_KEY")
    if not api_key:
        print("❌ 请设 DEEPSEEK_KEY 环境变量")
        sys.exit(1)

    args = [a for a in sys.argv[1:] if not a.startswith("--") or a == "--all"]
    flags = [a for a in sys.argv[1:] if a.startswith("--") and a != "--all"]

    input_dir = _input_dir
    output_dir = _output_dir

    for f in flags:
        if f.startswith("--input-dir="):
            input_dir = Path(f.split("=", 1)[1])
        elif f.startswith("--out-dir="):
            output_dir = Path(f.split("=", 1)[1])
    output_dir.mkdir(parents=True, exist_ok=True)

    if not args:
        print("用法: python enrich_kb.py <章节号> | --all [--input-dir=PATH] [--out-dir=PATH]")
        print(f"  默认 input: {_input_dir}")
        print(f"  默认 out:   {_output_dir}")
        sys.exit(1)

    if args[0] == "--all":
        chapters = list(range(1, 31))
        skip_existing = True
    else:
        chapters = [int(args[0])]
        skip_existing = False

    for ch in chapters:
        out_path = output_dir / f"ch{ch:02d}.json"
        if skip_existing and out_path.exists():
            try:
                existing = json.loads(out_path.read_text(encoding="utf-8"))
                if "error" not in existing:
                    print(f"  Ch{ch:02d} 已存在，跳过")
                    continue
            except:
                pass

        print(f"📖 Ch{ch:02d} 处理中...", end=" ", flush=True)
        result = enrich_chapter(ch, api_key, input_dir=input_dir)
        out_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

        if "error" in result:
            print(f"❌ {result['error']}")
        else:
            n_enrich = len(result.get("enrichments", []))
            n_kw = sum(len(e.get("addKeywords", [])) for e in result.get("enrichments", []))
            n_pit = sum(len(e.get("addPitfalls", [])) for e in result.get("enrichments", []))
            print(f"✅ {result['_kp_count']} KP → +{n_kw} 关键词, +{n_pit} 易错点 ({result['_kaodian_chars']} 字符)")

if __name__ == "__main__":
    main()
