#!/usr/bin/env python3
"""gen_kb.py: 用 DeepSeek 把每章文本生成知识点 JSON

用法：
  DEEPSEEK_KEY=sk-xxx python gen_kb.py <input_dir> <output_dir>

需要：pip install openai
"""
import json
import os
import sys
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
    import re
    m = re.search(r"\[\s*\{[\s\S]*\}\s*\]", raw)
    if not m:
        raise ValueError(f"未找到 JSON 数组：\n{raw[:500]}")
    return json.loads(m.group(0))

def main():
    if len(sys.argv) != 3:
        print("用法: DEEPSEEK_KEY=sk-xxx python gen_kb.py <input_dir> <output_dir>")
        sys.exit(1)
    api_key = os.environ.get("DEEPSEEK_KEY")
    if not api_key:
        print("请设置环境变量 DEEPSEEK_KEY")
        sys.exit(1)
    in_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

    for ch_file in sorted(in_dir.glob("ch*.txt")):
        text = ch_file.read_text(encoding="utf-8")
        print(f"生成 {ch_file.name} ...")
        resp = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": PROMPT.format(chapter_text=text)}]
        )
        raw = resp.choices[0].message.content
        try:
            points = extract_json(raw)
        except Exception as e:
            print(f"  ⚠️ 解析失败：{e}，跳过此章")
            continue
        ch_num = int(ch_file.stem[2:])
        for p in points:
            p["chapterId"] = ch_num
        (out_dir / f"{ch_file.stem}.json").write_text(
            json.dumps(points, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"  ✓ {len(points)} 个知识点")
    print(f"完成，输出到 {out_dir}/")

if __name__ == "__main__":
    main()
