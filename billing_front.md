# Billing Frontend — Workflow de test des paiements (côté UI)

Ce document décrit comment tester, **depuis l'interface**, tout ce qui touche au paiement : connexion acheteur, achat d'une œuvre, réservation d'un événement payant, portefeuille artiste.

> À lire d'abord : [billing.md](billing.md) décrit le flux backend → kernel. Ce document-ci se concentre sur le parcours **utilisateur dans le navigateur** et sur **ce qui est réellement câblé** aujourd'hui.

---

## 0. État réel du câblage frontend — à lire avant tout

Tous les parcours de paiement ne sont pas branchés au backend. Voici la vérité terrain (vérifiée dans le code) :

| Parcours | Déclenche un vrai paiement ? | Détail |
|---|---|---|
| **Événement payant** (réservation) | ✅ **Oui** | `TicketReservation` → `reserveTicket()` → `checkoutReservation()` → backend → kernel |
| **Achat d'une œuvre** (shop) | ❌ **Non** | `/checkout` crée la commande (`placeOrder`) mais **n'appelle jamais** `checkoutOrder`. La commande reste impayée. |
| **Page `/shop/checkout`** | ❌ Non | Maquette : bouton = simple `<Link>` vers `/shop/confirmation`. Aucun appel réseau, totaux codés en dur. |
| **Upgrade d'abonnement** (artiste) | ❌ Non | `SubscriptionsService.checkoutUpgrade` existe mais **n'est appelé nulle part** dans l'UI. |
| **Retrait / payout** (artiste) | ⚠️ Appelle le backend, mais **refusé** | `requestPayout` → `/api/wallet/withdraw` → le backend renvoie une erreur (le kernel n'expose pas de décaissement). |

**Conséquence :** le seul parcours testable de bout en bout par l'UI aujourd'hui est **la réservation d'un événement payant** (§4). Les autres se testent partiellement (création de commande, lecture du portefeuille) ou nécessitent un câblage (§7).

---

## 1. Prérequis

### 1.1 Aligner frontend et backend sur la même base — **piège critique**

Le frontend choisit son backend ainsi ([apiConfig.ts](yowpainter-frontend/src/lib/apiConfig.ts)) :

```
NEXT_PUBLIC_API_URL  (si défini)
  sinon si NODE_ENV=development  → http://localhost:8090
  sinon                         → https://yowpainter-backend-lpbz.onrender.com
```

⚠️ Le backend **local** (`.env.local`) et le backend **déployé** pointent sur **deux bases différentes**. Si tu testes le login/checkout sur un backend et que tu regardes le résultat (admin, statut, wallet) sur l'autre, les données ne coïncideront pas.

**Règle : un seul backend pour toute la session de test.**

- Test 100 % local : lance le frontend en dev (`npm run dev` → `NODE_ENV=development` → `localhost:8090`) **et** le backend local sur `:8090`.
- Test contre le déployé : utilise le frontend déployé (ou `NEXT_PUBLIC_API_URL=https://yowpainter-backend-lpbz.onrender.com`).

### 1.2 Le backend doit joindre le kernel — et le kernel doit joindre le backend

- Le backend a besoin d'une config kernel valide (`KSM_KERNEL_*`, cf. [billing.md §3](billing.md)).
- Le kernel renvoie la notification de paiement sur `BACKEND_URL/api/payment/callback`. En local, `localhost:8090` est **injoignable** depuis le kernel → le callback n'arrivera pas, et c'est le **polling de rattrapage** (toutes les 10 min) qui confirmera le paiement. Pour un callback immédiat, expose le backend via un tunnel (`ngrok http 8090`) et mets l'URL publique dans `BACKEND_URL`.

### 1.3 Lancer le frontend

```bash
cd yowpainter-frontend
npm install       # première fois
npm run dev       # http://localhost:3000
```

---

## 2. Créer les comptes de test

### 2.1 Compte acheteur (COLLECTOR)

1. Aller sur `http://localhost:3000/register`.
2. Laisser l'onglet **« Collector »** sélectionné (rôle `ROLE_BUYER`).
3. Renseigner prénom, nom, email, mot de passe → **S'inscrire**.
4. Vérifier l'email si le kernel l'exige (lien de vérification). Un acheteur n'a pas besoin d'organisation : il est utilisable directement après vérification email.

### 2.2 Compte artiste (vendeur) — nécessaire pour avoir une œuvre / un événement à payer

1. `/register` → onglet **« Artist »** → renseigner le nom d'artiste → s'inscrire.
2. Vérifier l'email.
3. **Un admin doit créer l'organisation de l'artiste** (statut `ORGANIZATION_VALIDATION_REQUIRED` → validation admin) avant que l'artiste puisse vendre / encaisser. Cf. `docs/ARTIST_APPROVAL_WORKFLOW.md`.
   > Rappel : la transition depuis `PENDING_EMAIL` se déclenche au **login de l'artiste**. Après avoir validé son email, l'artiste doit se **reconnecter** pour apparaître dans la liste admin des artistes en attente.
4. Une fois l'artiste `ACTIVE` : il publie une **œuvre** (dashboard artiste) et/ou crée un **événement payant** (`eventType = PAID`, avec un prix de billet).

---

## 3. Se connecter en tant qu'acheteur

1. `http://localhost:3000/login`.
2. Email + mot de passe de l'acheteur → **Se connecter**.
3. Le store d'auth (`useAuthStore`) stocke `accessToken` et redirige selon le rôle (`getDashboardRoute`). Un acheteur reste sur l'espace public.
4. Vérifier que le token est bien injecté : ouvrir les DevTools → onglet **Network** → une requête authentifiée doit porter `Authorization: Bearer eyJ...`.

---

## 4. ✅ Parcours principal testable : réserver un événement payant

C'est **le seul flux de paiement complet** côté UI.

### Étapes

1. Connecté en acheteur, aller sur la liste des événements : `/events` (ou l'espace d'un artiste `/{slug}/events`).
2. Ouvrir un événement dont le **type est `PAID`** (prix de billet > 0). Route : `/events/{id}` ou `/{slug}/events/{id}`.
3. Le composant **`TicketReservation`** s'affiche. Pour un événement payant, un champ **numéro de téléphone** (Mobile Money) est requis.
4. Renseigner nom, email, **téléphone** (ex. `690000001`) → **Réserver mon billet**.

### Ce qui se passe (vérifié dans [TicketReservation.tsx](yowpainter-frontend/src/components/events/TicketReservation.tsx) + [events.ts](yowpainter-frontend/src/lib/api/events.ts))

```
1. clic « Réserver »  → ouverture SYNCHRONE d'une fenêtre de paiement (anti-popup-blocker)
2. reserveTicket(eventId, { phoneNumber })
     → EventsService.reserveEvent(eventId)                     // crée la réservation
     → EventsService.checkoutReservation(reservationId, phone)  // POST /api/events/reservations/{id}/checkout
          → backend PaymentService.initiatePayment(...)
          → POST /api/payments/orders (kernel)                  // renvoie status=PENDING + redirectUrl
3. la fenêtre de paiement charge la redirectUrl MyCoolPay → le payeur confirme (USSD)
4. la fenêtre principale POLL  GET /api/payment/status/{reservationId}  toutes les 3 s
5. dès status=SUCCEEDED → fermeture de la fenêtre + redirection vers /tickets/{reservationId}
```

- Le téléphone est normalisé : 9 chiffres → préfixé `237` automatiquement.
- **La confirmation crédite le portefeuille de l'artiste** (via `processSuccessfulPayment`, déclenché par le polling).
- Au retour du PSP, la fenêtre atterrit sur **`/payment/return`** (page frontend) : en mode fenêtre, elle se ferme seule ; en repli même-onglet, elle vérifie le statut puis redirige vers le billet.

### À vérifier

- **Network** : `POST /api/events/reservations/{id}/checkout?phoneNumber=237...` renvoie `{ paymentReference, paymentId, status, redirectUrl }`.
- **Network (polling)** : des `GET /api/payment/status/{reservationId}` répétés, qui passent de `PENDING` à `SUCCEEDED`.
- **Portefeuille artiste** : le solde augmente une fois `SUCCEEDED`.
- **Événement gratuit** (`ticketPrice = 0`) : pas de téléphone, `reserveEvent` seul, billet immédiat — cas de contrôle.

### Conditions pour que le PSP accepte (sinon `FAILED` immédiat, sans redirectUrl)

- **Montant ≥ ~100 FCFA** (prix du billet).
- **Numéro Mobile Money valide** : `237` + préfixe camerounais (MTN `67`/`650-654`/`680-684`, Orange `69`/`655-659`/`685-689`).
- Provider `MYCOOLPAY` / méthode `MOBILE_MONEY` (Stripe/carte non opérationnel sur l'instance).

Si le PSP refuse, l'UI affiche : *« Le paiement n'a pas pu être initié. Vérifiez votre numéro Mobile Money et un montant d'au moins 100 FCFA. »*

---

## 5. ⚠️ Parcours achat d'œuvre — commande sans paiement (état actuel)

Utile pour tester la **création de commande**, mais **le paiement n'est pas déclenché**.

### Étapes

1. Connecté en acheteur, aller sur `/shop` (catalogue global) ou l'espace d'un artiste.
2. Sur une œuvre en vente (`ShopArticleCard`) → **Ajouter au panier** (`addItem` : `id`, `price`, `artistId`).
3. Aller au panier `/cart` → **passer à la caisse** → `/checkout`.
4. Renseigner **adresse** et **téléphone** → **Valider la commande**.

### Ce qui se passe réellement ([checkout/page.tsx](yowpainter-frontend/src/app/[locale]/(public)/checkout/page.tsx))

```
handlePlaceOrder()
  → pour chaque article : ShopOrdersService.placeOrder(artistId, { productId, quantity, shippingAddress })
  → setCompleted(true) ; clearCart()      // ⛔ AUCUN appel de paiement
```

- Le téléphone est **saisi et validé mais jamais envoyé**.
- `checkoutOrder()` (qui déclencherait le paiement) **n'est appelé nulle part**.
- Une commande par article est créée (`placeOrder` par item du panier).

### À vérifier

- **Network** : uniquement des `POST /api/shop/v1/public/{artistId}/orders`. **Aucun** `/checkout`.
- La commande apparaît dans `/shop/purchases` (ou dashboard) au statut **impayé** (`PENDING_PAYMENT`), pas `PAID`.

### ⚠️ Ne pas utiliser `/shop/checkout`

C'est une **maquette** ([shop/checkout/page.tsx](yowpainter-frontend/src/app/[locale]/(public)/shop/checkout/page.tsx)) : bouton « Confirmer le paiement » = `<Link>` vers `/shop/confirmation`, totaux codés en dur. Elle laisse croire à un paiement qui n'existe pas.

---

## 6. Côté artiste : portefeuille et retrait

Connecté en **artiste**, aller au dashboard `/artdashboard` → onglet **Portefeuille** (`WalletTab`).

| Action | Endpoint | État |
|---|---|---|
| Consulter le solde | `GET /api/wallet/balance` | ✅ Fonctionne |
| Historique transactions | `GET /api/wallet/transactions` | ✅ Fonctionne |
| Configurer le retrait | `POST /api/wallet/settings/payout` | ✅ Fonctionne (enregistre tel + réseau) |
| Demander un retrait | `POST /api/wallet/withdraw` | ⚠️ **Refusé** : le kernel n'expose pas de décaissement → message d'erreur attendu |

- Le solde artiste se crédite **uniquement** quand un paiement est confirmé (`SUCCEEDED`) : commande payée ou billet payé → `WalletService.creditWallet` (montant net après commission).
- Les infos blockchain (fingerprint, hash de transaction) s'affichent si l'œuvre/billet a été ancré après confirmation.
- **Retrait** : la demande atteint le backend mais renvoie « Le retrait vers Mobile Money n'est pas encore disponible ». C'est le comportement voulu.

---

## 7. Câblages manquants (si tu veux tester les autres parcours)

Ces parcours nécessitent une petite modification frontend (non appliquée) :

### 7.1 Paiement d'une œuvre (shop)

Dans `handlePlaceOrder()` ([checkout/page.tsx](yowpainter-frontend/src/app/[locale]/(public)/checkout/page.tsx)), après `placeOrder`, appeler le checkout :

```ts
const res = await ShopOrdersService.placeOrder(item.artistId, { ... });
if (!res.id) continue;
const payment = await ShopOrdersService.checkoutOrder(res.id, phoneNumber);
if (payment.redirectUrl) { window.location.href = payment.redirectUrl; return; }
```

> Attention : `placeOrder` crée **une commande par article** → donc un encaissement par article. À regrouper si un panier multi-articles doit donner un seul paiement.

### 7.2 Upgrade d'abonnement (artiste)

`SubscriptionsService.checkoutUpgrade(plan, phoneNumber)` existe mais n'est branché sur aucun bouton. Il faut l'appeler depuis la page de gestion d'abonnement du dashboard artiste.

> Ces modifications changent le comportement produit (l'utilisateur sera réellement débité) : à valider avant de les appliquer.

---

## 8. Vérifier les résultats d'un test

| Où | Quoi vérifier |
|---|---|
| **DevTools → Network** | La bonne requête part (`/checkout` pour événement, `/orders` pour shop), et la réponse (`paymentReference`, `status`, `redirectUrl`). |
| **DevTools → Console** | Erreurs éventuelles (checkout événement logge `Erreur lors du checkout` en cas d'échec). |
| **`/shop/purchases`** | Statut de la commande (`PENDING_PAYMENT` vs `PAID`). |
| **`/tickets/{id}`** | Le billet réservé (données en `localStorage`). |
| **Dashboard artiste → Portefeuille** | Le solde augmente après confirmation d'un paiement. |
| **Notifications** | Une notification interne est créée à la confirmation (commande payée / réservation confirmée). |
| **Logs backend** | `>>> KERNEL HTTP REQUEST START >>>` vers `/api/payments/orders`, puis le refresh de vérification. |

---

## 9. Dépannage

| Symptôme | Cause probable |
|---|---|
| L'admin ne voit pas l'artiste / statut incohérent | Frontend et backend sur **deux bases différentes** (§1.1), ou artiste pas reconnecté après vérif email |
| Le paiement d'une œuvre ne part jamais | **Normal** : le shop n'appelle pas `checkoutOrder` (§5). Utiliser l'événement payant, ou câbler §7.1 |
| Réservation payante : « Erreur lors du checkout » | Le téléphone est requis pour un événement `PAID` ; vérifier la config kernel du backend |
| Après paiement, on n'atterrit pas sur le billet | La fenêtre principale doit poller `GET /api/payment/status/{ref}`. Vérifier que la popup n'est pas bloquée (elle s'ouvre au clic) et que ce GET renvoie `SUCCEEDED` |
| Après paiement, on atterrit sur une page JSON | `callbackUrl` doit pointer sur `FRONTEND_URL/payment/return` (corrigé côté backend) — vérifier `FRONTEND_URL` du backend |
| Solde artiste inchangé après paiement réussi | Le crédit se fait à la confirmation (polling → `SUCCEEDED`). Vérifier que le polling aboutit et que l'artiste est `ACTIVE` avec `organizationId` |
| Popup de paiement bloquée | Autoriser les popups pour le site ; sinon le repli même-onglet prend le relais (retour via `/payment/return`) |
| Paiement `FAILED` immédiat (pas de fenêtre de paiement) | Montant < ~100 FCFA ou numéro Mobile Money invalide (§4) |
| Retrait refusé | **Attendu** : pas de décaissement kernel (§6) |
| Requêtes non authentifiées (401) | Token absent/expiré : se reconnecter ; vérifier le header `Authorization` dans Network |

---

## Références

- [billing.md](billing.md) — workflow backend → kernel et test par API
- [docs/KERNEL_INTEGRATION.md](docs/KERNEL_INTEGRATION.md) — intégration kernel
- `docs/ARTIST_APPROVAL_WORKFLOW.md` — validation de l'organisation artiste
- Frontend : `TicketReservation.tsx`, `lib/api/events.ts`, `(public)/checkout/page.tsx`, `components/artdashboard/WalletTab.tsx`
