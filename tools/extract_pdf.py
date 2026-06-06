#!/usr/bin/env python3
"""extract_pdf.py: 从 CPA 会计 PDF 抽取 30 章文本

用法：
  python extract_pdf.py <input.pdf> <output_dir>

输出：output_dir/ch01.txt ~ ch30.txt
"""
import sys
from pathlib import Path
import pdfplumber

CHAPTER_PATTERN = r"第[一二三四五六七八九十百零〇]+章\s+[一-龥]+"

def split_chapters(text: str) -> list[str]:
    import re
    matches = list(re.finditer(CHAPTER_PATTERN, text))
    if len(matches) < 1:
        return [text]
    chapters = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        chapters.append(text[start:end])
    return chapters

def main():
    if len(sys.argv) != 3:
        print("用法: python extract_pdf.py <input.pdf> <output_dir>")
        sys.exit(1)
    pdf_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    with pdfplumber.open(pdf_path) as pdf:
        full_text = "\n".join(p.extract_text() or "" for p in pdf.pages)
    chapters = split_chapters(full_text)
    for i, ch in enumerate(chapters[:30], 1):
        (out_dir / f"ch{i:02d}.txt").write_text(ch, encoding="utf-8")
    print(f"已抽取 {len(chapters[:30])} 章到 {out_dir}/")

if __name__ == "__main__":
    main()
