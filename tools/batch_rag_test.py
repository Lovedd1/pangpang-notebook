#!/usr/bin/env python3
"""batch_rag_test.py: 批量跑 N 道题 RAG 测试，并发执行

用法: DEEPSEEK_KEY=sk-xxx python batch_rag_test.py <questions.json> <output.json>
"""
import json
import os
import sys
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from openai import OpenAI

KB_PATH = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "json" / "accounting_knowledge_points.json"

def load_kb():
    data = json.loads(KB_PATH.read_text(encoding="utf-8"))
    return data["knowledgePoints"]

def recall(question, knowledge_points, top_k=5):
    text = question
    scores = []
    for kp in knowledge_points:
        match_count = sum(1 for kw in kp.get("keywords", []) if kw in text)
        formula_match = sum(1 for f in kp.get("formulas", []) if f and f in text)
        pitfall_match = sum(1 for p in kp.get("commonPitfalls", []) if p and p in text)
        score = match_count * 3.0 + formula_match * 2.0 + pitfall_match * 1.5
        scores.append((score, kp))
    scores.sort(key=lambda x: x[0], reverse=True)
    return [kp for score, kp in scores[:top_k] if score > 0]

def build_prompt(question, candidates):
    cand_str = "\n".join([
        f"[{i+1}] id={c['id']} ch{c['chapterId']} {c['name']}\n  关键词: {', '.join(c.get('keywords',[]))}"
        for i, c in enumerate(candidates)
    ])
    return f"""你是 CPA 会计老师。根据题目内容，从候选知识点里选最匹配的 1 个。

题目：
{question}

{cand_str}

请用以下 JSON 格式回答（**只输出 JSON，不要其他内容**）：
{{
  "knowledgePointId": <候选 id, 整数>,
  "chapterId": <候选 chapterId, 整数>,
  "confidence": <0.0~1.0>,
  "reasoning": "<为什么选这个，不超过 50 字>"
}}"""

def rerank(question, candidates, api_key):
    if not candidates:
        return {"error": "无候选"}
    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")
    prompt = build_prompt(question, candidates)
    for attempt in range(3):
        try:
            resp = client.chat.completions.create(
                model="deepseek-chat",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
            )
            raw = resp.choices[0].message.content
            m = re.search(r"\{[\s\S]*\}", raw)
            if m:
                return json.loads(m.group(0))
            return {"error": f"非JSON: {raw[:200]}"}
        except Exception as e:
            if attempt == 2:
                return {"error": f"3次失败: {e}"}

def process_one(q, kb, api_key):
    candidates = recall(q["question"], kb, top_k=5)
    result = rerank(q["question"], candidates, api_key)
    if "error" in result:
        return {**q, "status": "ERROR", "error": result["error"], "candidates": [{"id": c["id"], "ch": c["chapterId"], "name": c["name"]} for c in candidates]}
    kp_id = result.get("knowledgePointId")
    kp = next((k for k in kb if k["id"] == kp_id), None)
    return {
        "id": q["id"],
        "topic": q["topic"],
        "rag_chapter": result.get("chapterId"),
        "rag_kp_id": kp_id,
        "rag_kp_name": kp["name"] if kp else "(找不到)",
        "confidence": result.get("confidence", 0),
        "reasoning": result.get("reasoning", ""),
        "candidates": [{"id": c["id"], "ch": c["chapterId"], "name": c["name"]} for c in candidates],
        "status": "OK"
    }

def main():
    if len(sys.argv) != 3:
        print("用法: DEEPSEEK_KEY=sk-xxx python batch_rag_test.py <questions.json> <output.json>")
        sys.exit(1)
    api_key = os.environ.get("DEEPSEEK_KEY")
    if not api_key:
        print("❌ 请设 DEEPSEEK_KEY")
        sys.exit(1)
    questions = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    kb = load_kb()
    print(f"加载 {len(kb)} 知识点，并发跑 {len(questions)} 道题（max 10 workers）\n")

    results = []
    with ThreadPoolExecutor(max_workers=10) as ex:
        futures = {ex.submit(process_one, q, kb, api_key): q for q in questions}
        for i, fut in enumerate(as_completed(futures), 1):
            r = fut.result()
            results.append(r)
            mark = "✓" if r.get("status") == "OK" else "✗"
            print(f"[{i:2d}/{len(questions)}] {mark} #{r['id']:2d} {r['topic']:30s} → ch{r.get('rag_chapter','?'):2} {r.get('rag_kp_name','?')[:30]:30s} ({r.get('confidence',0):.0%})")

    results.sort(key=lambda x: x["id"])
    Path(sys.argv[2]).write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n✅ 结果写入 {sys.argv[2]}")

if __name__ == "__main__":
    main()
