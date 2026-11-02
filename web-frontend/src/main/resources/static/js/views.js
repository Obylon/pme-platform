// Pages du site. Chaque vue reçoit l'élément <main> et le remplit.
import { api } from './api.js';
import { hasRole, login, user } from './auth.js';
import { actionButton, clear, field, fmt, h, select, toast } from './dom.js';

export const CATEGORIES = [
  ['FIGURINE', 'Figurines'], ['BD', 'BD'], ['BLURAY', 'Blu-ray'], ['DVD', 'DVD'],
  ['JEU', 'Jeux'], ['FANZINE', 'Fanzine'], ['GOODIES', 'Goodies'], ['AUTRE', 'Autre'],
];
const categoryLabel = (code) => CATEGORIES.find(([c]) => c === code)?.[1] || code;
const TYPES = [['EXCHANGE', 'Échange'], ['GIVEAWAY', 'Don']];
const typeLabel = (code) => TYPES.find(([c]) => c === code)?.[1] || code;
const PLANS = [
  { code: 'PAPER', name: 'Papier', price: 3200, text: '4 numéros par an livrés chez vous' },
  { code: 'DIGITAL', name: 'Numérique', price: 2000, text: '4 numéros par an à lire en ligne' },
  { code: 'BOTH', name: 'Papier + numérique', price: 4200, text: 'La version papier et la liseuse en ligne' },
];
const splitWords = (text) => text.split(',').map((w) => w.trim().toLowerCase()).filter(Boolean);

function pageHeader(eyebrow, title, intro) {
  return h('header', { class: 'page-header' },
    h('p', { class: 'eyebrow' }, eyebrow), h('h1', {}, title), intro ? h('p', { class: 'lead' }, intro) : null);
}

function loginPrompt(message) {
  return h('div', { class: 'panel prompt' },
    h('p', {}, message),
    h('button', { type: 'button', class: 'primary', onclick: () => login() }, 'Se connecter'));
}

function errorBox(e) {
  return h('p', { class: 'error', role: 'alert' }, e.message);
}

// ---------- Annonces ----------

function listingCard(listing, refresh) {
  const me = user();
  const mine = me && listing.ownerId === me.id;
  const actions = h('div', { class: 'card-actions' });
  if (mine) {
    actions.append(actionButton('Retirer mon annonce', async () => {
      await api.withdrawListing(listing.id);
      toast('Annonce retirée.');
      await refresh();
    }, { class: 'secondary' }));
  } else if (hasRole('moderator')) {
    const reason = h('input', { type: 'text', required: true, maxlength: 500, placeholder: 'Motif du retrait' });
    const form = h('form', {
      class: 'inline-form',
      onsubmit: async (e) => {
        e.preventDefault();
        try {
          await api.moderateListing(listing.id, reason.value.trim());
          toast('Annonce modérée.');
          await refresh();
        } catch (err) {
          toast(err.message, 'error');
        }
      },
    }, field('Motif de modération', reason), h('button', { type: 'submit', class: 'danger' }, 'Retirer (modération)'));
    actions.append(form);
  }
  return h('article', { class: 'card listing' },
    h('div', { class: 'card-top' },
      h('span', { class: 'tag' }, categoryLabel(listing.category)),
      h('span', { class: `tag ${listing.type === 'GIVEAWAY' ? 'tag-give' : 'tag-swap'}` }, typeLabel(listing.type)),
      mine ? h('span', { class: 'tag tag-mine' }, 'Mon annonce') : null),
    h('h3', {}, listing.title),
    listing.description ? h('p', {}, listing.description) : null,
    listing.keywords?.length ? h('ul', { class: 'keywords', 'aria-label': 'Mots-clés' },
      listing.keywords.map((k) => h('li', {}, k))) : null,
    h('p', { class: 'meta' }, `Publiée le ${fmt.dateTime(listing.createdAt)}`),
    actions.childElementCount ? actions : null);
}

function publishForm(onPublished) {
  const title = h('input', { type: 'text', name: 'title', required: true, maxlength: 120 });
  const description = h('textarea', { name: 'description', rows: 3, maxlength: 2000 });
  const category = select('category', CATEGORIES, 'FIGURINE');
  const type = select('type', TYPES, 'EXCHANGE');
  const keywords = h('input', { type: 'text', name: 'keywords' });
  const submit = h('button', { type: 'submit', class: 'primary' }, 'Publier l’annonce');
  return h('form', {
    class: 'panel form-grid',
    'aria-labelledby': 'publish-title',
    onsubmit: async (e) => {
      e.preventDefault();
      submit.disabled = true;
      try {
        const words = splitWords(keywords.value);
        if (words.length > 10) throw new Error('10 mots-clés au maximum.');
        await api.publishListing({
          title: title.value.trim(), description: description.value.trim() || null,
          category: category.value, type: type.value, keywords: words,
        });
        e.target.reset();
        toast('Annonce publiée : les membres intéressés sont notifiés.');
        await onPublished();
      } catch (err) {
        toast(err.message, 'error');
      } finally {
        submit.disabled = false;
      }
    },
  },
  h('h2', { id: 'publish-title' }, 'Proposer un objet'),
  field('Titre', title),
  field('Catégorie', category),
  field('Type', type),
  field('Mots-clés', keywords, 'Séparés par des virgules, 10 au maximum (ex. : orc, festival)'),
  h('div', { class: 'span-2' }, field('Description', description)),
  h('div', { class: 'span-2' }, submit));
}

export async function trocView(main) {
  const list = h('div', { class: 'grid', 'aria-live': 'polite' });
  const filterCategory = select('category', [['', 'Toutes les catégories'], ...CATEGORIES], '');
  const filterType = select('type', [['', 'Échanges et dons'], ...TYPES], '');
  const refresh = async () => {
    try {
      const page = await api.listings({ category: filterCategory.value, type: filterType.value, size: 30 });
      clear(list, page.content.length
        ? page.content.map((l) => listingCard(l, refresh))
        : h('p', { class: 'empty' }, 'Aucune annonce pour ces critères.'));
    } catch (e) {
      clear(list, errorBox(e));
    }
  };
  filterCategory.addEventListener('change', refresh);
  filterType.addEventListener('change', refresh);
  clear(main,
    pageHeader('Espace communautaire', 'Troc et dons',
      'Échangez ou donnez vos goodies d’horreur. Les membres intéressés par la catégorie ou un mot-clé sont prévenus.'),
    user() ? publishForm(refresh) : loginPrompt('Connectez-vous pour proposer un objet.'),
    h('section', { 'aria-labelledby': 'liste-annonces' },
      h('div', { class: 'section-head' },
        h('h2', { id: 'liste-annonces' }, 'Annonces publiées'),
        h('div', { class: 'filters' }, field('Catégorie', filterCategory), field('Type', filterType))),
      list));
  await refresh();
}

// ---------- Alertes ----------

export async function notificationsView(main) {
  if (!user()) {
    clear(main, pageHeader('Mes alertes', 'Mes alertes'), loginPrompt('Connectez-vous pour gérer vos alertes.'));
    return;
  }
  const [prefs, notifs] = await Promise.all([
    api.preferences().catch(() => ({ categories: [], keywords: [], enabled: true })),
    api.notifications(),
  ]);
  const boxes = CATEGORIES.map(([code, label]) => h('label', { class: 'check' },
    h('input', { type: 'checkbox', value: code, checked: prefs.categories?.includes(code) }), label));
  const keywords = h('input', { type: 'text', value: (prefs.keywords || []).join(', ') });
  const enabled = h('input', { type: 'checkbox', checked: prefs.enabled });
  const form = h('form', {
    class: 'panel',
    onsubmit: async (e) => {
      e.preventDefault();
      try {
        await api.savePreferences({
          categories: boxes.map((b) => b.firstChild).filter((i) => i.checked).map((i) => i.value),
          keywords: splitWords(keywords.value),
          enabled: enabled.checked,
        });
        toast('Centres d’intérêt enregistrés.');
      } catch (err) {
        toast(err.message, 'error');
      }
    },
  },
  h('h2', {}, 'Mes centres d’intérêt'),
  h('fieldset', {}, h('legend', {}, 'Catégories suivies'), h('div', { class: 'checks' }, boxes)),
  field('Mots-clés suivis', keywords, 'Séparés par des virgules (ex. : orc, evil ed)'),
  h('label', { class: 'check' }, enabled, 'Recevoir les alertes'),
  h('button', { type: 'submit', class: 'primary' }, 'Enregistrer'));

  const list = notifs.content.length
    ? h('ul', { class: 'notif-list' }, notifs.content.map((n) => h('li', { class: n.read ? '' : 'unread' },
      h('p', {}, n.message), h('p', { class: 'meta' }, fmt.dateTime(n.createdAt)),
      h('a', { href: '#/troc' }, 'Voir les annonces'))))
    : h('p', { class: 'empty' }, 'Aucune alerte pour le moment.');
  clear(main,
    pageHeader('Mes alertes', 'Mes alertes',
      'Choisissez ce qui vous intéresse : vous êtes prévenu dès qu’une annonce correspondante est publiée.'),
    h('div', { class: 'two-cols' }, form,
      h('section', { class: 'panel', 'aria-labelledby': 'mes-alertes' },
        h('h2', { id: 'mes-alertes' }, `Alertes reçues (${notifs.totalElements})`), list)));
}

// ---------- Boutique ----------

function productCard(product, onAdded) {
  const qty = h('input', { type: 'number', min: 1, max: 10, value: 1, class: 'qty' });
  let buy;
  if (!product.available) buy = h('p', { class: 'meta' }, 'Épuisé');
  else if (!user()) buy = h('button', { type: 'button', class: 'secondary', onclick: () => login() }, 'Se connecter pour acheter');
  else {
    buy = h('div', { class: 'buy' }, field('Quantité', qty), actionButton('Ajouter au panier', async () => {
      await api.addToCart(product.id, Number(qty.value));
      toast(`${product.name} ajouté au panier.`);
      await onAdded();
    }, { class: 'primary' }));
  }
  return h('article', { class: 'card product' },
    h('div', { class: 'card-top' },
      h('span', { class: 'tag' }, categoryLabel(product.category)),
      product.exclusive ? h('span', { class: 'tag tag-excl' }, 'Exclusivité Evil Ed') : null),
    h('h3', {}, product.name),
    h('p', { class: 'price' }, fmt.price(product.priceCents)),
    h('p', { class: 'meta' }, product.available ? `En stock : ${product.stock}` : 'Rupture de stock'),
    buy);
}

export async function boutiqueView(main) {
  const grid = h('div', { class: 'grid' });
  const cartBox = h('section', { class: 'panel cart', 'aria-labelledby': 'panier' });
  const ordersBox = h('section', { class: 'panel', 'aria-labelledby': 'commandes' });
  const filter = select('category', [['', 'Tout le catalogue'], ...CATEGORIES.filter(([c]) => c !== 'AUTRE' && c !== 'DVD')], '');

  const loadOrders = async () => {
    const orders = await api.orders();
    clear(ordersBox, h('h2', { id: 'commandes' }, 'Mes commandes'),
      orders.content.length ? h('ul', { class: 'orders' }, orders.content.map((o) => h('li', {},
        h('p', {}, h('strong', {}, fmt.price(o.totalCents)), ` · ${o.status === 'CONFIRMED' ? 'Confirmée' : o.status}`),
        h('p', { class: 'meta' }, `${fmt.dateTime(o.createdAt)} · ${o.lines.map((l) => `${l.quantity} × ${l.productName}`).join(', ')}`))))
        : h('p', { class: 'empty' }, 'Aucune commande.'));
  };
  const loadCart = async () => {
    if (!user()) return;
    const cart = await api.cart();
    clear(cartBox, h('h2', { id: 'panier' }, 'Mon panier'),
      cart.lines.length
        ? [h('ul', { class: 'cart-lines' }, cart.lines.map((l) => h('li', {},
          h('span', {}, `${l.quantity} × ${l.name}`), h('span', {}, fmt.price(l.lineTotalCents)),
          actionButton('Retirer', async () => { await api.removeFromCart(l.productId); await loadCart(); },
            { class: 'link', 'aria-label': `Retirer ${l.name} du panier` })))),
        h('p', { class: 'total' }, 'Total ', h('strong', {}, fmt.price(cart.totalCents))),
        actionButton('Commander', async () => {
          const order = await api.checkout();
          toast(`Commande confirmée : ${fmt.price(order.totalCents)} (paiement simulé).`, 'success');
          await Promise.all([loadCart(), loadProducts(), loadOrders()]);
        }, { class: 'primary' })]
        : h('p', { class: 'empty' }, 'Votre panier est vide.'));
  };
  const loadProducts = async () => {
    try {
      const page = await api.products(filter.value);
      clear(grid, page.content.map((p) => productCard(p, loadCart)));
    } catch (e) {
      clear(grid, errorBox(e));
    }
  };
  filter.addEventListener('change', loadProducts);

  clear(main,
    pageHeader('Boutique', 'La boutique de l’Épouvante',
      'Figurines exclusives Evil Ed, BD, Blu-ray restaurés et numéros du fanzine.'),
    h('div', { class: 'shop-layout' },
      h('section', { 'aria-labelledby': 'catalogue' },
        h('div', { class: 'section-head' }, h('h2', { id: 'catalogue' }, 'Catalogue'), h('div', { class: 'filters' }, field('Rayon', filter))),
        grid),
      user() ? h('div', { class: 'side' }, cartBox, ordersBox) : loginPrompt('Connectez-vous pour acheter et suivre vos commandes.')));
  await loadProducts();
  if (user()) await Promise.all([loadCart(), loadOrders()]);
}

// ---------- Abonnement ----------

export async function abonnementView(main) {
  const status = h('section', { class: 'panel', 'aria-live': 'polite' });
  let current = null;
  const renderStatus = () => clear(status, h('h2', {}, 'Mon abonnement'),
    current
      ? h('p', {}, `Formule ${PLANS.find((p) => p.code === current.plan)?.name || current.plan} · `,
        h('strong', {}, current.status === 'ACTIVE' ? 'actif' : 'expiré'), ` jusqu’au ${fmt.date(current.endDate)}`)
      : h('p', { class: 'empty' }, user() ? 'Aucun abonnement en cours.' : 'Connectez-vous pour voir votre abonnement.'));
  if (user()) {
    current = await api.subscription().catch((e) => { if (e.status === 404) return null; throw e; });
  }
  const cards = PLANS.map((plan) => h('article', { class: 'card plan' },
    h('h3', {}, plan.name),
    h('p', { class: 'price' }, fmt.price(plan.price), h('span', { class: 'per' }, ' / an')),
    h('p', {}, plan.text),
    user()
      ? actionButton(current?.status === 'ACTIVE' ? 'Renouveler (+1 an)' : 'S’abonner', async () => {
        current = await api.subscribe(plan.code);
        toast(`Abonnement ${plan.name} actif jusqu’au ${fmt.date(current.endDate)}.`, 'success');
        renderStatus();
      }, { class: 'primary' })
      : h('button', { type: 'button', class: 'secondary', onclick: () => login() }, 'Se connecter pour s’abonner')));
  renderStatus();
  clear(main,
    pageHeader('Le fanzine', 'Abonnement au fanzine',
      'Quatre numéros par an. Un renouvellement prolonge d’un an à partir de la fin de l’abonnement en cours.'),
    h('div', { class: 'grid plans' }, cards),
    status);
}

// ---------- Accueil ----------

export async function homeView(main) {
  const latest = h('div', { class: 'grid' });
  const exclusives = h('div', { class: 'grid' });
  clear(main,
    h('section', { class: 'hero' },
      h('p', { class: 'eyebrow' }, 'Fanzine · Boutique · Communauté'),
      h('h1', {}, 'Le repaire des passionnés d’horreur'),
      h('p', { class: 'lead' }, 'Échangez vos trouvailles, soutenez le collectif Evil Ed et recevez le fanzine chez vous.'),
      h('div', { class: 'hero-actions' },
        h('a', { class: 'button primary', href: '#/troc' }, 'Voir les annonces'),
        h('a', { class: 'button secondary', href: '#/boutique' }, 'Visiter la boutique'))),
    h('section', { 'aria-labelledby': 'dernieres' }, h('h2', { id: 'dernieres' }, 'Dernières annonces'), latest),
    h('section', { 'aria-labelledby': 'exclus' }, h('h2', { id: 'exclus' }, 'Exclusivités Evil Ed'), exclusives));
  const refresh = async () => {
    const page = await api.listings({ size: 3 });
    clear(latest, page.content.length ? page.content.map((l) => listingCard(l, refresh)) : h('p', { class: 'empty' }, 'Aucune annonce.'));
  };
  await Promise.all([
    refresh().catch((e) => clear(latest, errorBox(e))),
    api.products().then((page) => clear(exclusives,
      page.content.filter((p) => p.exclusive).slice(0, 3).map((p) => productCard(p, async () => {}))))
      .catch((e) => clear(exclusives, errorBox(e))),
  ]);
}
