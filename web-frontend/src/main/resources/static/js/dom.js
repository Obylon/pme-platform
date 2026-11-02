// Construction du DOM sans innerHTML : tout texte venant des API est inséré comme texte (aucune injection HTML possible).

export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  Object.entries(attrs || {}).forEach(([key, value]) => {
    if (value === null || value === undefined || value === false) return;
    if (key === 'class') el.className = value;
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, String(value));
  });
  children.flat(Infinity).forEach((child) => {
    if (child === null || child === undefined || child === false) return;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  });
  return el;
}

export function clear(el, ...children) {
  el.replaceChildren(...children.flat(Infinity).filter((c) => c !== null && c !== undefined && c !== false));
  return el;
}

const money = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
const dateTime = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long' });

export const fmt = {
  price: (cents) => money.format(cents / 100),
  dateTime: (iso) => dateTime.format(new Date(iso)),
  date: (isoDate) => dateOnly.format(new Date(`${isoDate}T12:00:00`)),
};

let toastTimer;
export function toast(message, kind = 'info') {
  const box = document.getElementById('toast');
  box.textContent = message;
  box.dataset.kind = kind;
  box.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => box.classList.remove('visible'), 5000);
}

/** Bouton qui se désactive pendant l'action et affiche l'erreur éventuelle. */
export function actionButton(label, action, attrs = {}) {
  const button = h('button', { type: 'button', ...attrs }, label);
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      await action();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      button.disabled = false;
    }
  });
  return button;
}

let fieldSeq = 0;
export function field(label, control, hint) {
  fieldSeq += 1;
  const id = control.id || `champ-${fieldSeq}`;
  control.id = id;
  const hintEl = hint ? h('span', { class: 'hint', id: `${id}-hint` }, hint) : null;
  if (hintEl) control.setAttribute('aria-describedby', hintEl.id);
  return h('div', { class: 'field' }, h('label', { for: id }, label), control, hintEl);
}

export function select(name, options, value) {
  return h('select', { name },
    options.map(([v, label]) => h('option', { value: v, selected: v === value }, label)));
}
