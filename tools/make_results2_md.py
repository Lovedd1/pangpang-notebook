#!/usr/bin/env python3
"""make_results2_md.py: 生成 结二.md（章节用中文名称）"""
import json
from pathlib import Path

CHAPTER_NAMES = {
    1:"总论",2:"存货",3:"固定资产",4:"无形资产",5:"投资性房地产",
    6:"长期股权投资",7:"资产减值",8:"负债",9:"职工薪酬",10:"股份支付",
    11:"借款费用",12:"或有事项",13:"金融工具",14:"租赁",
    15:"持有待售",16:"所有者权益",17:"收入",18:"政府补助",
    19:"所得税",20:"非货币性资产交换",21:"债务重组",22:"外币折算",
    23:"财务报告",24:"会计政策/估计变更",25:"资产负债表日后事项",
    26:"企业合并",27:"合并财务报表",28:"每股收益",29:"公允价值计量",
    30:"政府及民间非营利组织会计"
}

EXPECTED = {
    1:1,2:2,3:3,4:4,5:5,6:6,7:7,8:11,9:9,10:10,
    11:17,12:12,13:13,14:14,15:15,16:16,17:17,18:18,19:21,20:19,
    21:22,22:24,23:25,24:26,25:27,26:28,27:29,28:30,29:30,30:15
}

def main():
    base = Path(__file__).parent
    kb = json.loads((base.parent / "app/src/main/assets/json/accounting_knowledge_points.json").read_text(encoding="utf-8"))
    results = json.loads((base / ".tmp/results_30_v6.json").read_text(encoding="utf-8"))
    results = {r["id"]: r for r in results}

    ok_count = wrong_count = 0
    wrong_chs = {}

    lines = ["# RAG 第 2 轮 30 道题测试结果\n",
             f"**知识库**：{len(kb['knowledgePoints'])} 知识点（IDF 召回 + DeepSeek 精排）\n",
             "| 题号 | 主题 | 预期章节 | RAG 章节 | RAG 知识点 | 置信度 | 是否确认 | 备注 |",
             "|------|------|---------|---------|-----------|--------|---------|------|"]

    for qid in sorted(EXPECTED.keys()):
        r = results.get(qid, {})
        exp_ch = EXPECTED[qid]
        rag_ch = r.get("rag_chapter")
        rag_kp = r.get("rag_kp_name", "?")[:30]
        conf = r.get("confidence", 0)
        topic = r.get("topic", "?")[:20]

        exp_name = CHAPTER_NAMES.get(exp_ch, f"ch{exp_ch}")
        rag_name = CHAPTER_NAMES.get(rag_ch, f"ch{rag_ch}") if rag_ch else "?"

        if rag_ch is None:
            status = "✗"
            note = f"RAG 失败：{r.get('error','')}"
            wrong_count += 1
            wrong_chs[exp_ch] = wrong_chs.get(exp_ch, 0) + 1
        elif rag_ch == exp_ch:
            status = "✓"
            note = "无"
            ok_count += 1
        else:
            status = "✗"
            note = f"误归到 {rag_name}"
            wrong_count += 1
            wrong_chs[exp_ch] = wrong_chs.get(exp_ch, 0) + 1

        lines.append(f"| {qid} | {topic} | {exp_name} | {rag_name} | {rag_kp} | {conf:.0%} | {status} | {note} |")

    lines.append("")
    lines.append(f"## 统计：✓{ok_count} / ✗{wrong_count} / 总{ok_count+wrong_count} ({ok_count*100/(ok_count+wrong_count):.0f}%)")
    lines.append("")
    if wrong_chs:
        lines.append("## 需要重录的章节")
        for ch, n in sorted(wrong_chs.items(), key=lambda x: -x[1]):
            lines.append(f"- **{CHAPTER_NAMES.get(ch, f'ch{ch}')}**：{n} 道题分类错")

    out = base.parent / "结二.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"✅ 写入 {out} (✓{ok_count}/✗{wrong_count})")

if __name__ == "__main__":
    main()
