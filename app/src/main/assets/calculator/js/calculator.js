/**
 * calculator.js
 * -----------------------------------------------------------------------------
 * Pure calculation logic. This file contains NO DOM code, so it can be imported
 * by the browser (app.js) AND by Node's test runner (tests/calculator.test.js).
 *
 * It is made of two layers:
 *
 *   1. An expression engine (tokenize → toRPN → evaluateRPN).
 *      It turns text like "5 + 3 * 2" into a number WITHOUT eval() or
 *      new Function(), using Dijkstra's "shunting-yard" algorithm so that
 *      × and ÷ are applied before + and − (operator precedence).
 *
 *   2. A Calculator class: a small state machine that models what happens when
 *      the user presses buttons (digits, operators, AC, C, =, ...). The UI only
 *      calls its methods and then asks it what to display.
 * -----------------------------------------------------------------------------
 */

/* ========================================================================== */
/*  Errors                                                                    */
/* ========================================================================== */

/** Messages shown to the user when a calculation cannot be completed. */
export const ERRORS = Object.freeze({
  DIVIDE_BY_ZERO: 'Cannot divide by 0',
  INVALID: 'Invalid expression',
  OVERFLOW: 'Result too large',
});

/**
 * A custom error type. Using our own class lets the UI tell the difference
 * between "the user typed something impossible" (show the message) and a real
 * programming bug (let it crash loudly so we notice it).
 */
export class CalculatorError extends Error {
  constructor(message) {
    super(message);
    this.name = 'CalculatorError';
  }
}

/* ========================================================================== */
/*  Constants                                                                 */
/* ========================================================================== */

/** Higher number = binds tighter. × and ÷ are evaluated before + and −. */
const PRECEDENCE = Object.freeze({ '+': 1, '-': 1, '*': 2, '/': 2 });

/** Symbols people may type or see, mapped to the operator we use internally. */
const OPERATOR_ALIASES = Object.freeze({ '×': '*', x: '*', X: '*', '÷': '/', '−': '-' });

/** How each internal operator is shown on screen (typographic symbols). */
const DISPLAY_SYMBOLS = Object.freeze({ '+': '+', '-': '−', '*': '×', '/': '÷' });

/**
 * Results are rounded to this many significant digits. A JavaScript number
 * (IEEE-754 double) is accurate to roughly 15–17 digits, so rounding to 15
 * hides tiny binary errors such as 0.1 + 0.2 = 0.30000000000000004.
 */
const SIGNIFICANT_DIGITS = 15;

/** Maximum number of digits the user may type into a single number. */
export const MAX_INPUT_DIGITS = 15;

/* ========================================================================== */
/*  Small helpers                                                             */
/* ========================================================================== */

const isDigit = (ch) => ch >= '0' && ch <= '9';
const isDigitOrDot = (ch) => isDigit(ch) || ch === '.';

/** Converts "×", "÷", "−", "x" to "*", "/", "-". Other characters pass through. */
export function normalizeOperator(symbol) {
  return OPERATOR_ALIASES[symbol] ?? symbol;
}

/** True if `symbol` (after normalising) is one of + - * /. */
export function isOperator(symbol) {
  return Object.hasOwn(PRECEDENCE, normalizeOperator(symbol));
}

/**
 * Removes floating-point noise from a result.
 *   roundResult(0.1 + 0.2)  → 0.3
 *   roundResult(-0)         → 0   (we never want to show "-0" as a result)
 */
export function roundResult(value) {
  const rounded = Number.parseFloat(value.toPrecision(SIGNIFICANT_DIGITS));
  return Object.is(rounded, -0) ? 0 : rounded;
}

/** Turns a calculated number into the text shown on the display. */
export function formatNumber(value) {
  return String(roundResult(value));
}

/** Formats a number the user typed (no rounding, but "-0" becomes "0"). */
function formatOperand(value) {
  return Object.is(value, -0) ? '0' : String(value);
}

/** Counts digits only (ignores "-" and "."), used to limit input length. */
function countDigits(text) {
  return text.replace(/[^0-9]/g, '').length;
}

/* ========================================================================== */
/*  1. Expression engine                                                      */
/* ========================================================================== */

/**
 * Step 1 – tokenize: split a string into a list of tokens.
 *
 *   "5 + -3 × 2"  →  [ {number 5}, {operator +}, {number -3}, {operator *}, {number 2} ]
 *
 * A "+" or "-" that appears where a NUMBER is expected (at the very start, or
 * straight after another operator) is treated as the sign of that number.
 *
 * @param {string} expression
 * @returns {{type: 'number'|'operator', value: number|string}[]}
 */
export function tokenize(expression) {
  if (typeof expression !== 'string') {
    throw new CalculatorError(ERRORS.INVALID);
  }

  const tokens = [];
  let i = 0;

  while (i < expression.length) {
    const ch = normalizeOperator(expression[i]);

    // Ignore whitespace between tokens.
    if (ch === ' ') {
      i += 1;
      continue;
    }

    const previous = tokens[tokens.length - 1];
    const expectingNumber = previous === undefined || previous.type === 'operator';
    const isSign = (ch === '-' || ch === '+') && expectingNumber;

    // ---- Numbers (optionally with a leading sign) --------------------------
    if (isDigitOrDot(ch) || isSign) {
      let text = '';
      if (isSign) {
        if (ch === '-') text = '-';
        i += 1; // skip the sign character
      }

      let digits = 0;
      let dots = 0;
      while (i < expression.length && isDigitOrDot(expression[i])) {
        if (expression[i] === '.') {
          dots += 1;
          // "1.2.3" is not a valid number.
          if (dots > 1) throw new CalculatorError(ERRORS.INVALID);
        } else {
          digits += 1;
        }
        text += expression[i];
        i += 1;
      }

      // A lone "." or "-" with no digits is not a number.
      if (digits === 0) throw new CalculatorError(ERRORS.INVALID);

      tokens.push({ type: 'number', value: Number(text) });
      continue;
    }

    // ---- Binary operators ---------------------------------------------------
    if (isOperator(ch)) {
      // Two operators in a row such as "5 * / 3" (signs were handled above).
      if (expectingNumber) throw new CalculatorError(ERRORS.INVALID);
      tokens.push({ type: 'operator', value: ch });
      i += 1;
      continue;
    }

    // Anything else (letters, brackets, ...) is rejected. This is what makes
    // the parser SAFE: only digits, ".", and + - * / can ever be processed.
    throw new CalculatorError(ERRORS.INVALID);
  }

  return tokens;
}

/**
 * Checks that tokens follow the pattern: number (operator number)*
 * e.g. [5, +, 3] is valid; [5, +] or [+, 5] or [] are not.
 */
function validateTokens(tokens) {
  if (!Array.isArray(tokens) || tokens.length === 0 || tokens.length % 2 === 0) {
    throw new CalculatorError(ERRORS.INVALID);
  }
  tokens.forEach((token, index) => {
    const expected = index % 2 === 0 ? 'number' : 'operator';
    const ok =
      token?.type === expected &&
      (expected === 'number' ? Number.isFinite(token.value) : isOperator(token.value));
    if (!ok) throw new CalculatorError(ERRORS.INVALID);
  });
}

/**
 * Step 2 – shunting-yard: reorder tokens into Reverse Polish Notation (RPN),
 * where every operator comes AFTER its two operands. RPN has no precedence
 * rules left to worry about, which makes evaluating it trivial.
 *
 *   5 + 3 × 2   →   5 3 2 × +
 *   5 × 3 + 2   →   5 3 × 2 +
 *
 * Algorithm: numbers go straight to the output. Before pushing an operator on
 * the operator stack, pop every operator already there with HIGHER OR EQUAL
 * precedence (equal → left-to-right evaluation, so 8 − 3 − 2 = 3).
 */
export function toRPN(tokens) {
  const output = [];
  const operators = [];

  for (const token of tokens) {
    if (token.type === 'number') {
      output.push(token);
      continue;
    }
    while (
      operators.length > 0 &&
      PRECEDENCE[operators[operators.length - 1].value] >= PRECEDENCE[token.value]
    ) {
      output.push(operators.pop());
    }
    operators.push(token);
  }

  // Whatever operators are left are moved to the output, top of stack first.
  while (operators.length > 0) output.push(operators.pop());
  return output;
}

/** Applies one operator to two numbers. */
function applyOperator(a, b, operator) {
  switch (operator) {
    case '+':
      return a + b;
    case '-':
      return a - b;
    case '*':
      return a * b;
    case '/':
      if (b === 0) throw new CalculatorError(ERRORS.DIVIDE_BY_ZERO);
      return a / b;
    default:
      throw new CalculatorError(ERRORS.INVALID);
  }
}

/**
 * Step 3 – evaluate RPN with a stack.
 * Numbers are pushed; an operator pops two numbers, combines them and pushes
 * the result back. At the end exactly one number (the answer) remains.
 */
export function evaluateRPN(rpn) {
  const stack = [];
  for (const token of rpn) {
    if (token.type === 'number') {
      stack.push(token.value);
    } else {
      const b = stack.pop(); // right operand is on top
      const a = stack.pop();
      if (a === undefined || b === undefined) throw new CalculatorError(ERRORS.INVALID);
      stack.push(applyOperator(a, b, token.value));
    }
  }
  if (stack.length !== 1) throw new CalculatorError(ERRORS.INVALID);
  return stack[0];
}

/**
 * Evaluates an already-tokenized expression and returns a rounded number.
 * The Calculator class uses this directly so numbers never need to be turned
 * back into text and re-parsed.
 */
export function evaluateTokens(tokens) {
  validateTokens(tokens);
  const value = evaluateRPN(toRPN(tokens));
  if (!Number.isFinite(value)) throw new CalculatorError(ERRORS.OVERFLOW);
  return roundResult(value);
}

/**
 * Public one-liner: calculate("5 + 3 * 2") → 11
 * Throws CalculatorError for invalid input or division by zero.
 */
export function calculate(expression) {
  return evaluateTokens(tokenize(expression));
}

/** Turns tokens into human-readable text, e.g. "5 + 3 × 2". */
export function formatTokens(tokens) {
  return tokens
    .map((token) =>
      token.type === 'number' ? formatOperand(token.value) : DISPLAY_SYMBOLS[token.value],
    )
    .join(' ');
}

/* ========================================================================== */
/*  2. Calculator state machine                                               */
/* ========================================================================== */

/**
 * Models the calculator's keypad behaviour.
 *
 * State:
 *   tokens         – the part of the expression already "committed",
 *                    e.g. after typing "5 + 3 ×" it holds [5, +, 3, *].
 *   current        – the number currently being typed, as TEXT (so "0." and
 *                    "-0" can exist while typing). '' right after an operator.
 *   lastExpression – the expression shown above the result after "=".
 *   justEvaluated  – true right after "=", so the next digit starts fresh.
 *   error          – an error message, or null.
 */
export class Calculator {
  constructor() {
    this.clearAll();
  }

  /** AC – reset everything. */
  clearAll() {
    this.tokens = [];
    this.current = '0';
    this.lastExpression = '';
    this.justEvaluated = false;
    this.error = null;
  }

  /** After an error or a result, typing a new number starts a new calculation. */
  #startFreshIfNeeded() {
    if (this.error !== null || this.justEvaluated) this.clearAll();
  }

  /** After a result, operators / ± / % continue from that result. */
  #continueFromResult() {
    if (this.justEvaluated) {
      this.tokens = [];
      this.lastExpression = '';
      this.justEvaluated = false;
    }
  }

  /** Digit keys 0–9. */
  inputDigit(digit) {
    const d = String(digit);
    if (d.length !== 1 || !isDigit(d)) return;
    this.#startFreshIfNeeded();

    if (this.current === '0') {
      this.current = d; // replace the leading zero: "0" → "7"
    } else if (this.current === '-0') {
      this.current = `-${d}`;
    } else if (countDigits(this.current) < MAX_INPUT_DIGITS) {
      this.current += d;
    }
  }

  /** "." key – only one decimal point is allowed per number. */
  inputDecimal() {
    this.#startFreshIfNeeded();
    if (this.current.includes('.')) return;
    this.current = this.current === '' ? '0.' : `${this.current}.`;
  }

  /** + − × ÷ keys. */
  inputOperator(symbol) {
    const operator = normalizeOperator(symbol);
    if (!isOperator(operator) || this.error !== null) return;
    this.#continueFromResult();

    if (this.current === '') {
      // Operator pressed twice in a row ("5 + ×"): replace the last operator.
      if (this.tokens.length > 0) {
        this.tokens[this.tokens.length - 1] = { type: 'operator', value: operator };
      }
      return;
    }

    this.tokens.push(
      { type: 'number', value: Number(this.current) },
      { type: 'operator', value: operator },
    );
    this.current = '';
  }

  /** ± key – flips the sign of the number being typed. */
  toggleSign() {
    if (this.error !== null || this.current === '') return;
    this.#continueFromResult();
    this.current = this.current.startsWith('-') ? this.current.slice(1) : `-${this.current}`;
  }

  /** % key – divides the current number by 100 (50 → 0.5). */
  percent() {
    if (this.error !== null || this.current === '') return;
    this.#continueFromResult();
    this.current = formatNumber(Number(this.current) / 100);
  }

  /** C / Backspace – delete the last character typed. */
  backspace() {
    if (this.error !== null) {
      this.clearAll();
      return;
    }
    if (this.justEvaluated) {
      // A result is not edited digit by digit; just clear the expression line.
      this.lastExpression = '';
      return;
    }
    if (this.current === '') {
      // Nothing typed after the operator: remove the operator and go back to
      // editing the previous number.
      if (this.tokens.length >= 2) {
        this.tokens.pop(); // operator
        this.current = formatOperand(this.tokens.pop().value);
      }
      return;
    }
    if (/e/i.test(this.current)) {
      // Numbers like "1e+21" can't sensibly lose one character.
      this.current = '0';
      return;
    }
    this.current = this.current.slice(0, -1);
    if (this.current === '' || this.current === '-') this.current = '0';
  }

  /**
   * "=" key. Returns { expression, result } on success (so the UI can store it
   * in the history), or null if there was nothing to calculate or an error.
   */
  equals() {
    if (this.error !== null || this.justEvaluated) return null;

    const tokens = [...this.tokens];
    if (this.current === '') {
      tokens.pop(); // "5 + =" → ignore the dangling operator
    } else {
      tokens.push({ type: 'number', value: Number(this.current) });
    }
    if (tokens.length < 3) return null; // a single number: nothing to do

    const expression = formatTokens(tokens);
    try {
      const result = formatNumber(evaluateTokens(tokens));
      this.tokens = [];
      this.current = result;
      this.lastExpression = `${expression} =`;
      this.justEvaluated = true;
      return { expression, result };
    } catch (err) {
      if (!(err instanceof CalculatorError)) throw err; // real bug: re-throw
      this.tokens = [];
      this.current = '0';
      this.lastExpression = `${expression} =`;
      this.justEvaluated = false;
      this.error = err.message;
      return null;
    }
  }

  /** Loads a number (e.g. a result picked from the history) into the display. */
  loadValue(value) {
    const text = String(value);
    if (!Number.isFinite(Number(text)) || text.trim() === '') return;
    this.clearAll();
    this.current = text;
    this.justEvaluated = true; // typing a digit replaces it; an operator uses it
  }

  /**
   * Everything the UI needs to draw the display.
   * @returns {{ value: string, expression: string, isError: boolean }}
   */
  getDisplay() {
    if (this.error !== null) {
      return { value: this.error, expression: this.lastExpression, isError: true };
    }

    let value = this.current;
    if (value === '') {
      // Right after an operator, keep showing the previous number.
      const lastNumber = this.tokens[this.tokens.length - 2];
      value = lastNumber ? formatOperand(lastNumber.value) : '0';
    }

    const expression = this.justEvaluated ? this.lastExpression : formatTokens(this.tokens);
    return { value, expression, isError: false };
  }
}
