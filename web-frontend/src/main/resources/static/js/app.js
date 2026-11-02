// Point d'entrée : connexion, en-tête et navigation entre les pages (routage par ancre #/...).
import { api } from './api.js';
import { handleRedirect, hasRole, login, logout, onChange, user } from './auth.js';
import { clear, h, toast } from './dom.js';
import { abonnementView, boutiqueView, homeView, notificationsView, trocView } from './views.js';

const ROUTES = {
  '': { view: homeView, title: 'Accueil' },
  troc: { view: trocView, title: 'Troc et dons' },
  boutique: { view: boutiqueView, title: 'Boutique' },
  abonnement: { view: abonnementView, title: 'Abonnement au fanzine' },
  notifications: { view: notificationsView, title: 'Mes alertes' },
};

function renderAccount() {
  const box = document.getElementById('account');
  const me = user();
  if (!me) {
    clear(box, h('button', { type: 'button', class: 'primary', onclick: () => login() }, 'Se connecter'));
    return;
  }
  clear(box,
    h('span', { class: 'who' }, 'Bonjour, ', h('strong', {}, me.name)),
    hasRole('moderator') ? h('span', { class: 'tag tag-mod' }, 'Modérateur') : null,
    h('button', { type: 'button', class: 'secondary', onclick: () => logout() }, 'Se déconnecter'));
}

async function refreshBadge() {
  const badge = document.getElementById('notif-count');
  if (!user()) {
    badge.hidden = true;
    return;
  }
  try {
    const page = await api.notifications();
    const unread = page.content.filter((n) => !n.read).length;
    badge.hidden = unread === 0;
    badge.textContent = String(unread);
    badge.setAttribute('aria-label', `${unread} alerte${unread > 1 ? 's' : ''} non lue${unread > 1 ? 's' : ''}`);
  } catch {
    badge.hidden = true;
  }
}

async function render() {
  const name = location.hash.replace(/^#\/?/, '').split('/')[0];
  const route = ROUTES[name] || ROUTES[''];
  const main = document.getElementById('contenu');
  document.title = `${route.title} – La Petite Maison de l’Épouvante`;
  document.querySelectorAll('.nav a').forEach((a) => {
    if (a.dataset.route === name) a.setAttribute('aria-current', 'page');
    else a.removeAttribute('aria-current');
  });
  clear(main, h('p', { class: 'loading' }, 'Chargement…'));
  try {
    await route.view(main);
  } catch (e) {
    clear(main, h('p', { class: 'error', role: 'alert' }, e.message));
  }
  main.focus({ preventScroll: true });
  refreshBadge();
}

async function start() {
  try {
    await handleRedirect();
  } catch (e) {
    toast(e.message, 'error');
  }
  renderAccount();
  onChange(() => {
    renderAccount();
    refreshBadge();
  });
  window.addEventListener('hashchange', render);
  await render();
}

start();
