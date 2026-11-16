/**
 * app.js
 * -----------------------------------------------------------------------------
 * UI layer: connects the HTML to the pure logic in calculator.js.
 *
 * Responsibilities:
 *   - Button clicks and keyboard input  → Calculator methods
 *   - Calculator.getDisplay()           → update the display
 *   - History panel (last 10 results, saved in localStorage)
 *   - Light/dark theme toggle (saved in localStorage)
 *
 * No maths happens here; that keeps calculator.js easy to unit-test.
 * -----------------------------------------------------------------------------
 */
import { Calculator } from './calculator.js';

/* ========================================================================== */
/*  Setup                                                                     */
/* ========================================================================== */

const HISTORY_KEY = 'calculator.history';
const THEME_KEY = 'calculator.theme'; // also read by the inline script in index.html
const HISTORY_LIMIT = 10;
const PRESS_FEEDBACK_MS = 120;

/** Browser bar colours for each theme (match --color-bg in style.css). */
const THEME_COLORS = { light: '#f4f5f7', dark: '#0f1115' };

const calculator = new Calculator();

/** Cache DOM look-ups once instead of querying on every render. */
const els = {
  result: document.getElementById('result'),
  expression: document.getElementById('expression'),
  keypad: document.getElementById('keypad'),
  historyList: document.getElementById('history-list'),
  historyEmpty: document.getElementById('history-empty'),
  clearHistory: document.getElementById('clear-history'),
  themeToggle: document.getElementById('theme-toggle'),
  themeColorMeta: document.querySelector('meta[name="theme-color"]'),
};

/* ========================================================================== */
/*  Safe localStorage helpers                                                 */
/*  localStorage can throw (private browsing, storage disabled, quota full),  */
/*  so every access is wrapped in try/catch and the app keeps working.        */
/* ========================================================================== */

function readStorage(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* Ignore: the feature simply won't persist between visits. */
  }
}

/* ========================================================================== */
/*  Display                                                                   */
/* ========================================================================== */

/** Redraws the display from the calculator's current state. */
function render() {
  const { value, expression, isError } = calculator.getDisplay();

  els.result.textContent = value;
  els.expression.textContent = expression;
  els.result.classList.toggle('is-error', isError);

  // Shrink the font for long numbers so they fit on narrow screens.
  const length = value.length;
  els.result.dataset.size = length > 14 ? 'xs' : length > 10 ? 'sm' : 'md';
}

/* ========================================================================== */
/*  Input handling                                                            */
/* ========================================================================== */

/**
 * Runs one calculator action. Both mouse/touch and keyboard end up here,
 * so the two input methods always behave identically.
 *
 * @param {string} action  - digit | decimal | operator | sign | percent | backspace | clear | equals
 * @param {string} [value] - the digit or operator, when relevant
 */
function performAction(action, value) {
  switch (action) {
    case 'digit':
      calculator.inputDigit(value);
      break;
    case 'decimal':
      calculator.inputDecimal();
      break;
    case 'operator':
      calculator.inputOperator(value);
      break;
    case 'sign':
      calculator.toggleSign();
      break;
    case 'percent':
      calculator.percent();
      break;
    case 'backspace':
      calculator.backspace();
      break;
    case 'clear':
      calculator.clearAll();
      break;
    case 'equals': {
      const entry = calculator.equals();
      if (entry) addToHistory(entry); // null when nothing was calculated or on error
      break;
    }
    default:
      return; // unknown action: do nothing
  }
  render();
}

/**
 * Event delegation: one listener on the keypad handles every button.
 * event.target may be the button itself or something inside it, so we use
 * closest('button') to find the button that was clicked.
 */
els.keypad.addEventListener('click', (event) => {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const { action, digit, operator } = button.dataset;
  performAction(action, digit ?? operator);
});

/** Maps a keyboard key to [action, value]. Returns null for keys we ignore. */
function keyToAction(key) {
  if (key >= '0' && key <= '9') return ['digit', key];
  switch (key) {
    case '.':
    case ',': // many keyboards (e.g. European) use a comma as decimal separator
      return ['decimal'];
    case '+':
    case '-':
    case '*':
    case '/':
      return ['operator', key];
    case 'x':
    case 'X':
      return ['operator', '*'];
    case '%':
      return ['percent'];
    case 'Enter':
    case '=':
      return ['equals'];
    case 'Backspace':
      return ['backspace'];
    case 'Escape':
    case 'Delete':
      return ['clear'];
    default:
      return null;
  }
}

/** Finds the on-screen button matching an action so it can flash on key press. */
function findButton(action, value) {
  if (action === 'digit') return els.keypad.querySelector(`[data-digit="${value}"]`);
  if (action === 'operator') return els.keypad.querySelector(`[data-operator="${value}"]`);
  return els.keypad.querySelector(`[data-action="${action}"]`);
}

/** Briefly shows the "pressed" style on a button. */
function flashButton(button) {
  if (!button) return;
  button.classList.add('is-pressed');
  setTimeout(() => button.classList.remove('is-pressed'), PRESS_FEEDBACK_MS);
}

document.addEventListener('keydown', (event) => {
  // Leave browser shortcuts such as Ctrl+R or Cmd+C alone.
  if (event.ctrlKey || event.metaKey || event.altKey) return;

  const mapped = keyToAction(event.key);
  if (!mapped) return;

  // preventDefault stops: "/" opening Firefox's quick-find, Backspace navigating
  // back in old browsers, and Enter also "clicking" a focused button (which
  // would run the action twice).
  event.preventDefault();

  const [action, value] = mapped;
  performAction(action, value);
  flashButton(findButton(action, value));
});

/* ========================================================================== */
/*  History                                                                   */
/* ========================================================================== */

/** Loads history from localStorage, discarding anything malformed. */
function loadHistory() {
  try {
    const parsed = JSON.parse(readStorage(HISTORY_KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item) => typeof item?.expression === 'string' && typeof item?.result === 'string')
      .slice(0, HISTORY_LIMIT);
  } catch {
    return []; // invalid JSON
  }
}

let history = loadHistory();

function saveHistory() {
  writeStorage(HISTORY_KEY, JSON.stringify(history));
}

/** Adds a result to the top of the list and keeps only the newest 10. */
function addToHistory(entry) {
  history = [entry, ...history].slice(0, HISTORY_LIMIT);
  saveHistory();
  renderHistory();
}

/** Rebuilds the history list. textContent (not innerHTML) is used to avoid XSS. */
function renderHistory() {
  els.historyList.replaceChildren(
    ...history.map(({ expression, result }) => {
      const item = document.createElement('li');
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'history-item';
      button.dataset.result = result;
      button.setAttribute('aria-label', `${expression} equals ${result}. Use this result`);

      const exprEl = document.createElement('span');
      exprEl.className = 'history-expression';
      exprEl.textContent = `${expression} =`;

      const resultEl = document.createElement('span');
      resultEl.className = 'history-result';
      resultEl.textContent = result;

      button.append(exprEl, resultEl);
      item.append(button);
      return item;
    }),
  );

  const isEmpty = history.length === 0;
  els.historyEmpty.hidden = !isEmpty;
  els.clearHistory.disabled = isEmpty;
}

// Clicking a history entry loads its result so you can keep calculating with it.
els.historyList.addEventListener('click', (event) => {
  const button = event.target.closest('.history-item');
  if (!button) return;
  calculator.loadValue(button.dataset.result);
  render();
});

els.clearHistory.addEventListener('click', () => {
  history = [];
  saveHistory();
  renderHistory();
});

/* ========================================================================== */
/*  Theme                                                                     */
/* ========================================================================== */

/** Saved choice first; otherwise follow the operating system setting. */
function getInitialTheme() {
  const saved = readStorage(THEME_KEY);
  if (saved === 'light' || saved === 'dark') return saved;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const isDark = theme === 'dark';
  els.themeToggle.setAttribute('aria-pressed', String(isDark));
  els.themeToggle.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');
  els.themeColorMeta?.setAttribute('content', THEME_COLORS[theme]);
}

els.themeToggle.addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  writeStorage(THEME_KEY, next); // remember the user's choice
});

/* ========================================================================== */
/*  Start                                                                     */
/* ========================================================================== */

applyTheme(getInitialTheme());
renderHistory();
render();
