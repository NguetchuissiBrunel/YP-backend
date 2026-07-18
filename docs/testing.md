# Guide de Test et de Vérification Complet

Ce document fournit les procédures détaillées pour tester et valider l'ensemble des modules clés de l'application YowPainter Backend.

---

## 1. Cartographie des interfaces de Test (Swagger)

Le test complet du système requiert l'interaction avec deux interfaces Swagger distinctes :
1.  **Swagger YowPainter Backend (Local/Dev) :** Accessible sur `http://localhost:8090/swagger-ui/index.html` (ou l'URL de votre déploiement). C'est l'interface principale pour tester tous les flux métiers et l'authentification des utilisateurs finaux de YowPainter.
2.  **Swagger KSM Kernel Core (IAM tiers) :** Accessible sur `https://kernel-core.yowyob.com/swagger-ui/index.html`. C'est l'interface pour auditer et vérifier que les ressources d'identité (Utilisateurs, Organisations, Rôles, Permissions) ont bien été provisionnées ou configurées par YowPainter dans le fournisseur IAM.

---

## 2. Flux de Test et Vérification Étape par Étape

### Étape 1 : Inscription de l'Utilisateur
*   **Interface de test :** **Swagger YowPainter Backend** (`http://localhost:8090/swagger-ui/index.html`)
*   **Endpoint à appeler :** `POST /api/auth/register`
*   **Payload à soumettre :**
    ```json
    {
      "email": "artist@example.com",
      "password": "SecurePassword123",
      "firstName": "John",
      "lastName": "Doe",
      "artistName": "John Art",
      "slug": "john-art",
      "location": "Paris"
    }
    ```
*   **Vérification sous-jacente :**
    1.  Le backend YowPainter relaie la requête au Kernel IAM et crée le compte de l'utilisateur.
    2.  *(Facultatif)* Sur le **Swagger KSM Kernel Core**, vous pouvez exécuter un endpoint de recherche d'utilisateur pour vérifier la présence du compte créé sous l'identifiant IAM.
    3.  Dans la base de données de YowPainter, vérifiez que l'utilisateur est présent dans la table `public.app_user` et l'artiste à l'état `PENDING_APPROVAL` dans `public.artist`.

---

### Étape 2 : Authentification
*   **Interface de test :** **Swagger YowPainter Backend**
*   **Endpoint à appeler :** `POST /api/auth/login`
*   **Payload à soumettre :**
    ```json
    {
      "email": "artist@example.com",
      "password": "SecurePassword123"
    }
    ```
*   **Vérification sous-jacente :**
    1.  Le backend valide les identifiants auprès du Kernel IAM.
    2.  Le backend génère un token JWT signé qu'il renvoie dans le corps de réponse. Copiez ce token pour l'insérer dans le bouton **Authorize** en haut à droite du Swagger du Backend YowPainter (format `Bearer <token>`).

---

### Étape 3 : Approbation de l'Artiste & Provisionnement du Tenant
*   **Interface de test :** **Swagger YowPainter Backend** (authentifié en tant qu'Administrateur)
*   **Endpoint à appeler :** `POST /api/admin/artists/{id}/approve`

#### A. Comprendre les Rôles et la Répartition des Tâches : Administrateur YowPainter vs Artiste vs Administrateur Kernel
1.  **L'Administrateur YowPainter (ex: `admin-samuel@yowpainter.com`) :**
    *   C'est un compte distinct, avec le rôle `ROLE_ADMIN` au niveau de YowPainter.
    *   Il initie l'approbation de l'artiste depuis YowPainter. Il ne s'agit pas de l'artiste lui-même.
    *   Ce compte possède les droits pour approuver l'artiste dans YowPainter, mais il **n'a pas** les privilèges d'administration système sur le Kernel (son token est rejeté pour la création/validation d'organisation car `adm` vaut `false`).
2.  **L'Artiste (ex: `samuelftagat@gmail.com`) :**
    *   C'est l'utilisateur final artiste, créé avec le rôle `ROLE_ARTIST`.
    *   C'est l'artiste (via son token `userToken` obtenu lors de l'onboarding) qui **crée l'organisation** dans le Kernel.
    *   Tant qu'il n'est pas approuvé, son espace de travail (schéma de base de données isolé) n'est pas finalisé.
3.  **L'Administrateur Kernel (Bootstrap Admin technique, ex: `platform-admin`) :**
    *   C'est un compte technique dédié au niveau du Kernel-core (`adm: true` et permission `organizations:write`).
    *   C'est lui qui est chargé de **valider (approuver) la création de l'organisation** dans le Kernel-core.
    *   Pour éviter les conflits d'autorisation, YowPainter n'utilise jamais le jeton de l'Administrateur YowPainter connecté pour agir sur le Kernel. Il utilise exclusivement le compte technique `platform-admin` du Kernel.

#### B. Prérequis indispensables
1.  **Avoir un compte administrateur lié localement :**
    *   Le compte admin doit exister à la fois dans le Kernel et dans la base locale `public.app_user` (avec le rôle `ROLE_ADMIN`).
    *   *Si vous avez créé l'admin directement depuis le Swagger du Kernel*, il n'est pas présent dans la base locale de YowPainter. Vous devez l'insérer dans `public.app_user` pour que Spring Security lui attribue les droits `ROLE_ADMIN` :
        ```sql
        INSERT INTO public.app_user (id, email, password_hash, first_name, last_name, role, created_at, kernel_user_id) 
        VALUES ('8e222fe2-12cf-44d5-b4ad-8c7053ad34c7', 'admin-samuel@yowpainter.com', 'KERNEL_MANAGED', 'Samuel', 'Admin', 'ROLE_ADMIN', NOW(), '58ed3dec-c158-4322-9596-72b7ddbc7989');
        ```
2.  **Se connecter en tant qu'Administrateur :**
    *   Appelez `POST /api/auth/login` avec les identifiants de l'administrateur :
        ```json
        {
          "principal": "admin-samuel@yowpainter.com",
          "password": "Samuelseanpaint2*"
        }
        ```
    *   Copiez le `accessToken` reçu de la réponse et collez-le dans le bouton **Authorize** de Swagger (au format `Bearer <token>`).
3.  **Ajuster le statut de l'artiste :**
    *   Seuls les artistes ayant le statut `PENDING_APPROVAL` (email vérifié) peuvent être approuvés.
    *   *En local/sandbox*, si l'artiste est toujours au statut `PENDING_EMAIL`, forcez son statut à `PENDING_APPROVAL` en base de données :
        ```sql
        UPDATE public.artist SET status = 'PENDING_APPROVAL' WHERE email = 'samuelftagat@gmail.com';
        ```

#### C. Récupération de l'identifiant de l'artiste (`{id}`)
1.  Utilisez le token de l'administrateur pour appeler l'endpoint :
    `GET /api/admin/artists/pending`
2.  Dans la liste retournée, repérez l'artiste (ex: `samuelftagat@gmail.com`) et copiez la valeur du champ `"id"` (UUID local de l'artiste, par exemple `2df1c461-d8ec-4d29-b73d-5442b6765d99`).

#### D. Fonctionnement du mécanisme de MFA et reprise transactionnelle
Si les identifiants techniques `platform-admin` du Kernel requièrent une validation MFA (Multi-Factor Authentication), le backend YowPainter supporte un workflow asynchrone sécurisé :
1.  **Tentative de Login Technique :** Lors du premier appel à `POST /api/admin/artists/{id}/approve`, le backend se connecte en tant que `platform-admin` (le compte technique administrateur du Kernel dédié). Le JWT de l'administrateur YowPainter connecté n'est utilisé que pour autoriser localement le endpoint YowPainter. Toutes les requêtes vers le Kernel (`/api/organizations`, `/api/organizations/search`, et l'approbation `/api/organizations/{organizationId}/approve`) sont exécutées exclusivement avec le token d'administration technique bootstrap du Kernel.
2.  **Détection du MFA :** Si le Kernel renvoie une demande de MFA (status `CONFIRM_MFA`), le backend lève une exception `KernelMfaRequiredException`.
3.  **Persistance de la session en base :** Le token MFA temporaire est enregistré dans la table `public.pending_provision_session` avec l'identifiant de l'artiste en guise d'ID de session.
4.  **Retour de la réponse MFA_REQUIRED :** Le contrôleur retourne une réponse JSON avec le statut `MFA_REQUIRED` et l'identifiant de session :
    ```json
    {
      "status": "MFA_REQUIRED",
      "message": "Veuillez saisir le code reçu par email.",
      "mfaSessionId": "2df1c461-d8ec-4d29-b73d-5442b6765d99"
    }
    ```

#### E. Appel de la Confirmation MFA (Reprise)
Une fois le code MFA reçu par e-mail, l'administrateur confirme le code :
*   **Endpoint à appeler :** `POST /api/admin/artists/{id}/approve/confirm`
*   **Payload à soumettre :**
    ```json
    {
      "mfaCode": "123456"
    }
    ```
*   **Vérification sous-jacente :**
    1.  Le backend soumet le code avec le jeton MFA récupéré de la table `pending_provision_session` au Kernel via `/api/auth/login/mfa/confirm`.
    2.  Le Kernel retourne l'access token d'administration qui est mis en cache.
    3.  Le provisioning de l'artiste reprend automatiquement là où il s'était arrêté de manière transactionnelle.
    4.  **Idempotence :** Si l'organisation avait déjà été créée lors de la première tentative, le backend la recherche par son slug via `GET /api/organizations/search?q={slug}` pour éviter les conflits `409` et réutiliser l'organisation existante.
    5.  La session MFA temporaire est supprimée à la réussite du provisioning.

#### F. Gestion des Erreurs MFA et Codes Métiers
Le backend gère et traduit précisément les codes d'erreur du Kernel dans le corps de réponse :
*   **Code MFA Invalide :** Si le code est incorrect, retourne `400 Bad Request` avec `"AUTH_MFA_INVALID_CODE : Code MFA invalide."`.
*   **Code MFA Expiré :** Si le code est expiré, retourne `400 Bad Request` avec `"AUTH_MFA_EXPIRED : Code MFA expiré."`.
*   **Identifiants Invalides :** Si les identifiants techniques sont incorrects, retourne `"AUTH_INVALID_CREDENTIALS : Identifiants de connexion invalides."`.
*   **Indisponibilité :** Si le réseau ou le Kernel est inaccessible, retourne `"NETWORK_ERROR"`.

#### G. Vérifications post-approbation
1.  **Vérification de l'Organisation Kernel :**
    *   L'artiste passe au statut `ACTIVE` en base locale.
    *   Une organisation Kernel a été créée et activée. Sur le **Swagger KSM Kernel Core**, vous pouvez appeler `GET /api/v1/organizations/{organizationId}` pour vérifier sa validité.
2.  **Vérification de la base de données (Multi-Tenant) :**
    *   Dans PostgreSQL, vérifiez qu'un nouveau schéma nommé `tenant_<organization_id>` (ex: `tenant_58aa299f-b7f2-4049-a6b2-0e738f1df2e0`) a été créé.
    *   Vérifiez que toutes les tables (comme `artwork`, `order`, etc.) y ont été créées automatiquement par les migrations Liquibase (`db/changelog/db.changelog-tenant.yaml`).

---

### Étape 4 : Création d'Œuvres (Validation de l'isolation)
*   **Interface de test :** **Swagger YowPainter Backend** (authentifié avec le JWT de l'artiste John Art)
*   **Endpoint à appeler :** `POST /api/artworks`
*   **Payload à soumettre :**
    ```json
    {
      "title": "Ma superbe peinture",
      "description": "Acrylique sur toile",
      "technique": "ACRYLIC",
      "style": "MODERN",
      "dimensions": "80x60 cm",
      "price": 150000.00
    }
    ```
*   **Vérification sous-jacente :**
    1.  Le backend résout l'organisation rattachée au token de John Art et exécute l'insertion SQL dans le schéma `tenant_<john_art_org_id>`.
    2.  Connectez-vous avec un autre artiste et appelez `GET /api/artworks/me` : l'œuvre ne doit pas y figurer, confirmant l'isolation stricte des données de chaque schéma.

---

### Étape 5 : Flux de Commande et Paiement PaymentProvider
*   **Interface de test :** **Swagger YowPainter Backend** (authentifié avec un compte Acheteur)
*   **Endpoint à appeler :** `POST /api/orders`
*   **Vérification sous-jacente :**
    1.  La commande est créée dans le schéma tenant de l'artiste vendeur.
    2.  Initiez le paiement via `POST /api/payments/initiate`.
    3.  PaymentProvider Sandbox simule le prélèvement et notifie le backend sur le webhook de notification (`POST /api/payments/callback`).
    4.  Le callback met à jour le statut du paiement en `SUCCESSFUL` directement dans la table `payment` du schéma du tenant concerné, puis ajuste le solde du portefeuille dans `public.wallet`.

---

### Étape 6 : Réception de notifications en temps réel
*   **Interface de test :** **Client WebSocket externe** (ex: APIC, Chrome Extension Client) connecté sur `ws://localhost:8090/ws`
*   **Destination de souscription :** `/user/queue/notifications`
*   **Vérification sous-jacente :**
    1.  Lorsqu'une commande change de statut (ex: payée), une notification STOMP est envoyée au client WebSocket. Le client doit instantanément afficher le payload de notification.

---

## 3. Scénarios de Test pour la Récupération de Fichiers (BFF Proxy)

Ces scénarios valident le bon fonctionnement de l'endpoint proxy `/api/files/{fileId}` et de son intégration sécurisée avec le Kernel Core.

### Scénario 3.1 : Récupération d'une image existante (Succès)
*   **Description :** Récupération réussie d'une image par un utilisateur authentifié ou anonyme.
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Headers attendus dans la réponse :**
    - `Content-Type`: format MIME correct (ex: `image/jpeg` ou `image/png`)
    - `Content-Length`: taille exacte en octets du fichier
    - `Content-Disposition` (si disponible/nécessaire)
*   **Statut HTTP attendu :** `200 OK`
*   **Corps :** Flux binaire de l'image.

### Scénario 3.2 : Récupération d'une image inexistante
*   **Description :** Tentative de récupération d'un fichier avec un identifiant UUID non attribué ou inexistant.
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Statut HTTP attendu :** `404 Not Found` ou `400 Bad Request` (selon la réponse du Kernel).
*   **Corps :** Message d'erreur standardisé indiquant que le fichier est introuvable.

### Scénario 3.3 : Utilisateur non authentifié (Accès public)
*   **Description :** Un utilisateur anonyme (sans header `Authorization`) demande à afficher une image.
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Comportement attendu :** Le BFF résout le token système (`KernelSystemUserTokenProvider`) pour s'authentifier de manière transparente auprès du Kernel et renvoie l'image sans bloquer l'utilisateur final.
*   **Statut HTTP attendu :** `200 OK`

### Scénario 3.4 : Accès refusé par le Kernel
*   **Description :** Tentative d'accès à un fichier dont le droit de lecture est explicitement révoqué ou restreint au niveau du Kernel.
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Statut HTTP attendu :** `403 Forbidden` ou `401 Unauthorized` relayé proprement par le BFF.

### Scénario 3.5 : Erreur interne du Kernel Core
*   **Description :** Le Kernel Core rencontre une erreur interne lors du chargement ou du traitement du fichier.
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Statut HTTP attendu :** `502 Bad Gateway` ou `500 Internal Server Error` retourné par le BFF.

### Scénario 3.6 : Timeout d'appel au Kernel
*   **Description :** L'appel HTTP du BFF vers le Kernel Core dépasse le délai d'attente (timeout).
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Statut HTTP attendu :** `504 Gateway Timeout` ou `500 Internal Server Error`.

### Scénario 3.7 : Erreur réseau / Perte de connexion avec le Kernel
*   **Description :** Le Kernel Core est injoignable par le BFF (serveur éteint, coupure réseau).
*   **Méthode / URL :** `GET /api/files/{fileId}`
*   **Statut HTTP attendu :** `503 Service Unavailable` ou `500 Internal Server Error`.
