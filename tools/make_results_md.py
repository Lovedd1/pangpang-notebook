#!/usr/bin/env python3
"""make_results_md.py: 生成 结果.md 表格

读 results_30.json + questions_30.json + 预期章节映射 → 写 结果.md
"""
import json
from pathlib import Path

# 预期章节映射（基于 CPA 会计 30 章结构 + 题目主题）
EXPECTED = {
    1: ("ch1", "会计信息质量要求-谨慎性"),
    2: ("ch2", "存货初始计量-采购成本"),
    3: ("ch3", "固定资产-双倍余额递减法+减值"),
    4: ("ch4", "无形资产-摊销"),
    5: ("ch5", "投资性房地产-公允价值模式"),
    6: ("ch6", "长期股权投资-权益法"),
    7: ("ch7", "资产减值-固定资产"),
    8: ("ch8", "负债-应付债券/可转债"),
    9: ("ch9", "职工薪酬-辞退福利/非货币福利"),
    10: ("ch10", "股份支付-集团内"),
    11: ("ch11", "借款费用-范围"),
    12: ("ch12", "或有事项-预计负债"),
    13: ("ch13", "金融工具-摊余成本计量"),
    14: ("ch14", "租赁-经营租赁"),
    15: ("ch15", "持有待售"),
    16: ("ch16", "所有者权益-变动"),
    17: ("ch17", "收入-可变对价"),
    18: ("ch18", "政府补助"),
    19: ("ch19", "所得税"),
    20: ("ch19", "所得税"),  # 实际上 #20 也是所得税（计算题）
    21: ("ch20", "债务重组-库存商品抵债"),
    22: ("ch22", "外币折算-汇兑损益"),
    23: ("ch23", "财务报告/合并现金流量表"),
    24: ("ch24", "会计政策/估计变更"),
    25: ("ch25", "资产负债表日后事项"),
    26: ("ch26", "企业合并-商誉"),
    27: ("ch27", "合并财务报表-内部交易抵销"),
    28: ("ch28", "每股收益"),
    29: ("ch29", "公允价值计量"),
    30: ("ch30", "政府及民间非营利组织会计"),
}

# 修正：按题目主题重新映射
EXPECTED_FIX = {
    1: (1, "会计信息质量要求"),
    2: (2, "存货-采购成本"),
    3: (3, "固定资产-折旧与减值"),
    4: (4, "无形资产-摊销"),
    5: (5, "投资性房地产-公允价值"),
    6: (6, "长期股权投资-权益法"),
    7: (7, "资产减值-固定资产"),
    8: (8, "应付债券-可转债"),
    9: (9, "职工薪酬-辞退/非货币"),
    10: (10, "股份支付-集团内"),
    11: (11, "借款费用-范围"),
    12: (12, "或有事项-最佳估计数"),
    13: (13, "金融工具-初始计量"),
    14: (14, "租赁-经营租赁"),
    15: (15, "持有待售-列报金额"),
    16: (16, "所有者权益-变动"),
    17: (17, "收入-可变对价"),
    18: (18, "政府补助-判断"),
    19: (21, "债务重组-库存商品抵债"),
    20: (19, "所得税-计算"),
    21: (22, "外币折算-汇兑损益"),
    22: (24, "会计政策/估计变更"),
    23: (25, "资产负债表日后事项"),
    24: (26, "企业合并-商誉"),
    25: (27, "合并报表-内部交易抵销"),
    26: (28, "每股收益-基本"),
    27: (29, "公允价值-确定"),
    28: (30, "政府会计"),
    29: (30, "民间非营利组织"),
    30: (15, "终止经营-列报"),
}

def main():
    base = Path(__file__).parent
    results = json.loads((base / ".tmp/results_30.json").read_text(encoding="utf-8"))
    results = {r["id"]: r for r in results}

    lines = []
    lines.append("# RAG 30 道题测试结果\n")
    lines.append("**测试时间**：2026-06-07")
    lines.append("**知识库**：299 知识点（覆盖 CPA 会计 1-30 章）")
    lines.append("**测试方式**：PC 端 `tools/rag_test.py` 流程（关键词召回 + DeepSeek 精排）")
    lines.append("**是否确认标准**：")
    lines.append("- ✓ **确认** = RAG 章节与预期章节一致")
    lines.append("- ⚠ **部分确认** = 章节对，但知识点名不够精确（同一章内有多个候选）")
    lines.append("- ✗ **不确认** = RAG 章节错（与预期章节不同）")
    lines.append("")
    lines.append("## 结果表\n")
    lines.append("| 题号 | 主题 | 预期章节 | RAG 章节 | RAG 知识点 | 置信度 | 是否确认 | 备注 |")
    lines.append("|------|------|---------|---------|-----------|--------|---------|------|")

    wrong_chapters = {}  # 错题章节 → 错题数
    partial_chapters = {}  # 部分对章节
    ok_count, partial_count, wrong_count = 0, 0, 0

    for qid in sorted(EXPECTED_FIX.keys()):
        r = results.get(qid, {})
        exp_ch, exp_kp = EXPECTED_FIX[qid]
        rag_ch = r.get("rag_chapter")
        rag_kp = r.get("rag_kp_name", "?")
        conf = r.get("confidence", 0)

        if rag_ch is None:
            status = "✗"
            note = f"❌ RAG 失败：{r.get('error','')}"
            wrong_count += 1
            wrong_chapters[exp_ch] = wrong_chapters.get(exp_ch, 0) + 1
        elif rag_ch == exp_ch:
            # 章节对，再看知识点名
            status = "✓"
            note = "无"
            ok_count += 1
        else:
            status = "✗"
            note = f"应归 **ch{exp_ch} {exp_kp}**"
            wrong_count += 1
            wrong_chapters[exp_ch] = wrong_chapters.get(exp_ch, 0) + 1
            # 也记 RAG 误归的章节
            wrong_chapters[f"误归:{rag_ch}"] = wrong_chapters.get(f"误归:{rag_ch}", 0) + 1

        lines.append(f"| {qid} | {r.get('topic','?')[:25]} | ch{exp_ch} | ch{rag_ch if rag_ch else '?'} | {rag_kp[:30]} | {conf:.0%} | {status} | {note} |")

    lines.append("")
    lines.append("## 统计\n")
    lines.append(f"- ✓ 确认：**{ok_count} / 30** ({ok_count/30:.0%})")
    lines.append(f"- ✗ 不确认：**{wrong_count} / 30** ({wrong_count/30:.0%})")
    lines.append("")
    lines.append("## 需要重新录入的章节（按错题数排序）\n")
    real_wrong = {ch: n for ch, n in wrong_chapters.items() if not (isinstance(ch, str) and ch.startswith("误归:"))}
    for ch, n in sorted(real_wrong.items(), key=lambda x: -x[1]):
        lines.append(f"- **ch{ch}**：{n} 道题分类错 → 需重抽/补知识点")
    lines.append("")
    lines.append("## RAG 误归的章节（次要参考）\n")
    misc = {int(ch.replace("误归:", "")): n for ch, n in wrong_chapters.items() if isinstance(ch, str) and ch.startswith("误归:")}
    for ch, n in sorted(misc.items(), key=lambda x: -x[1]):
        lines.append(f"- 误归到 ch{ch}：{n} 次")

    out_path = base.parent / "结果.md"
    out_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"✅ 写入 {out_path}")
    print(f"\n战绩：✓{ok_count} / ✗{wrong_count} / 总{ok_count+wrong_count}")
    print(f"需重抽章节：{', '.join(f'ch{c}' for c in real_wrong.keys())}")

if __name__ == "__main__":
    main()
