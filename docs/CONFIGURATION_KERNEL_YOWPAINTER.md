# Configuration YowPainter ↔ Kernel RT-Comops

Guide pratique des réglages nécessaires **dans le kernel** et **dans le backend YowPainter** pour que les deux applications communiquent en local (ou en environnement déployé).

> Voir aussi : [KERNEL_INTEGRATION.md](./KERNEL_INTEGRATION.md) (architecture et flux métier).

---

## 1. Vue d'ensemble

```
┌─────────────┐         JWT (login)          ┌──────────────────┐
│  Frontend   │ ───────────────────────────► │ YowPainter :8090 │
│  :3000      │   (jamais de X-Api-Key)      │  (consommateur)  │
└─────────────┘                              └────────┬─────────┘
                                                      │
                           X-Client-Id + X-Api-Key   │
                           X-Tenant-Id + Bearer JWT  │
                                                      ▼
                                             ┌──────────────────┐
                                             │  Kernel :8080    │
                                             │  RT-Comops       │
                                             └──────────────────┘
```

| Composant | Port local | Rôle |
|-----------|------------|------|
| Kernel RT-Comops | **8080** | Auth, org, commerce, fichiers, notifications |
| YowPainter backend | **8090** | API métier + proxy vers le kernel |
| Frontend | **3000** | Appelle **uniquement** YowPainter (`8090`) |

Le frontend **ne doit jamais** appeler le kernel avec `X-Api-Key`. Seul le backend YowPainter est une `ClientApplication` du kernel.

---

## 2. Configuration côté kernel

### 2.1 Démarrer le kernel

1. Démarrer le kernel RT-Comops (projet `KSM_Kernel_Layer` ou stack Docker).
2. Vérifier : `http://localhost:8080` répond.
3. JWKS JWT : `http://localhost:8080/.well-known/jwks.json`

### 2.2 Tenant de développement

En local, le tenant par défaut est :

```text
11111111-1111-1111-1111-111111111111
```

Tous les appels backend → kernel doivent inclure ce UUID dans `X-Tenant-Id`.

### 2.3 Compte bootstrap `platform-admin`

Certaines opérations YowPainter (inscription artiste, inscription admin, provision des rôles) utilisent un compte admin kernel en arrière-plan.

| Paramètre | Valeur locale typique |
|-----------|----------------------|
| Username | `platform-admin` |
| Password | `PlatformAdmin!123` |
| Rôle kernel | `SYSTEM_ADMIN` (bootstrap au démarrage du kernel) |

**Prérequis MFA (kernel)** — les endpoints admin (`/api/client-applications`, `/api/administration/*`) exigent :

1. Login MFA : `POST /api/auth/login` → `POST /api/auth/login/mfa/confirm` (code `codePreview` en dev).
2. Colonne `mfa_enabled = true` en base sur `platform-admin` (sinon `403 MFA_REQUIRED_FOR_ADMIN`).

Activation rapide en local (PostgreSQL Docker kernel) :

```bash
docker exec iwm-postgres psql -U iwm -d iwm -c \
  "UPDATE auth_core.user_account SET mfa_enabled = true, mfa_channel = 'EMAIL' WHERE username = 'platform-admin';"
```

### 2.4 ClientApplication YowPainter

Le backend YowPainter doit être enregistré comme **ClientApplication** dans le kernel.

#### Option A — Client dédié (recommandé)

Exécuter une fois (kernel démarré, `platform-admin` avec MFA) :

```powershell
.\scripts\bootstrap-kernel-client.ps1
```

Ce script crée :

| Champ kernel | Valeur |
|--------------|--------|
| `clientId` | `yowpainter-backend` |
| `clientSecret` | `yowpainter-local-dev-secret-2026` |
| `allowedServices` | `ORGANIZATION`, `SETTINGS`, `COMMERCIAL`, `PRODUCT`, `SALES`, `NOTIFICATION`, `ADMINISTRATION` |

> **Note** : `/api/files` (upload photos) n'est pas mappé à un service quota spécifique — il passe en service `CORE` côté kernel. Aucun service `FILE` à ajouter dans `allowedServices`.

#### Option B — Dev rapide sans bootstrap

Utiliser le client bootstrap kernel déjà présent :

```bash
KSM_KERNEL_CLIENT_ID=dev-platform-backend
KSM_KERNEL_API_KEY=dev-api-key
```

Tous les services sont alors disponibles sans créer `yowpainter-backend`.

### 2.5 Plan commercial `COMMERCE`

L'inscription **artiste** applique un plan commercial sur l'organisation créée. Le plan `COMMERCE` doit exister dans le kernel (bootstrap ou seed local).

### 2.6 Rôles administratifs (inscription admin)

`POST /api/administration/roles/defaults` provisionne notamment le rôle **`GENERAL_ADMIN`** (pas `TENANT_ADMIN`).

L'inscription admin YowPainter (`POST /api/admin/auth/register`) :

1. Sign-up kernel (`PROSPECT`)
2. Provision des rôles via session `platform-admin`
3. Assignation du rôle `GENERAL_ADMIN` à l'utilisateur

---

## 3. Configuration côté YowPainter

### 3.1 Fichier `.env.local`

Copier `.env.example` → `.env.local` (ne **jamais** committer `.env.local`).

Exemple aligné sur la config actuelle du projet :

```bash
# Kernel
KSM_KERNEL_BASE_URL=http://localhost:8080
KSM_KERNEL_CLIENT_ID=yowpainter-backend
KSM_KERNEL_API_KEY=yowpainter-local-dev-secret-2026
KSM_KERNEL_TENANT_ID=11111111-1111-1111-1111-111111111111
KSM_KERNEL_JWK_SET_URI=http://localhost:8080/.well-known/jwks.json
KSM_KERNEL_DEFAULT_PLAN_CODE=COMMERCE
KSM_KERNEL_DEFAULT_CURRENCY=XAF

# Port backend (évite conflit avec kernel 8080)
PORT=8090

# CORS + emails
FRONTEND_URL=http://localhost:3000

# URL publique du backend : le kernel l'utilise comme callbackUrl de paiement.
# En local, le kernel doit pouvoir joindre cette URL (sinon : tunnel type ngrok).
BACKEND_URL=http://localhost:8090

# Paiement — le kernel pilote le PSP ; aucun secret PSP ici.
PAYMENT_PROVIDER=MYCOOLPAY   # MYCOOLPAY (Mobile Money) | STRIPE (carte)
PAYMENT_METHOD=MOBILE_MONEY  # MOBILE_MONEY | CARD

# Compte admin kernel (inscription artiste/admin, provision rôles, paiements)
KSM_KERNEL_BOOTSTRAP_ADMIN_USERNAME=platform-admin
KSM_KERNEL_BOOTSTRAP_ADMIN_PASSWORD=PlatformAdmin!123
```

### 3.2 Mapping vers `application.yml`

Les variables ci-dessus alimentent `ksm.kernel.*` dans `src/main/resources/application.yml` :

| Variable env | Propriété Spring | Usage |
|--------------|------------------|-------|
| `KSM_KERNEL_BASE_URL` | `ksm.kernel.base-url` | URL RestClient kernel |
| `KSM_KERNEL_CLIENT_ID` | `ksm.kernel.client-id` | Header `X-Client-Id` |
| `KSM_KERNEL_API_KEY` | `ksm.kernel.api-key` | Header `X-Api-Key` |
| `KSM_KERNEL_TENANT_ID` | `ksm.kernel.tenant-id` | Header `X-Tenant-Id` |
| `KSM_KERNEL_JWK_SET_URI` | `ksm.kernel.jwk-set-uri` | Validation JWT RS256 |
| `KSM_KERNEL_BOOTSTRAP_ADMIN_*` | `ksm.kernel.bootstrap-admin-*` | Session admin pour opérations privilégiées (dont paiements) |
| `BACKEND_URL` | `app.backend-url` | `callbackUrl` transmise au kernel lors d'un paiement |
| `PAYMENT_PROVIDER` | `app.payment.provider` | `MYCOOLPAY` \| `STRIPE` |
| `PAYMENT_METHOD` | `app.payment.method` | `MOBILE_MONEY` \| `CARD` |

> Les identifiants des PSP (MyCoolPay, Stripe) se configurent **dans le kernel**, jamais dans YowPainter.

### 3.3 Démarrage

```powershell
.\scripts\run-local.ps1
```

- Swagger : `http://localhost:8090/swagger-ui.html`
- Health : `http://localhost:8090/api/public/health`

### 3.4 Base de données YowPainter

YowPainter conserve sa **propre base PostgreSQL** (profils `Artist` / `Buyer` / `Admin`, œuvres, commandes locales, etc.).

Variables optionnelles si vous n'utilisez pas les défauts Render dans `application.yml` :

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://...
DB_USER=...
DB_PASS=...
```

---

## 4. Contrat HTTP backend → kernel

Chaque appel sortant de YowPainter vers le kernel envoie automatiquement (via `KernelHttpClient`) :

| Header | Source | Obligatoire |
|--------|--------|-------------|
| `X-Client-Id` | `KSM_KERNEL_CLIENT_ID` | Oui |
| `X-Api-Key` | `KSM_KERNEL_API_KEY` | Oui |
| `X-Tenant-Id` | `KSM_KERNEL_TENANT_ID` | Oui |
| `Authorization: Bearer …` | JWT utilisateur ou bootstrap admin | Selon endpoint |
| `X-Organization-Id` | Contexte artiste / commerce | Endpoints org-scopés |

Le kernel vérifie dans l'ordre :

1. ClientApplication valide (`client-id` + secret)
2. Service autorisé dans `allowedServices` (si route mappée)
3. Quotas tenant / organisation
4. Permissions JWT utilisateur

---

## 5. Flux d'authentification

### 5.1 Login utilisateur

```
Client → POST /api/auth/login (YowPainter)
       → POST /api/auth/login (kernel)
       ← JWT RS256 (sub = UUID utilisateur kernel)
```

YowPainter valide le JWT via JWKS kernel et lie le profil local via `kernel_user_id` ou email (`/api/users/me`).

### 5.2 Appels protégés Swagger

1. `POST /api/auth/login` ou `/api/admin/auth/login`
2. Copier `accessToken` dans **Authorize**
3. Token expire ~15 min → se reconnecter si 401

### 5.3 JWT kernel vs profil local

| Champ JWT | Signification |
|-----------|---------------|
| `sub` | UUID utilisateur kernel (pas l'email) |
| `tid` | Tenant ID |
| `authorities` / `permissions` | Droits kernel (ex. `ROLE_GENERAL_ADMIN`) |

YowPainter résout le profil local (`Artist`, `Buyer`, `Admin`) à partir de `kernel_user_id` ou de l'email kernel.

---

## 6. Checklist de mise en route

### Kernel

- [ ] Kernel démarré sur `http://localhost:8080`
- [ ] PostgreSQL + Redis kernel OK
- [ ] `platform-admin` existe, MFA activé
- [ ] ClientApplication `yowpainter-backend` créée **ou** usage de `dev-platform-backend`
- [ ] Plan `COMMERCE` disponible

### YowPainter

- [ ] `.env.local` créé et secrets alignés avec le kernel
- [ ] `PORT=8090`
- [ ] `KSM_KERNEL_BOOTSTRAP_ADMIN_*` renseignés
- [ ] `.\scripts\run-local.ps1` → health `UP`
- [ ] Swagger accessible

### Test rapide

```powershell
# Health YowPainter
curl http://localhost:8090/api/public/health

# Inscription acheteur (email nouveau)
# POST /api/auth/register dans Swagger

# Login + upload photo profil
# POST /api/me/profile-picture (multipart, champ file)
```

---

## 7. Erreurs fréquentes et solutions

| Symptôme | Cause probable | Action |
|----------|----------------|--------|
| `401` sur routes protégées | JWT expiré | Re-login Swagger, nouveau token |
| `401` sur `/api/auth/sign-up` | `client-id` / `api-key` invalides | Vérifier `.env.local` ou lancer `bootstrap-kernel-client.ps1` |
| `403 Profil local introuvable` | Pas de compte local lié au JWT | S'inscrire via YowPainter ou se reconnecter après sync `kernel_user_id` |
| `501` inscription admin « TENANT_ADMIN introuvable » | Ancienne version backend | Utiliser version avec rôle `GENERAL_ADMIN` + redémarrer |
| `502` upload fichier | Kernel inaccessible ou erreur `/api/files` | Vérifier kernel `8080`, token valide |
| `500` upload photo `NoClassDefFoundError: Publisher` | Bug multipart corrigé | Redémarrer backend après `mvn compile` |
| `403 MFA_REQUIRED_FOR_ADMIN` | MFA non activé sur `platform-admin` | SQL ci-dessus ou client `dev-platform-backend` |
| Email déjà utilisé à l'inscription | Compte kernel existant | Nouvel email |

---

## 8. Récapitulatif des endpoints kernel consommés

| Domaine YowPainter | Endpoint kernel |
|--------------------|-----------------|
| Auth | `/api/auth/sign-up`, `/login`, `/refresh`, `/logout`, reset password |
| Profil | `/api/users/me` |
| Administration | `/api/administration/roles`, `/roles/defaults`, `/users/{id}/roles` |
| Organisation | `/api/organizations` + plans commerciaux |
| Produits / ventes | `/api/products`, `/api/sales/orders` |
| Fichiers | `POST /api/files?documentType=PROFILE_PICTURE` (ou `ARTWORK_IMAGE`) |
| Notifications | `/api/notifications/deliveries` |

---

## 9. Fichiers utiles dans ce dépôt

| Fichier | Rôle |
|---------|------|
| `.env.example` | Modèle variables locales |
| `.env.local` | Config réelle (gitignored) |
| `scripts/run-local.ps1` | Charge `.env.local` + `mvn spring-boot:run` |
| `scripts/bootstrap-kernel-client.ps1` | Crée `yowpainter-backend` dans le kernel |
| `docs/KERNEL_INTEGRATION.md` | Architecture hexagonale et flux métier |
| `D:\KSM_Kernel_Layer\docs\external-backend-integration.md` | Référence officielle kernel |

---

## 10. Production (rappels)

- Générer un **secret fort** pour `KSM_KERNEL_API_KEY` (ne pas réutiliser `yowpainter-local-dev-secret-2026`).
- Restreindre `allowedServices` au strict nécessaire.
- Désactiver ou changer le mot de passe `platform-admin` bootstrap après provisioning.
- `KSM_KERNEL_JWK_SET_URI` doit pointer vers le JWKS du kernel de production.
- Le frontend utilise `NEXT_PUBLIC_API_URL` vers l'URL YowPainter publique, jamais le kernel.
