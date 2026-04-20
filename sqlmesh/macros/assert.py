from __future__ import annotations

from sqlglot import exp

from sqlmesh.core.macros import MacroEvaluator, macro


@macro("ASSERT")
def assert_macro(
    evaluator: MacroEvaluator, condition: exp.Expression, message: exp.Expression
) -> exp.Case:
    """Raise a DuckDB error when the condition is false."""
    return exp.Case().when(exp.not_(condition), exp.func("ERROR", message))
