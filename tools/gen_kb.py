#!/usr/bin/env python3
"""gen_kb.py: 用 DeepSeek 把每章文本生成知识点 JSON

用法：
  DEEPSEEK_KEY=sk-xxx python gen_kb.py <input_dir> <output_dir>

需要：pip install openai
改进点（2026-06-07）：
- 断点续跑：已存在的 kb_chXX.json 自动跳过
- 进度显示：每章 start / done / total
- 重试机制：DeepSeek 失败重试 3 次（指数退避）
- Key 安全：只从环境变量读，永不写文件/print
"""
import json
import os
import re
import sys
import time
from pathlib import Path
from openai import OpenAI

PROMPT = """你是 CPA 会计老师。从下面章节内容里抽取 5~15 个**核心知识点**。
对每个知识点输出 JSON 数组（**只输出 JSON 数组，不要任何其他内容**），
每个元素结构：
{{
  "name": "不超过 15 字",
  "description": "200~400 字, 讲清是什么/怎么做/与什么相关",
  "keywords": ["5~10 个高频术语"],
  "formulas": ["出现的核心公式, 没有就空数组"],
  "commonPitfalls": ["考生常错点, 没有就空数组"]
}}
章节内容：
{chapter_text}"""

def extract_json(raw: str) -> list:
    m = re.search(r"\[\s*\{[\s\S]*\}\s*\]", raw)
    if not m:
        raise ValueError(f"未找到 JSON 数组：\n{raw[:500]}")
    return json.loads(m.group(0))

def call_with_retry(client, **kwargs) -> str:
    """调 DeepSeek chat completion，失败指数退避重试 3 次"""
    last_err = None
    for attempt in range(3):
        try:
            resp = client.chat.completions.create(**kwargs)
            return resp.choices[0].message.content
        except Exception as e:
            last_err = e
            wait = 2 ** attempt
            print(f"    ⚠️ 尝试 {attempt+1}/3 失败: {type(e).__name__}, {wait}s 后重试...")
            time.sleep(wait)
    raise RuntimeError(f"DeepSeek 3 次重试均失败: {last_err}")

def main():
    if len(sys.argv) != 3:
        print("用法: DEEPSEEK_KEY=sk-xxx python gen_kb.py <input_dir> <output_dir>")
        sys.exit(1)

    api_key = os.environ.get("DEEPSEEK_KEY")
    if not api_key:
        print("❌ 请先设置环境变量 DEEPSEEK_KEY")
        print("   Linux/Mac:  export DEEPSEEK_KEY=sk-xxx")
        print("   Windows:    set DEEPSEEK_KEY=sk-xxx")
        sys.exit(1)
    if not api_key.startswith("sk-"):
        print(f"⚠️  Key 格式异常（不以 sk- 开头），长度 {len(api_key)} 字符。确认下？继续 5s 后跳过。")
        time.sleep(5)

    in_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

    chapter_files = sorted(in_dir.glob("ch*.txt"))
    if not chapter_files:
        print(f"❌ 在 {in_dir} 找不到 ch*.txt")
        sys.exit(1)

    total = len(chapter_files)
    print(f"📚 共 {total} 章，输出到 {out_dir}/\n")

    succeeded, skipped, failed = 0, 0, 0
    for idx, ch_file in enumerate(chapter_files, 1):
        out_path = out_dir / f"{ch_file.stem}.json"

        # 断点续跑
        if out_path.exists():
            print(f"[{idx:02d}/{total}] {ch_file.name} → ⏭  跳过（已存在 {out_path.stat().st_size} bytes）")
            skipped += 1
            continue

        text = ch_file.read_text(encoding="utf-8")
        if len(text.strip()) < 100:
            print(f"[{idx:02d}/{total}] {ch_file.name} → ⚠️  文本过短（{len(text)} 字符），跳过")
            failed += 1
            continue

        print(f"[{idx:02d}/{total}] {ch_file.name} → 🤖 调 DeepSeek ({len(text)} 字符)...")
        t0 = time.time()
        try:
            raw = call_with_retry(
                client,
                model="deepseek-chat",
                messages=[{"role": "user", "content": PROMPT.format(chapter_text=text)}]
            )
            points = extract_json(raw)
            ch_num = int(ch_file.stem[2:])
            for p in points:
                p["chapterId"] = ch_num
            out_path.write_text(
                json.dumps(points, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            elapsed = time.time() - t0
            print(f"             ✓ {len(points)} 个知识点 ({elapsed:.1f}s)")
            succeeded += 1
        except Exception as e:
            print(f"             ❌ 失败：{e}")
            failed += 1
            continue

    print(f"\n📊 总结：成功 {succeeded} / 跳过 {skipped} / 失败 {failed} / 总 {total}")
    if failed > 0:
        print(f"💡 失败的章节可修复后重跑——已成功的会自动跳过")

if __name__ == "__main__":
    main()
