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

【Ch6 长期股权投资 vs Ch13 金融工具的边界铁则】（重要！2026-06-10 修复）
- Ch6 长期股权投资处置的核心场景：子公司/联营/合营企业股权出售、持股比例变化、丧失控制/共同控制/重大影响、个别报表层面核算方法转换、分次转让+临时过户+无表决权或利润分配权（实质重于形式）。
- Ch13 金融资产终止确认的核心场景：应收/应付账款保理、应收票据贴现、信贷资产证券化、过手安排测试、风险报酬转移的金融工具判断。
- ⚠️ '题干是长期股权投资处置（出售联营/子公司股权）+ 选项中有非交易性权益工具投资/其他综合收益转入留存收益'→ 核心交易是 Ch6 长期股权投资处置，选项中 Ch13 概念是辅助细节——选 Ch6！
- 题面同时出现'股权转让/过户/表决权/利润分配权/风险报酬/未满足终止确认条件'时，先判断核心主体：
  - 主体是股权（长期股权投资）→ Ch6
  - 主体是金融工具（应收/应付/票据/信贷资产/保理）→ Ch13
- ⚠️ '临时过户但无表决权或利润分配权'是 Ch6 实质重于形式的核心考点，**不是** Ch13 终止确认。

【Ch6 vs Ch26 vs Ch27 边界铁则】（2026-06-10 修复）
- Ch6 KP[47]（非同一控制下企业合并初始计量）关注个别财务报表层面：长期股权投资的确认时点、控制权取得判断（董事会改组/派出董事/监管批文/财产交接/工商登记/股东变更与取得控制权日的关系）。
- Ch26（企业合并）关注合并层面：购买日条件/合并成本与商誉/反向购买/或有对价。
- Ch27（合并财务报表）关注合并抵消与合并工作底稿：抵消分录/少数股东权益。
- 判别：题干问'长期股权投资的确认时点/控制权取得日'且包含董事会改组/派出过半数/工商变更/过渡期损益→ Ch6 KP[47]。

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
