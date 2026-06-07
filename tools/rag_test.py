#!/usr/bin/env python3
"""rag_test.py: PC 端 RAG 测试（跳过 OCR，直接输入题目文字）

流程: 题目文字 → 关键词召回 top-5 候选 → DeepSeek 精排 → 输出 (chapterId, knowledgePointId, 置信度, 推理)

用法:
  DEEPSEEK_KEY=sk-xxx python rag_test.py "题目文字"
  DEEPSEEK_KEY=sk-xxx python rag_test.py -f question.txt   # 从文件读

改进点（2026-06-07）：
- 不调 ML Kit OCR（Android 端侧能力，PC 不可用）
- 关键词召回用 KnowledgeBase 相同的算法（TF 计数 + formula/pitfall 加权）
- DeepSeek 精排 prompt 还原 Android 端 DeepSeekKnowledgeClassifier 的行为
"""
import json
import os
import re
import sys
from pathlib import Path
from openai import OpenAI

KB_PATH = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "json" / "accounting_knowledge_points.json"

def load_kb():
    data = json.loads(KB_PATH.read_text(encoding="utf-8"))
    return data["knowledgePoints"]

def recall(question: str, knowledge_points: list, top_k: int = 5) -> list:
    """关键词召回: 与 KnowledgeBase.kt 同样的算法"""
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

def build_prompt(question: str, candidates: list) -> str:
    cand_str = "\n".join([
        f"[候选 {i+1}] id={c['id']} chapterId={c['chapterId']} name={c['name']}\n"
        f"  描述: {c.get('description','')[:200]}\n"
        f"  关键词: {', '.join(c.get('keywords',[]))}"
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
  "reasoning": "<为什么选这个>"
}}"""

def rerank_with_deepseek(question: str, candidates: list, api_key: str) -> dict:
    """DeepSeek 精排: 还原 Android 端 DeepSeekKnowledgeClassifier 的行为"""
    if not candidates:
        return {"error": "无候选（关键词召回失败）"}

    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")
    prompt = build_prompt(question, candidates)
    resp = client.chat.completions.create(
        model="deepseek-chat",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.1,
    )
    raw = resp.choices[0].message.content
    m = re.search(r"\{[\s\S]*\}", raw)
    if not m:
        return {"error": f"DeepSeek 返回非 JSON: {raw[:200]}"}
    return json.loads(m.group(0))

def main():
    if len(sys.argv) < 2:
        print("用法:")
        print('  DEEPSEEK_KEY=sk-xxx python rag_test.py "题目文字"')
        print('  DEEPSEEK_KEY=sk-xxx python rag_test.py -f question.txt')
        sys.exit(1)

    api_key = os.environ.get("DEEPSEEK_KEY")
    if not api_key:
        print("❌ 请设 DEEPSEEK_KEY 环境变量")
        sys.exit(1)

    if sys.argv[1] == "-f":
        question = Path(sys.argv[2]).read_text(encoding="utf-8")
    else:
        question = sys.argv[1]

    print(f"📝 题目 ({len(question)} 字符):")
    print(f"  {question[:200]}{'...' if len(question) > 200 else ''}")
    print()

    kb = load_kb()
    print(f"📚 加载知识库: {len(kb)} 个知识点\n")

    print("🔍 关键词召回 top-5 候选:")
    candidates = recall(question, kb, top_k=5)
    if not candidates:
        print("  ❌ 无匹配（关键词全 miss）")
        return
    for i, c in enumerate(candidates, 1):
        print(f"  [{i}] id={c['id']} ch{c['chapterId']:02d} {c['name']}  (关键词命中: {sum(1 for kw in c.get('keywords',[]) if kw in question)})")
    print()

    print("🤖 DeepSeek 精排...")
    result = rerank_with_deepseek(question, candidates, api_key)
    if "error" in result:
        print(f"  ❌ {result['error']}")
        return

    kp_id = result.get("knowledgePointId")
    ch_id = result.get("chapterId")
    confidence = result.get("confidence", 0)
    reasoning = result.get("reasoning", "")

    kp = next((k for k in kb if k["id"] == kp_id), None)
    if kp:
        print(f"\n✅ 最终结果:")
        print(f"  章节:  第 {ch_id} 章")
        print(f"  知识点: {kp['name']} (id={kp_id})")
        print(f"  置信度: {confidence:.0%}")
        print(f"  推理: {reasoning}")
        print(f"\n📖 知识点详情:")
        print(f"  {kp.get('description','')[:400]}")
        print(f"  关键词: {', '.join(kp.get('keywords',[]))}")
        if kp.get('formulas'):
            print(f"  公式: {'; '.join(kp['formulas'])}")
        if kp.get('commonPitfalls'):
            print(f"  易错: {'; '.join(kp['commonPitfalls'])}")
    else:
        print(f"\n⚠️  DeepSeek 选了 id={kp_id}，但知识库找不到（幻觉？）")

if __name__ == "__main__":
    main()
