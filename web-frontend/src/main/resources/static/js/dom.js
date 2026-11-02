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

const SVG_NS = 'http://www.w3.org/2000/svg';
const ICONS = {
  swap: ['M7 7h11l-3-3', 'M17 17H6l3 3'],
  bag: ['M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z', 'M3 6h18', 'M16 10a4 4 0 0 1-8 0'],
  book: ['M4 19.5A2.5 2.5 0 0 1 6.5 17H20', 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'],
  bell: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],
  figure: ['M12 2a4 4 0 1 1 0 8a4 4 0 0 1 0-8z', 'M5 22v-3a7 7 0 0 1 14 0v3'],
  disc: ['M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z', 'M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z'],
  dice: ['M4 4h16v16H4z', 'M8.5 8.5h.01', 'M15.5 15.5h.01', 'M15.5 8.5h.01', 'M8.5 15.5h.01'],
  mug: ['M4 8h12v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3z', 'M16 10h2a2 2 0 0 1 0 4h-2', 'M8 2v3', 'M12 2v3'],
  moon: ['M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z'],
};

/** Icône décorative au trait (ignorée par les lecteurs d'écran). */
export function icon(name, cls = 'icon') {
  const svg = document.createElementNS(SVG_NS, 'svg');
  Object.entries({
    viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.8',
    'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true', class: cls,
  }).forEach(([k, v]) => svg.setAttribute(k, v));
  (ICONS[name] || ICONS.bag).forEach((d) => {
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', d);
    svg.append(path);
  });
  return svg;
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
