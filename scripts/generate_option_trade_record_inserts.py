#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从「股票交易 - 期权计划.csv」生成 option_trade_record 批量 INSERT（需与本表字段一致）。"""

import csv
import re
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

# 列索引（0-based）：序号,时间,代码,...,买入时间,期权代码,到期时间,买入,均价,数量,...,卖出时间,卖出价格,...
COL_BUY_TIME = 5
COL_OPTION_CODE = 6
COL_EXP = 7
COL_BUY_PRICE = 8
COL_QTY = 10
COL_SELL_TIME = 12
COL_SELL_PRICE = 13

# OCC 风格：YYMMDD + C|P + 行权价（整数或小数）
CODE_TOKEN_RE = re.compile(r"(\d{6})([CP])([\d.]+)", re.IGNORECASE)


def parse_tokens(cell: str):
    if not cell or not str(cell).strip():
        return []
    s = str(cell).strip().replace("\n", " ").replace("\r", " ")
    # 也匹配被空格分隔的多个 code
    out = []
    for m in CODE_TOKEN_RE.finditer(s):
        yy, cp, strike_s = m.group(1), m.group(2).upper(), m.group(3)
        y, mo, d = int(yy[:2]), int(yy[2:4]), int(yy[4:6])
        year = 2000 + y
        exp = f"{year:04d}-{mo:02d}-{d:02d}"
        right = "call" if cp == "C" else "put"
        strike = Decimal(strike_s).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        out.append({"exp": exp, "right": right, "strike": strike})
    return out


def sql_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def parse_date(s: str):
    s = (s or "").strip()
    if not s or s.lower() in ("null", "none"):
        return None
    # yyyy-MM-dd
    if re.match(r"^\d{4}-\d{2}-\d{2}$", s):
        return s
    return None


def parse_price(s: str):
    if s is None or str(s).strip() == "":
        return None
    try:
        return Decimal(str(s).strip()).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    except Exception:
        return None


def split_qty(qty: int, n: int):
    if n <= 0:
        return []
    if n == 1:
        return [qty]
    if qty >= n:
        base, rem = divmod(qty, n)
        return [base + (1 if i < rem else 0) for i in range(n)]
    # qty < n：双开 1 组 → 两腿各 1 张
    if qty == 1 and n == 2:
        return [1, 1]
    out = [0] * n
    for i in range(min(qty, n)):
        out[i] = 1
    return out


def main():
    csv_path = Path("/Users/Luonanqin/Downloads/股票交易 - 期权计划.csv")
    out_path = Path(
        "/Users/Luonanqin/study/intellij_idea_workspaces/splan/src/main/resources/sql/option_trade_record_inserts.sql"
    )
    if len(sys.argv) >= 2:
        csv_path = Path(sys.argv[1])
    if len(sys.argv) >= 3:
        out_path = Path(sys.argv[2])

    rows_out = []
    underlying_fill = ""

    with open(csv_path, "r", encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        for row in reader:
            if not row or len(row) < 14:
                continue
            # 代码列 forward fill
            code_cell = row[2].strip() if len(row) > 2 else ""
            if code_cell:
                underlying_fill = code_cell

            opt_cell = row[COL_OPTION_CODE] if len(row) > COL_OPTION_CODE else ""
            tokens = parse_tokens(opt_cell)
            if not tokens:
                continue

            buy_date = parse_date(row[COL_BUY_TIME] if len(row) > COL_BUY_TIME else "")
            if not buy_date:
                continue

            buy_p = parse_price(row[COL_BUY_PRICE] if len(row) > COL_BUY_PRICE else "")
            if buy_p is None:
                continue
            try:
                qty = int(float(row[COL_QTY])) if len(row) > COL_QTY and str(row[COL_QTY]).strip() else 0
            except ValueError:
                qty = 0
            if qty < 1:
                continue

            sell_date = parse_date(row[COL_SELL_TIME] if len(row) > COL_SELL_TIME else "")
            sell_p = parse_price(row[COL_SELL_PRICE] if len(row) > COL_SELL_PRICE else "")

            n = len(tokens)
            qtys = split_qty(qty, n)
            uc = sql_escape(underlying_fill) if underlying_fill else ""

            for i, tok in enumerate(tokens):
                exp_use = tok["exp"]
                strike = tok["strike"]
                right = tok["right"]
                q = qtys[i] if i < len(qtys) else qty
                if q < 1:
                    continue

                strike_sql = str(strike.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
                sell_sql = "NULL" if sell_date is None else f"'{sql_escape(sell_date)}'"
                sell_p_sql = "NULL" if sell_p is None else str(sell_p)

                line = (
                    f"('{uc}',{strike_sql},'{right}','{exp_use}','{buy_date}',"
                    f"{sell_sql},{buy_p},{sell_p_sql},{q},now(),now())"
                )
                rows_out.append(line)

    header_sql = """-- 由 scripts/generate_option_trade_record_inserts.py 自 CSV 生成
-- 执行前请先建表 option_trade_record

SET NAMES utf8mb4;

INSERT INTO `option_trade_record` (
  `underlying_code`,
  `strike_price`,
  `option_right`,
  `expiration_date`,
  `buy_date`,
  `sell_date`,
  `buy_price`,
  `sell_price`,
  `quantity`,
  `create_time`,
  `update_time`
) VALUES
"""
    body = ",\n".join(rows_out) + ";\n"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(header_sql + body, encoding="utf-8")
    print(f"Wrote {len(rows_out)} row(s) to {out_path}")


if __name__ == "__main__":
    main()
