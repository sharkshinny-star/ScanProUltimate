"""Writes a one-page PDF with a 4x3 table in a real text layer, with no dependencies: a test input for ScanPro's
PDF tools (PDF to Excel, Word, PowerPoint, Repair...). Usage: python3 make_test_pdf.py <output.pdf>"""
import sys

rows = [("Item", "Qty", "Price"), ("Apples", "12", "4.50"), ("Pears", "3", "9.00"), ("Mangoes", "20", "2.25")]
stream = "\n".join(
    f"BT /F1 14 Tf {72 + c * 170} {700 - r * 26} Td ({cell}) Tj ET"
    for r, row in enumerate(rows) for c, cell in enumerate(row)
)
items = [
    (1, "<< /Type /Catalog /Pages 2 0 R >>"),
    (2, "<< /Type /Pages /Kids [4 0 R] /Count 1 >>"),
    (3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
    (4, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R /Resources << /Font << /F1 3 0 R >> >> >>"),
    (5, f"<< /Length {len(stream)} >>\nstream\n{stream}\nendstream"),
]
out = b"%PDF-1.4\n"
offsets = {}
for number, body in items:
    offsets[number] = len(out)
    out += f"{number} 0 obj\n{body}\nendobj\n".encode()
xref = len(out)
out += f"xref\n0 {len(items) + 1}\n0000000000 65535 f \n".encode()
for number, _ in items:
    out += f"{offsets[number]:010d} 00000 n \n".encode()
out += f"trailer\n<< /Size {len(items) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
open(sys.argv[1], "wb").write(out)
print(sys.argv[1])
