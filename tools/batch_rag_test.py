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

import math

def load_kb():
    data = json.loads(KB_PATH.read_text(encoding="utf-8"))
    return data["knowledgePoints"]

def build_idf(knowledge_points):
    """预计算每个关键词的 IDF 权重（基于全文本频率，不是 keyword list 频率）

    核心思路：在 name+desc+keywords 的全文里出现的 KP 数越多，该词越通用，IDF 越低。
    '资产' 出现在 ~200 个 KP 的全文里 → 低 IDF；'谨慎性' 只出现在 ~3 个 → 高 IDF
    """
    N = len(knowledge_points)
    # 合并每个 KP 的全文本（name + description + keywords，便于子串搜索）
    full_texts = []
    for kp in knowledge_points:
        ft = kp.get("name", "") + " " + kp.get("description", "") + " " + " ".join(kp.get("keywords", []))
        full_texts.append(ft)

    # 收集所有独特关键词
    all_kws = set()
    for kp in knowledge_points:
        for kw in kp.get("keywords", []):
            all_kws.add(kw)

    # 对每个关键词，统计它作为子串出现在多少个 KP 的 full_text 中
    df = {}
    for kw in all_kws:
        count = sum(1 for ft in full_texts if kw in ft)
        df[kw] = count

    # IDF = log(N / df)，+0.5 平滑防止罕见词过拟合
    return {kw: math.log((N + 0.5) / (df[kw] + 0.5)) for kw in df}, df

def extract_ngrams(text, max_len=5):
    """从文本提取 2-5 字符滑动窗口（子串匹配用）"""
    ngrams = set()
    for n in range(2, min(max_len + 1, len(text) + 1)):
        for i in range(len(text) - n + 1):
            ngrams.add(text[i:i+n])
    return ngrams

def recall(question, knowledge_points, idf=None, top_k=5):
    """关键词召回 + IDF 权重"""
    text = question
    scores = []
    for kp in knowledge_points:
        score = 0.0
        for kw in kp.get("keywords", []):
            if kw in text:
                # IDF 加权：罕见关键词（如'谨慎性'）得高分，通用词（如'资产'）得低分
                w = idf.get(kw, 3.0) if idf else 3.0
                score += w
        for f in kp.get("formulas", []):
            if f and f in text:
                score += (idf.get(f, 2.0) if idf else 2.0) * 0.8
        for p in kp.get("commonPitfalls", []):
            if p and p in text:
                score += (idf.get(p, 1.5) if idf else 1.5) * 0.6
        if score > 0:
            scores.append((score, kp))
    scores.sort(key=lambda x: x[0], reverse=True)
    return [kp for score, kp in scores[:top_k]]

CHAPTER_NAMES_CN = {
    1:"总论(会计信息质量/基本假设/要素)",2:"存货",3:"固定资产",4:"无形资产",5:"投资性房地产",
    6:"长期股权投资",7:"资产减值",8:"负债",9:"职工薪酬",10:"股份支付",
    11:"借款费用",12:"或有事项",13:"金融工具",14:"租赁",
    15:"持有待售/终止经营",16:"所有者权益(含其他综合收益)",17:"收入/费用/利润",18:"政府补助",
    19:"所得税",20:"非货币性资产交换",21:"债务重组",22:"外币折算",
    23:"财务报告/现金流量表",24:"会计政策/估计变更",25:"资产负债表日后事项",
    26:"企业合并",27:"合并财务报表",28:"每股收益",29:"公允价值计量",
    30:"政府及民间非营利组织会计"
}

def build_prompt(question, candidates):
    cand_str = "\n".join([
        f"[{i+1}] id={c['id']} 第{c['chapterId']}章({CHAPTER_NAMES_CN.get(c['chapterId'],'?')}) - {c['name']}\n  关键词: {', '.join(c.get('keywords',[]))}"
        for i, c in enumerate(candidates)
    ])
    return f"""你是 CPA 会计老师。根据题目内容判断这道题**主要属于哪个章节**。
若题目涉及跨章节，选占比>50%的章节作为 primary，另一章为 secondary。

⚠️ 常见跨章节陷阱（多选题/判断题更容易误判）：
- 优先股/永续债涉及「应分类为金融负债还是权益工具」→ 第13章（金融工具），不是第16章
- 优先股/永续债涉及「分类后的股利处理/发行费用/重分类计量」→ 第16章（所有者权益）
- 题目中出现"所有者权益"字眼不代表题目属于第16章——如果核心是判断分类标准，仍属第13章

题目：
{question}

候选知识点：
{cand_str}

JSON（只输出 JSON）：
格式A（单章节>85%）：
{{"primary":{{"knowledgePointId":<id>,"chapterId":<id>,"proportion":1.0}},"secondary":null,"confidence":<0.0~1.0>,"reasoning":"<主要章节>"}}

格式B（跨章节）：
{{"primary":{{"knowledgePointId":<主id>,"chapterId":<主ch>,"proportion":<0.50~0.85>}},"secondary":{{"knowledgePointId":<次id>,"chapterId":<次ch>,"proportion":<余>}},"confidence":<0.0~1.0>,"reasoning":"<主次占比依据>"}}
"""

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

def process_one(q, kb, api_key, idf=None):
    candidates = recall(q["question"], kb, idf=idf, top_k=7)  # 扩展到 7 个候选，给跨章节更多选择
    result = rerank(q["question"], candidates, api_key)
    if "error" in result:
        return {**q, "status": "ERROR", "error": result["error"], "candidates": [{"id": c["id"], "ch": c["chapterId"], "name": c["name"]} for c in candidates]}

    # 兼容新旧两种 JSON 格式
    primary = result.get("primary")
    if primary is None:
        # 旧格式：{knowledgePointId, chapterId, confidence, reasoning}
        kp_id = result.get("knowledgePointId")
        primary_ch = result.get("chapterId")
        primary_prop = 1.0
        secondary_info = None
    else:
        # 新格式：{primary: {..}, secondary: .., confidence, reasoning}
        kp_id = primary.get("knowledgePointId")
        primary_ch = primary.get("chapterId")
        primary_prop = primary.get("proportion", 1.0)
        secondary = result.get("secondary")
        secondary_info = None
        if secondary:
            sec_kp = next((k for k in kb if k["id"] == secondary.get("knowledgePointId")), None)
            secondary_info = {
                "ch": secondary.get("chapterId"),
                "name": sec_kp["name"] if sec_kp else "?",
                "proportion": secondary.get("proportion", 0)
            }

    kp = next((k for k in kb if k["id"] == kp_id), None)
    # 校验：用知识库的实际 chapterId 覆盖 DeepSeek 可能的幻觉（如 chapterId=216）
    actual_ch = kp["chapterId"] if kp else primary_ch
    return {
        "id": q["id"],
        "topic": q["topic"],
        "rag_chapter": actual_ch,           # ← 用知识库真实 chapterId
        "rag_kp_id": kp_id,
        "rag_kp_name": kp["name"] if kp else "(找不到)",
        "primary_proportion": primary_prop,
        "secondary": secondary_info,
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
    idf, df = build_idf(kb)
    # 打印 IDF 横截面（验证权重合理性）
    sample_kws = ['谨慎性', '权益法', '资产', '确认', '现值', '债务重组', '会计信息质量']
    print(f"加载 {len(kb)} 知识点, IDF 横截面:", {k: round(idf.get(k, 0), 1) for k in sample_kws if k in idf})
    print(f"并发跑 {len(questions)} 道题（max 10 workers）\n")

    results = []
    with ThreadPoolExecutor(max_workers=10) as ex:
        futures = {ex.submit(process_one, q, kb, api_key, idf): q for q in questions}
        for i, fut in enumerate(as_completed(futures), 1):
            r = fut.result()
            results.append(r)
            mark = "✓" if r.get("status") == "OK" and r.get('rag_chapter') else "✗"
            ch_str = str(r.get('rag_chapter') or '?')
            kp_str = (r.get('rag_kp_name') or '?')[:30]
            print(f"[{i:2d}/{len(questions)}] {mark} #{r['id']:2d} {r.get('topic','?')[:25]:25s} → ch{ch_str:>3s} {kp_str:30s} ({r.get('confidence',0):.0%})")

    results.sort(key=lambda x: x["id"])
    Path(sys.argv[2]).write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n✅ 结果写入 {sys.argv[2]}")

if __name__ == "__main__":
    main()
