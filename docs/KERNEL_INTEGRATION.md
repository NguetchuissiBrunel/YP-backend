# Intégration YowPainter ↔ Kernel RT-Comops

Ce document décrit l'activation du backend YowPainter en **mode consommateur hexagonal** du kernel RT-Comops.

> **Guide configuration pas à pas** (kernel + `.env.local` + bootstrap + dépannage) : [CONFIGURATION_KERNEL_YOWPAINTER.md](./CONFIGURATION_KERNEL_YOWPAINTER.md)

## Prérequis kernel

1. Déployer le kernel RT-Comops (`D:\KSM_Kernel_Layer` ou stack Docker).
2. Créer une **ClientApplication** nommée `yowpainter-backend` dans le kernel.
3. Récupérer : `client-id`, `api-key`, `tenant-id` (UUID du tenant kernel).
4. S'assurer que le plan commercial `COMMERCE` (ou plan custom) est disponible.

## Variables d'environnement

```bash
KSM_KERNEL_BASE_URL=http://localhost:8080
KSM_KERNEL_CLIENT_ID=yowpainter-backend
KSM_KERNEL_API_KEY=<secret>
KSM_KERNEL_TENANT_ID=<uuid-tenant-kernel>
KSM_KERNEL_DEFAULT_PLAN_CODE=COMMERCE
KSM_KERNEL_DEFAULT_CURRENCY=XAF

# JWT RS256 via JWKS kernel (optionnel si base-url correcte)
KSM_KERNEL_JWK_SET_URI=http://localhost:8080/.well-known/jwks.json

# Kafka — synchronisation événements métier (optionnel)
KSM_KERNEL_KAFKA_ENABLED=true
KSM_KERNEL_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KSM_KERNEL_KAFKA_TOPIC=iwm.events.business
KSM_KERNEL_KAFKA_GROUP_ID=yowpainter-backend

# Migration one-shot organizationId sur données existantes
KSM_KERNEL_LEGACY_MIGRATION_ENABLED=true
```

Le backend YowPainter est **toujours** un consommateur du kernel (auth, org, commerce, fichiers, notifications). Le frontend **ne doit jamais** appeler le kernel avec `X-Api-Key`.

## Flux principaux

### Auth
- `POST /api/auth/login` → proxy kernel → JWT RS256
- `POST /api/auth/forgot-password` → kernel `/api/auth/forgot-password` + `/api/auth/password-reset/issue`
- `POST /api/auth/reset-password` → kernel `/api/auth/reset-password` (token kernel)
- Inscription artiste → sign-up kernel ; provisioning org/plan/rôle soit **automatique** (`KSM_KERNEL_AUTO_PROVISION_ARTISTS=true`), soit **validation admin** (`false`, voir [ARTIST_APPROVAL_WORKFLOW.md](./ARTIST_APPROVAL_WORKFLOW.md))
- Inscription acheteur → sign-up kernel `PROSPECT` + `AppUser` local
- Inscription admin (`POST /api/admin/auth/register`) → sign-up kernel + rôle `GENERAL_ADMIN` (provisionné via `POST /api/administration/roles/defaults`) + assignation via `/api/administration/users/{id}/roles` (session bootstrap `platform-admin` requise)

### Commerce
- Produits/commandes délégués au kernel (`KernelCommerceService`)
- À succès du paiement : `POST /api/sales/orders/{id}/confirm` sur le kernel
- Événements Kafka `SALES_ORDER_CONFIRMED` / `SALES_ORDER_CANCELLED` → sync statut commande locale

### Paiement (module billing du kernel)

YowPainter **n'intègre aucun PSP en direct** : l'encaissement est délégué au kernel, qui pilote MyCoolPay (Mobile Money) et Stripe (carte). Les secrets PSP vivent dans le kernel.

| Étape | Appel |
|---|---|
| Initier | `POST /api/payments/orders` — `InitiatePaymentRequest` avec `idempotencyKey` dérivée de la référence métier (`yowpainter-<type>-<referenceId>`) |
| Vérifier | `POST /api/payments/orders/{id}/refresh` — **seule source de vérité** sur l'issue d'un paiement |
| Consulter | `GET /api/payments/orders/{id}` |

Adaptateur : `KernelPaymentOrderHttpAdapter` (port `KernelPaymentOrderPort`).

**Authentification.** Ces endpoints sont **server-to-server** : ils s'authentifient avec les en-têtes `X-Client-Id` / `X-Api-Key` que `KernelHttpClient` envoie sur chaque appel. **Aucun jeton porteur n'est requis** — vérifié en direct (`X-Client-Id`+`X-Api-Key` seuls → 500 « amount is required », donc l'auth passe ; sans en-têtes → 401). On ne déclenche donc **jamais** de login admin bootstrap sur le chemin de paiement : un `/api/auth/login` sur le chemin critique était fragile (un hoquet de la gateway le faisait échouer et cassait tout le paiement). Le bearer de l'acheteur, s'il est présent dans le `RequestContext` pendant un checkout, est transmis en bonus mais n'est pas nécessaire.

**Règle de sécurité.** `POST /api/payment/callback` est public (l'émetteur ne s'authentifie pas auprès de nous). Son contenu n'est **jamais** une preuve de paiement : il ne sert qu'à désigner la référence à re-vérifier. `PaymentService` reconfirme systématiquement statut **et** montant via `refresh` avant tout effet de bord (commande payée, wallet crédité, facture émise). Un kernel injoignable ne valide jamais un paiement.

**Rattrapage.** `PaymentPollingService` rafraîchit toutes les 10 min les paiements `PENDING` de plus de 5 min, pour le cas d'un callback perdu.

**Décaissement.** Aucun endpoint kernel de payout : `PayoutService` refuse la demande **avant** tout mouvement (un débit suivi d'un rollback laissait auparavant un retrait fantôme ancré sur la blockchain).

> ⚠️ Piège swagger. Les endpoints `/api/paiement` (billing legacy) renvoient un `PaymentView` **brut**, sans enveloppe `ApiResponse` — contrairement à `/api/payments/orders` et `/api/v1/blockchain/*` qui sont enveloppés. D'où les variantes `postRaw`/`putRaw`/`getRaw` de `KernelHttpClient`, à n'utiliser **que** pour `/api/paiement`.

> ⚠️ Piège swagger (bis). Le schéma `WalletResponse` du swagger kernel est une **collision de noms** springdoc entre le wallet billing (`ownerId`, `balance`) et le wallet blockchain (`organizationId`, `label`, `publicKey`, `fingerprint`, `active`). `KernelWalletResponseDto` suit le **vrai** contrat blockchain, pas le swagger : ne pas l'« aligner » sur ce dernier.

### Fichiers
- `POST /api/artworks/images/upload` (multipart) → `POST /api/files` kernel
- `POST /api/me/profile-picture` (multipart) → `POST /api/files` kernel (`PROFILE_PICTURE`)
- URL retournée : `{KSM_KERNEL_BASE_URL}/api/files/{fileId}`

### Notifications
- Source unique : kernel (`POST /api/notifications/deliveries` + `GET /api/notifications/deliveries`)
- Plus de double écriture en base locale YowPainter

## Architecture hexagonale

```
shared/kernel/
  port/          → KernelAuthPort, KernelOrganizationPort, KernelProductPort, ...
  adapter/       → Implémentations HTTP (RestClient)
  event/         → Consommation Kafka (optionnel)
config/          → KernelProperties, KernelKafkaProperties, KernelSecurityConfig
```

## Migration données legacy

Pour backfiller `organization_id` sur artworks/produits/commandes existants :

1. Chaque artiste doit avoir `organization_id` renseigné (via inscription kernel ou admin).
2. Activer `KSM_KERNEL_LEGACY_MIGRATION_ENABLED=true` **une seule fois** au démarrage.
3. Désactiver ensuite la variable.

> Les anciennes données multi-schémas nécessitent une migration manuelle ou une réinscription des artistes via le kernel.

## Dépannage inscription (`POST /api/auth/register` → 500)

Cause la plus fréquente en local : la `ClientApplication` `yowpainter-backend` n'existe pas encore dans le kernel, ou le secret ne correspond pas.

1. Vérifier les logs backend : `Kernel call failed on /api/auth/sign-up` avec message kernel (401 = credentials invalides).
2. **Dev rapide** : dans `.env.local`, utiliser le client bootstrap kernel :
   ```bash
   KSM_KERNEL_CLIENT_ID=dev-platform-backend
   KSM_KERNEL_API_KEY=dev-api-key
   ```
3. **Client dédié** : exécuter `.\scripts\bootstrap-kernel-client.ps1` (nécessite MFA activé sur `platform-admin` dans le kernel).
4. Redémarrer le backend après modification de `.env.local`.

## Dev local (kernel sur 8080, backend sur 8090)

1. Démarrer le kernel RT-Comops en local (`http://localhost:8080`).
2. Créer la `ClientApplication` `yowpainter-backend` dans le kernel :
   ```powershell
   .\scripts\bootstrap-kernel-client.ps1
   ```
   (ou manuellement via `D:\KSM_Kernel_Layer\docs\external-backend-integration.md`)
3. Copier `.env.example` → `.env.local` et vérifier que `KSM_KERNEL_API_KEY` correspond au secret de la ClientApplication.
4. Lancer :

```powershell
.\scripts\run-local.ps1
```

Variables typiques :

```bash
KSM_KERNEL_BASE_URL=http://localhost:8080
KSM_KERNEL_CLIENT_ID=yowpainter-backend
KSM_KERNEL_API_KEY=yowpainter-local-dev-secret-2026
KSM_KERNEL_TENANT_ID=11111111-1111-1111-1111-111111111111
KSM_KERNEL_JWK_SET_URI=http://localhost:8080/.well-known/jwks.json
PORT=8090
```

- **Kernel** : `http://localhost:8080`
- **YowPainter** : `http://localhost:8090` (Swagger : `/swagger-ui.html`)
- **Frontend** : `NEXT_PUBLIC_API_URL=http://localhost:8090`

## Tests locaux

```bash
mvn test
```

## Endpoints kernel consommés

| Domaine | Endpoints |
|---------|-----------|
| Auth | `/api/auth/sign-up`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/forgot-password`, `/api/auth/password-reset/issue`, `/api/auth/reset-password` |
| Administration | `/api/administration/roles`, `/api/administration/roles/defaults`, `/api/administration/users/{id}/roles` |
| Org | `/api/organizations`, plans commerciaux |
| Produits | `/api/products` |
| Ventes | `/api/sales/orders`, `.../confirm`, `.../cancel` |
| Fichiers | `/api/files` |
| Notifications | `POST/GET /api/notifications/deliveries` |
