#!/usr/bin/env python3
"""extract_middle.py: 从中册 PDF 按已知起始页精准切分 13-20 章

中册 PDF 物理页号（pdfplumber 索引）与教材逻辑章节号不一致，
不能依赖正则。本脚本硬编码 8 章起始页（从扫描结果得出）。

用法: python extract_middle.py <input.pdf> <output_dir>
"""
import sys
from pathlib import Path
import pdfplumber

# 物理页 → 章节号
CHAPTER_PAGES = {
    13: 5,    # 第十三章 金融工具
    14: 66,   # 第十四章 租赁
    15: 112,  # 第十五章 持有待售
    16: 138,  # 第十六章 所有者权益
    17: 153,  # 第十七章 收入、费用和利润
    18: 223,  # 第十八章 政府补助
    19: 241,  # 第十九章 所得税
    20: 286,  # 第二十章 非货币性资产交换
}

def main():
    if len(sys.argv) != 3:
        print("用法: python extract_middle.py <input.pdf> <output_dir>")
        sys.exit(1)
    pdf_path = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)

    with pdfplumber.open(pdf_path) as pdf:
        total = len(pdf.pages)
        chapter_nums = sorted(CHAPTER_PAGES.keys())

        for idx, ch in enumerate(chapter_nums):
            start = CHAPTER_PAGES[ch]
            # 最后一章到 PDF 末尾；其他章到下一章起始页前
            end = CHAPTER_PAGES[chapter_nums[idx+1]] - 1 if idx+1 < len(chapter_nums) else total

            texts = []
            for p in range(start, end + 1):
                if p > total:
                    break
                t = pdf.pages[p-1].extract_text() or ""
                texts.append(t)
            full = "\n".join(texts)
            out_path = out_dir / f"ch{ch:02d}.txt"
            out_path.write_text(full, encoding="utf-8")
            print(f"第 {ch:2d} 章: p{start}-p{end} ({end-start+1} 页, {len(full)} 字符) → {out_path.name}")

if __name__ == "__main__":
    main()
