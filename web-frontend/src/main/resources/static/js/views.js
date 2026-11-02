// Pages du site (charte « salle obscure »). Chaque vue reçoit l'élément <main> et le remplit.
import { api } from './api.js';
import { hasRole, login, user } from './auth.js';
import { actionButton, clear, field, fmt, h, icon, select, toast } from './dom.js';

export const CATEGORIES = [
  ['FIGURINE', 'Figurines'], ['BD', 'BD'], ['BLURAY', 'Blu-ray'], ['DVD', 'DVD'],
  ['JEU', 'Jeux'], ['FANZINE', 'Fanzine'], ['GOODIES', 'Goodies'], ['AUTRE', 'Autre'],
];
const CATEGORY_ICON = { FIGURINE: 'figure', BD: 'book', FANZINE: 'book', BLURAY: 'disc', DVD: 'disc', JEU: 'dice', GOODIES: 'mug' };
const categoryLabel = (code) => CATEGORIES.find(([c]) => c === code)?.[1] || code;
const TYPES = [['EXCHANGE', 'Échange'], ['GIVEAWAY', 'Don']];
const typeLabel = (code) => TYPES.find(([c]) => c === code)?.[1] || code;
const PLANS = [
  { code: 'DIGITAL', tag: 'Petit budget', name: 'Numérique', price: 2000, text: 'Les 4 numéros à lire en ligne, dès leur parution.' },
  { code: 'BOTH', tag: 'Le plus choisi', name: 'Papier + numérique', price: 4200, text: 'Le fanzine chez vous et la liseuse en ligne.', featured: true },
  { code: 'PAPER', tag: 'Pour collectionner', name: 'Papier', price: 3200, text: 'Les 4 numéros imprimés, livrés à domicile.' },
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

/** Rangée de filtres en pilules (boutons à bascule accessibles). */
function pills(options, current, onPick, label) {
  const group = h('div', { class: 'pills', role: 'group', 'aria-label': label });
  const render = (value) => clear(group, options.map(([v, text]) => h('button', {
    type: 'button', class: 'pill', 'aria-pressed': String(v === value),
    onclick: () => { render(v); onPick(v); },
  }, text)));
  render(current);
  return group;
}

function visuel(category, badge) {
  return h('div', { class: 'visuel' }, icon(CATEGORY_ICON[category] || 'bag', 'visuel-icon'), badge);
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
    actions.append(h('form', {
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
    }, field('Motif de modération', reason), h('button', { type: 'submit', class: 'danger' }, 'Retirer')));
  }
  return h('article', { class: 'card listing' },
    visuel(listing.category),
    h('div', { class: 'card-body' },
      h('div', { class: 'card-top' },
        h('span', { class: 'tag' }, categoryLabel(listing.category)),
        h('span', { class: `tag ${listing.type === 'GIVEAWAY' ? 'tag-give' : 'tag-swap'}` }, typeLabel(listing.type)),
        mine ? h('span', { class: 'tag tag-mine' }, 'Mon annonce') : null),
      h('h3', {}, listing.title),
      listing.description ? h('p', { class: 'meta' }, listing.description) : null,
      listing.keywords?.length ? h('ul', { class: 'keywords', 'aria-label': 'Mots-clés' },
        listing.keywords.map((k) => h('li', {}, k))) : null,
      h('p', { class: 'meta' }, `Publiée le ${fmt.dateTime(listing.createdAt)}`),
      actions.childElementCount ? actions : null));
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
        toast('Annonce publiée : les membres intéressés sont prévenus.', 'success');
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
  const filters = { category: '', type: '' };
  const refresh = async () => {
    try {
      const page = await api.listings({ ...filters, size: 30 });
      clear(list, page.content.length
        ? page.content.map((l) => listingCard(l, refresh))
        : h('p', { class: 'empty' }, 'Aucune annonce pour ces critères… pour l’instant.'));
    } catch (e) {
      clear(list, errorBox(e));
    }
  };
  const category = select('category', [['', 'Toutes les catégories'], ...CATEGORIES], '');
  category.addEventListener('change', () => { filters.category = category.value; refresh(); });
  clear(main,
    pageHeader('Espace communautaire', 'Troc et dons',
      'Donnez une seconde vie à vos goodies en double. Les membres intéressés par la catégorie ou un mot-clé sont prévenus.'),
    user() ? publishForm(refresh) : loginPrompt('Connectez-vous pour proposer un objet à échanger ou à donner.'),
    h('section', { 'aria-labelledby': 'liste-annonces' },
      h('div', { class: 'section-head' },
        h('h2', { id: 'liste-annonces' }, 'Ça s’échange en ce moment'),
        h('div', { class: 'pills' },
          pills([['', 'Tout'], ['EXCHANGE', 'Échanges'], ['GIVEAWAY', 'Dons']], '', (v) => { filters.type = v; refresh(); }, 'Type d’annonce'),
          field('Catégorie', category))),
      list));
  await refresh();
}

// ---------- Alertes ----------

export async function notificationsView(main) {
  if (!user()) {
    clear(main, pageHeader('Mes alertes', 'Ne ratez plus une pièce rare'), loginPrompt('Connectez-vous pour créer vos alertes.'));
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
        toast('Centres d’intérêt enregistrés.', 'success');
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
    : h('p', { class: 'empty' }, 'Aucune alerte pour le moment. Le calme avant la tempête…');
  clear(main,
    pageHeader('Mes alertes', 'Ne ratez plus une pièce rare',
      'Choisissez vos catégories et mots-clés : une alerte arrive dès qu’une annonce correspond.'),
    h('div', { class: 'two-cols' }, form,
      h('section', { class: 'panel', 'aria-labelledby': 'mes-alertes' },
        h('h2', { id: 'mes-alertes' }, `Alertes reçues (${notifs.totalElements})`), list)));
}

// ---------- Boutique ----------

function productCard(product, onAdded) {
  const badge = product.exclusive ? h('span', { class: 'tag tag-excl' }, 'Exclusivité Evil Ed') : null;
  let buy;
  if (!product.available) buy = h('p', { class: 'meta' }, 'Épuisé, revenez vite');
  else if (!user()) buy = h('button', { type: 'button', class: 'secondary', onclick: () => login() }, 'Se connecter pour acheter');
  else {
    const qty = h('input', { type: 'number', min: 1, max: 10, value: 1 });
    buy = h('div', { class: 'buy' }, field('Quantité', qty), actionButton('Ajouter', async () => {
      await api.addToCart(product.id, Number(qty.value));
      toast(`${product.name} ajouté au panier.`, 'success');
      await onAdded();
    }, { class: 'light', 'aria-label': `Ajouter ${product.name} au panier` }));
  }
  return h('article', { class: 'card product' },
    visuel(product.category, badge),
    h('div', { class: 'card-body' },
      h('p', { class: 'rayon' }, categoryLabel(product.category)),
      h('h3', {}, product.name),
      h('p', { class: 'meta' }, !product.available ? 'Rupture de stock' : product.stock <= 50 ? `Plus que ${product.stock} exemplaires` : 'En stock'),
      h('p', { class: 'price' }, fmt.price(product.priceCents)),
      buy));
}

export async function boutiqueView(main) {
  const grid = h('div', { class: 'grid' });
  const cartBox = h('section', { class: 'panel cart', 'aria-labelledby': 'panier' });
  const ordersBox = h('section', { class: 'panel', 'aria-labelledby': 'commandes' });
  let rayon = '';

  const loadOrders = async () => {
    const orders = await api.orders();
    clear(ordersBox, h('h2', { id: 'commandes' }, 'Mes commandes'),
      orders.content.length ? h('ul', { class: 'orders' }, orders.content.map((o) => h('li', {},
        h('p', {}, h('strong', {}, fmt.price(o.totalCents)), ` · ${o.status === 'CONFIRMED' ? 'Confirmée' : o.status}`),
        h('p', { class: 'meta' }, `${fmt.dateTime(o.createdAt)} · ${o.lines.map((l) => `${l.quantity} × ${l.productName}`).join(', ')}`))))
        : h('p', { class: 'empty' }, 'Aucune commande pour l’instant.'));
  };
  const loadCart = async () => {
    if (!user()) return;
    const cart = await api.cart();
    clear(cartBox, h('h2', { id: 'panier' }, 'Mon panier'),
      cart.lines.length
        ? [h('ul', { class: 'cart-lines' }, cart.lines.map((l) => h('li', {},
          h('span', {}, `${l.quantity} × ${l.name}`), h('strong', {}, fmt.price(l.lineTotalCents)),
          actionButton('Retirer', async () => { await api.removeFromCart(l.productId); await loadCart(); },
            { class: 'link', 'aria-label': `Retirer ${l.name} du panier` })))),
        h('p', { class: 'total' }, h('span', { class: 'meta' }, 'Total'), h('strong', {}, fmt.price(cart.totalCents))),
        actionButton('Commander', async () => {
          const order = await api.checkout();
          toast(`Commande confirmée : ${fmt.price(order.totalCents)} (paiement simulé).`, 'success');
          await Promise.all([loadCart(), loadProducts(), loadOrders()]);
        }, { class: 'primary' })]
        : h('p', { class: 'empty' }, 'Votre panier attend sa première pièce.'),
      h('div', { class: 'reassurance' },
        h('p', {}, 'Livraison soignée depuis nos boutiques'),
        h('p', {}, 'Paiement sécurisé par un prestataire certifié'),
        h('p', {}, 'Retrait possible en boutique et au festival')));
  };
  const loadProducts = async () => {
    try {
      const page = await api.products(rayon);
      clear(grid, page.content.map((p) => productCard(p, loadCart)));
    } catch (e) {
      clear(grid, errorBox(e));
    }
  };
  const rayons = [['', 'Tout'], ['FIGURINE', 'Figurines'], ['BLURAY', 'Blu-ray'], ['BD', 'BD'], ['JEU', 'Jeux'], ['FANZINE', 'Fanzine'], ['GOODIES', 'Goodies']];

  clear(main,
    h('div', { class: 'section-head page-header' },
      h('div', {}, h('p', { class: 'eyebrow' }, 'La boutique'), h('h1', {}, 'Trésors de l’Épouvante')),
      pills(rayons, '', (v) => { rayon = v; loadProducts(); }, 'Rayon')),
    h('div', { class: 'shop-layout' },
      h('section', { 'aria-label': 'Catalogue' }, grid),
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
  const cards = PLANS.map((plan) => h('article', { class: `card plan${plan.featured ? ' featured' : ''}` },
    h('p', { class: 'tagline' }, plan.tag),
    h('h3', {}, plan.name),
    h('p', { class: 'price' }, fmt.price(plan.price), h('span', { class: 'per' }, ' / an')),
    h('p', {}, plan.text),
    user()
      ? actionButton(current?.status === 'ACTIVE' ? 'Renouveler (+1 an)' : 'Je m’abonne', async () => {
        current = await api.subscribe(plan.code);
        toast(`Abonnement ${plan.name} actif jusqu’au ${fmt.date(current.endDate)}.`, 'success');
        renderStatus();
      }, { class: plan.featured ? 'primary' : 'secondary' })
      : h('button', { type: 'button', class: 'secondary', onclick: () => login() }, 'Se connecter pour s’abonner')));
  renderStatus();
  clear(main,
    pageHeader('Le fanzine', 'Quatre frissons par an',
      'Horreur, fantastique et heroic fantasy : critiques, entretiens, pépites oubliées et coulisses du festival. Un renouvellement prolonge d’un an.'),
    h('div', { class: 'plans' }, cards),
    status);
}

// ---------- Accueil ----------

function heroCard(product, onAdded) {
  if (!product) return null;
  const card = productCard(product, onAdded);
  card.querySelector('.rayon').textContent = 'Édition limitée · Collectif Evil Ed';
  return h('div', { class: 'hero-card' }, card);
}

export async function homeView(main) {
  const latest = h('div', { class: 'grid' });
  const heroSlot = h('div', { class: 'hero-card' });
  const porte = (href, ic, title, text, go) => h('a', { class: 'card porte', href },
    icon(ic), h('h3', {}, title), h('p', { class: 'meta' }, text), h('span', { class: 'go' }, go));

  const hero = h('section', { class: 'hero', 'aria-labelledby': 'accroche' },
    h('div', { class: 'hero-text' },
      h('p', { class: 'eyebrow' }, 'Le lieu de rêve pour frissonner'),
      h('h1', { id: 'accroche' }, 'Le repaire des passionnés ', h('span', { class: 'accent' }, 'd’horreur')),
      h('p', { class: 'lead' }, 'Échangez vos trouvailles, chassez les éditions limitées du collectif Evil Ed et recevez chaque saison le fanzine horreur, fantastique et heroic fantasy.'),
      h('div', { class: 'hero-actions' },
        h('a', { class: 'button primary', href: '#/boutique' }, 'Découvrir les exclusivités'),
        h('a', { class: 'button secondary', href: '#/troc' }, 'Voir les annonces de troc')),
      h('div', { class: 'stats' },
        h('p', {}, h('strong', {}, '4'), h('span', {}, 'numéros du fanzine par an')),
        h('p', {}, h('strong', {}, '4'), h('span', {}, 'boutiques : Angoulême, Aix, Lyon, Londres')),
        h('p', {}, h('strong', {}, '1'), h('span', {}, 'Petit Festival chaque année')))),
    heroSlot);

  clear(main,
    hero,
    h('div', { class: 'bandeau', 'aria-hidden': 'true' },
      h('ul', {}, ['Figurines', 'BD', 'Blu-ray restaurés', 'Jeux de société', 'Fanzine', 'Goodies'].map((t) => h('li', {}, t)))),
    h('section', { 'aria-labelledby': 'portes', style: null },
      h('p', { class: 'eyebrow', style: null }, 'Trois façons d’entrer'),
      h('h2', { id: 'portes' }, 'Poussez la porte'),
      h('div', { class: 'portes' },
        porte('#/troc', 'swap', 'Troc et dons', 'Proposez vos goodies en double, donnez une seconde vie à vos VHS. Vous êtes prévenu dès qu’un objet qui vous intéresse apparaît.', 'Parcourir les annonces →'),
        porte('#/boutique', 'bag', 'La boutique', 'Figurines exclusives Evil Ed, BD, Blu-ray restaurés et jeux : la sélection de nos boutiques, livrée chez vous.', 'Entrer dans la boutique →'),
        porte('#/abonnement', 'book', 'Le fanzine', 'Quatre numéros par an, sur papier, en ligne ou les deux. Critiques, interviews et coulisses du festival.', 'Choisir ma formule →'))),
    h('section', { 'aria-labelledby': 'dernieres' },
      h('p', { class: 'eyebrow' }, 'Espace communautaire'),
      h('h2', { id: 'dernieres' }, 'Ça s’échange en ce moment'),
      latest,
      h('div', { class: 'panel cta-band' },
        h('div', { class: 'who-line' }, icon('bell'),
          h('div', {}, h('p', {}, h('strong', {}, 'Ne ratez plus une pièce rare')),
            h('p', { class: 'meta' }, 'Choisissez vos catégories et mots-clés : une alerte arrive dès qu’une annonce correspond.'))),
        h('a', { class: 'button primary', href: '#/notifications' }, 'Créer mes alertes'))),
    h('section', { class: 'festival', 'aria-labelledby': 'festival' },
      h('div', {},
        h('p', { class: 'eyebrow' }, 'Festival annuel'),
        h('h2', { id: 'festival' }, 'Le Petit Festival de l’Épouvante revient'),
        h('p', { class: 'lead' }, 'Projections, masterclasses, tournois de jeux de plateau et éditions vendues uniquement sur place.')),
      h('a', { class: 'button blood', href: '#/notifications' }, 'Être prévenu')));

  const refresh = async () => {
    const page = await api.listings({ size: 3 });
    clear(latest, page.content.length ? page.content.map((l) => listingCard(l, refresh)) : h('p', { class: 'empty' }, 'Aucune annonce pour l’instant.'));
  };
  await Promise.all([
    refresh().catch((e) => clear(latest, errorBox(e))),
    api.products().then((page) => {
      const card = heroCard(page.content.find((p) => p.exclusive && p.available), async () => {});
      if (card) heroSlot.replaceWith(card);
    }).catch(() => heroSlot.remove()),
  ]);
}
