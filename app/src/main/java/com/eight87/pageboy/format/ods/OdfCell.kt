package com.eight87.pageboy.format.ods

/**
 * Phase L — sealed cell model for the ODS renderer. Mirrors the cell
 * subset format-ods.md prescribes for v1 (text / number / boolean /
 * date / formula-cached / empty). Each variant carries the cached
 * value LibreOffice / Calc computed at last save — pageboy never
 * evaluates formulas.
 *
 * R.X.2 — sealed dispatch. Adding a cell kind = one new variant + one
 * arm in the body's `when (cell)`.
 *
 * `formatted` is the display string the parser computed from the cell's
 * raw value (number style / date style applied where the parser knows
 * the format; otherwise the value's `toString()`). The body always
 * renders `formatted` so per-cell rendering stays a single `Text`
 * Composable.
 */
sealed interface OdfCell {

  val formatted: String

  data class Text(override val formatted: String) : OdfCell

  data class Number(val value: Double, override val formatted: String) : OdfCell

  data class Bool(val value: Boolean, override val formatted: String) : OdfCell

  /** Date — stored as ISO string per the `office:date-value` attribute. */
  data class Date(val iso: String, override val formatted: String) : OdfCell

  /**
   * Formula with cached value. Pageboy displays the cached value; the
   * formula text is preserved for diagnostics but not surfaced.
   *
   * Spec gotcha #2 (format-ods.md): trust cached values over formulas;
   * if a cell has *only* a formula with no cached value, the parser
   * emits this with `formatted = "#FORMULA"` and `cachedValue = null`.
   */
  data class Formula(
    val formula: String,
    val cachedValue: OdfCell?,
    override val formatted: String,
  ) : OdfCell

  /** Visually empty cell — placeholder so the grid renders the gap. */
  data object Empty : OdfCell {
    override val formatted: String = ""
  }
}
