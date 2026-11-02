// Appels aux API REST (même domaine). Les erreurs RFC 9457 (application/problem+json) sont traduites en messages.
import { accessToken } from './auth.js';

const MESSAGES = {
  400: 'Les informations saisies ne sont pas valides.',
  401: 'Connectez-vous pour continuer.',
  403: 'Vous n’avez pas les droits pour cette action.',
  404: 'Élément introuvable.',
  409: 'Action impossible pour le moment (stock ou conflit).',
  429: 'Trop de requêtes, réessayez dans un instant.',
};

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, body) {
  const headers = { Accept: 'application/json' };
  const token = await accessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Le service est injoignable. Vérifiez votre connexion.');
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const detail = data?.detail || data?.title;
    throw new ApiError(res.status, detail && res.status !== 500 ? detail : MESSAGES[res.status] || 'Une erreur est survenue.');
  }
  return data;
}

const query = (params) => {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v); });
  const s = q.toString();
  return s ? `?${s}` : '';
};

export const api = {
  listings: (filters) => request('GET', `/api/community/listings${query(filters)}`),
  publishListing: (listing) => request('POST', '/api/community/listings', listing),
  withdrawListing: (id) => request('DELETE', `/api/community/listings/${encodeURIComponent(id)}`),
  moderateListing: (id, reason) => request('POST', `/api/community/listings/${encodeURIComponent(id)}/moderation`, { reason }),

  preferences: () => request('GET', '/api/notifications/preferences/me'),
  savePreferences: (prefs) => request('PUT', '/api/notifications/preferences/me', prefs),
  notifications: () => request('GET', '/api/notifications/me?size=20'),

  products: (category) => request('GET', `/api/shop/products${query({ category, size: 50 })}`),
  cart: () => request('GET', '/api/shop/cart'),
  addToCart: (productId, quantity) => request('POST', '/api/shop/cart/items', { productId, quantity }),
  removeFromCart: (productId) => request('DELETE', `/api/shop/cart/items/${encodeURIComponent(productId)}`),
  checkout: () => request('POST', '/api/shop/orders'),
  orders: () => request('GET', '/api/shop/orders/me?size=10'),

  subscription: () => request('GET', '/api/shop/subscriptions/me'),
  subscribe: (plan) => request('POST', '/api/shop/subscriptions', { plan }),
};
