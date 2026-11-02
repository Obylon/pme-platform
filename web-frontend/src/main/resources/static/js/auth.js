// Connexion OpenID Connect auprès de Keycloak : flux Authorization Code + PKCE (S256), client public pme-web.
// Keycloak et les API sont servis par la même Gateway que ce site : aucun appel inter-domaines (pas de CORS).
// Les jetons restent dans la session de l'onglet (sessionStorage) ; en production, un « backend for frontend »
// avec cookie HttpOnly réduirait encore l'exposition en cas de XSS (plan de remédiation).

const REALM_URL = `${location.origin}/auth/realms/pme/protocol/openid-connect`;
const CLIENT_ID = 'pme-web';
const REDIRECT_URI = `${location.origin}/`;
const STORE_KEY = 'pme.tokens';
const PKCE_KEY = 'pme.pkce';

let tokens = readStore();
const listeners = new Set();

function readStore() {
  try {
    return JSON.parse(sessionStorage.getItem(STORE_KEY)) || null;
  } catch {
    return null;
  }
}

function save(next) {
  tokens = next;
  try {
    if (next) sessionStorage.setItem(STORE_KEY, JSON.stringify(next));
    else sessionStorage.removeItem(STORE_KEY);
  } catch {
    // stockage indisponible (navigation privée stricte) : la session reste en mémoire
  }
  listeners.forEach((fn) => fn(user()));
}

function base64Url(bytes) {
  let binary = '';
  bytes.forEach((b) => { binary += String.fromCodePoint(b); });
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

function randomString(size) {
  return base64Url(crypto.getRandomValues(new Uint8Array(size)));
}

function decodePayload(jwt) {
  const part = jwt.split('.')[1].replaceAll('-', '+').replaceAll('_', '/');
  const json = decodeURIComponent(Array.from(atob(part), (c) => `%${c.codePointAt(0).toString(16).padStart(2, '0')}`).join(''));
  return JSON.parse(json);
}

function withExpiry(response) {
  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token,
    idToken: response.id_token,
    expiresAt: Date.now() + response.expires_in * 1000,
  };
}

async function tokenRequest(params) {
  const res = await fetch(`${REALM_URL}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: CLIENT_ID, ...params }),
  });
  if (!res.ok) throw new Error('Connexion refusée par le serveur d’identité.');
  return withExpiry(await res.json());
}

/** Redirige vers la page de connexion Keycloak. */
export async function login(returnTo = location.hash || '#/') {
  const verifier = randomString(48);
  const state = randomString(16);
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  sessionStorage.setItem(PKCE_KEY, JSON.stringify({ verifier, state, returnTo }));
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: 'code',
    scope: 'openid',
    redirect_uri: REDIRECT_URI,
    code_challenge: base64Url(new Uint8Array(digest)),
    code_challenge_method: 'S256',
    state,
  });
  location.assign(`${REALM_URL}/auth?${params}`);
}

/** Termine la connexion au retour de Keycloak (code dans l'URL). Renvoie la page à afficher ensuite. */
export async function handleRedirect() {
  const query = new URLSearchParams(location.search);
  const code = query.get('code');
  if (!code && !query.get('error')) return null;
  const pending = JSON.parse(sessionStorage.getItem(PKCE_KEY) || 'null');
  sessionStorage.removeItem(PKCE_KEY);
  history.replaceState(null, '', `${location.pathname}${pending?.returnTo || '#/'}`);
  if (!code || !pending || pending.state !== query.get('state')) {
    throw new Error('La connexion n’a pas abouti, veuillez réessayer.');
  }
  save(await tokenRequest({
    grant_type: 'authorization_code',
    code,
    redirect_uri: REDIRECT_URI,
    code_verifier: pending.verifier,
  }));
  return pending.returnTo;
}

/** Jeton d'accès valide (renouvelé 30 s avant expiration), ou null si personne n'est connecté. */
export async function accessToken() {
  if (!tokens) return null;
  if (Date.now() < tokens.expiresAt - 30_000) return tokens.accessToken;
  try {
    save(await tokenRequest({ grant_type: 'refresh_token', refresh_token: tokens.refreshToken }));
    return tokens.accessToken;
  } catch {
    save(null);
    return null;
  }
}

/** Utilisateur connecté (identifiant, nom, rôles), ou null. */
export function user() {
  if (!tokens) return null;
  const claims = decodePayload(tokens.accessToken);
  return {
    id: claims.sub,
    name: claims.preferred_username || claims.name || 'membre',
    roles: claims.realm_access?.roles || [],
  };
}

export function hasRole(role) {
  return user()?.roles.includes(role) ?? false;
}

export function onChange(fn) {
  listeners.add(fn);
}

/** Déconnexion locale puis fin de session chez Keycloak. */
export function logout() {
  const idToken = tokens?.idToken;
  save(null);
  const params = new URLSearchParams({ client_id: CLIENT_ID, post_logout_redirect_uri: REDIRECT_URI });
  if (idToken) params.set('id_token_hint', idToken);
  location.assign(`${REALM_URL}/logout?${params}`);
}
