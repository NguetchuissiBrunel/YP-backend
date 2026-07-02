# Guide de Test et de Vérification - Workflow Acheteur (Buyer)

Ce document détaille les procédures de test et de validation de l'inscription, de la connexion et de la gestion de profil pour les **Acheteurs (Buyers)** de YowPainter.

---

## 1. Cartographie des Interfaces de Test

Comme pour les artistes, les tests de l'acheteur impliquent :
1.  **Swagger YowPainter Backend (Local/Dev) :** Accessible sur `http://localhost:8090/swagger-ui/index.html`. Permet de tester les endpoints d'inscription, de connexion, de gestion du profil acheteur et d'achat.
2.  **Swagger KSM Kernel Core (IAM tiers) :** Accessible sur `https://kernel-core.yowyob.com/swagger-ui/index.html`. Permet d'auditer et de vérifier la création des utilisateurs acheteurs côté Kernel.

---

## 2. Flux de Test et Vérification Étape par Étape

### Étape 1 : Inscription de l'Acheteur
*   **Interface de test :** **Swagger YowPainter Backend**
*   **Endpoint à appeler :** `POST /api/auth/register`
*   **Payload à soumettre :**
    ```json
    {
      "email": "buyer@example.com",
      "password": "SecurePassword123",
      "firstName": "Jane",
      "lastName": "Doe",
      "role": "ROLE_BUYER"
    }
    ```
*   **Vérification sous-jacente :**
    1.  Le backend contacte le Kernel IAM pour découvrir les contextes d'inscription associés à l'organisation de la plateforme (`yow-painter`).
    2.  Le compte de l'acheteur est créé au niveau du Kernel sous le type de compte `PROSPECT` avec des métadonnées d'onboarding spécifiques (`platform = yowpainter`, `role = ROLE_BUYER`).
    3.  Un enregistrement est inséré dans la base locale `public.app_user` avec le rôle `ROLE_BUYER` et un hash de mot de passe factice `{KERNEL_MANAGED}` (les mots de passe réels sont gérés par le Kernel).
    4.  Si la validation e-mail est requise par le Kernel, la réponse retourne un statut `PENDING_EMAIL` et aucun token JWT n'est renvoyé dans la réponse immédiate.

---

### Étape 2 : Confirmation de l'e-mail
*   **Interface de test :** **Swagger YowPainter Backend** ou lien de redirection
*   **Endpoint à appeler :** `POST /api/auth/confirm-email` ou lien `GET /api/auth/confirm-email?token={token}`
*   **Vérification sous-jacente :**
    1.  Le jeton de vérification reçu par l'acheteur est transmis au Kernel pour confirmation.
    2.  À la confirmation, le statut local de l'utilisateur passe automatiquement à `ACTIVE` (les acheteurs n'ont pas besoin d'approbation manuelle administrative contrairement aux artistes).
    3.  L'acheteur est désormais autorisé à s'authentifier.

---

### Étape 3 : Authentification (Connexion)
*   **Interface de test :** **Swagger YowPainter Backend**
*   **Endpoint à appeler :** `POST /api/auth/login`
*   **Payload à soumettre :**
    ```json
    {
      "email": "buyer@example.com",
      "password": "SecurePassword123"
    }
    ```
*   **Vérification sous-jacente :**
    1.  Le backend transmet les identifiants au Kernel IAM pour authentification.
    2.  Le profil local est synchronisé et le `kernelUserId` est lié en base de données.
    3.  Le backend renvoie un objet `AuthResponse` contenant :
        *   Les jetons `accessToken` et `refreshToken`.
        *   Les détails de l'acheteur (`firstName`, `lastName`, `email`, `role = ROLE_BUYER`, `registrationStatus = ACTIVE`).
        *   L'URL de son avatar (désinfectée sous forme d'URL relative `/api/files/{id}`).
    4.  Copiez le `accessToken` et utilisez-le pour vous authentifier sur le Swagger via le bouton **Authorize** (format `Bearer <token>`).

---

### Étape 4 : Consultation du profil connecté
*   **Interface de test :** **Swagger YowPainter Backend** (authentifié avec le token de l'acheteur)
*   **Endpoint à appeler :** `GET /api/buyer/me`
*   **Vérification sous-jacente :**
    1.  Retourne les détails complets du profil de l'acheteur connecté (id, firstName, lastName, email, profilePictureUrl, bio, role).
    2.  Vérifiez que le rôle renvoyé est bien `"ROLE_BUYER"`.

---

### Étape 5 : Mise à jour du profil de l'acheteur
*   **Interface de test :** **Swagger YowPainter Backend** (authentifié avec le token de l'acheteur)
*   **Endpoint à appeler :** `PUT /api/buyer/me`
*   **Payload à soumettre :**
    ```json
    {
      "firstName": "Jane Modified",
      "lastName": "Doe Modified",
      "bio": "Passionnée d'art contemporain et collectionneuse."
    }
    ```
*   **Vérification sous-jacente :**
    1.  Les informations de l'acheteur sont mises à jour dans la table `public.app_user` en base locale.
    2.  La réponse HTTP retourne le profil modifié avec un statut `200 OK`.

---

### Étape 6 : Parcours d'achat et isolation (Commande et Paiement)
*   **Interface de test :** **Swagger YowPainter Backend** (authentifié avec le token de l'acheteur)
*   **Processus :**
    1.  L'acheteur parcourt les œuvres d'art disponibles via `GET /api/artworks` (les œuvres proviennent des schémas isolés de chaque artiste).
    2.  L'acheteur initie une commande via `POST /api/orders` en spécifiant l'ID de l'œuvre d'art.
    3.  La commande est enregistrée dans la base de données spécifique au schéma du tenant de l'artiste vendeur, garantissant l'isolation mais permettant à l'acheteur d'interagir avec la ressource de façon transparente.
    4.  L'acheteur effectue le paiement en appelant `POST /api/payments/initiate` avec son numéro de téléphone ou identifiant de paiement mobile.
