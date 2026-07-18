
# YowPainter Backend API 🎨

Une plateforme SaaS multi-tenant moderne conçue pour les artistes et les collectionneurs d'art. Le backend gère l'isolation complète des données pour chaque artiste (tenant) tout en offrant une expérience fluide pour les acheteurs sur le schéma public.

## 🚀 Stack Technique

Le projet a été récemment modernisé vers les standards les plus récents :

*   **Framework** : Spring Boot 4.0.0 (basé sur Spring Framework 7.0)
*   **Base de Données** : PostgreSQL 18.0
*   **Multi-Tenancy** : Isolation par schéma (Schema-per-tenant) via Hibernate 6
*   **Sécurité** : Spring Security 7.0 avec Authentification JWT (Stateless)
*   **Paiements** : Encaissement **délégué au Kernel Core** (module billing) — MyCoolPay (Mobile Money) et Stripe (carte). Le backend n'intègre aucun PSP en direct.
*   **Migrations** : Liquibase (gestion automatique des schémas public et artistes)
*   **Documentation** : OpenAPI 3 / Swagger UI (SpringDoc v3)
*   **E-mails** : Spring Boot Mail (pour la récupération de mot de passe)
*   **Utilitaires** : Lombok, Jakarta Validation

## 🏗️ Architecture du Projet

Le backend de YowPainter suit une **Architecture Hexagonale (Ports & Adapters)** couplée à une approche **modulaire**. Cette structure assure une séparation stricte entre la logique métier pure (le domaine) et les détails techniques (bases de données, frameworks web, services tiers).

### 🌌 Avantages de cette architecture
1. **Indépendance des Frameworks** : Le cœur de l'application (le Domaine) ne dépend d'aucune bibliothèque externe ni du framework Spring Boot.
2. **Facilité de Test** : La logique métier peut être testée unitairement de manière extrêmement simple et rapide en simulant (mockant) ou en implémentant de simples doublons (stubs) pour les ports de sortie.
3. **Flexibilité et Évolutivité** : Remplacer un composant technique (ex: changer de base de données, ou brancher un nouvel endpoint de paiement du Kernel) se fait simplement en implémentant un nouvel adaptateur sans altérer la logique métier centrale.

---

### 🎨 Schéma de l'Architecture

```mermaid
flowchart TB
    subgraph Infrastructure [Couche Infrastructure (Adapters)]
        direction TB
        in_rest[REST Controllers / DTOs]
        in_ws[WebSocket Endpoints]
        out_jpa[JPA Repositories]
        out_client[Kernel Core API Client <br> (billing, wallet, blockchain)]
        out_mail[Email Sender API Implementation]
    end

    subgraph Application [Couche Application (Use Cases)]
        services[Application Services <br> (Orchestration des flux métier)]
    end

    subgraph Domain [Couche Domaine (Cœur Métier)]
        direction TB
        ports_out[Ports de sortie / Outbound Ports <br> (Interfaces: AppUserRepositoryPort, etc.)]
        models[Modèles de Domaine & Règles Métier <br> (AppUser, Artwork, Payment...)]
    end

    in_rest & in_ws -->|Invoquent| services
    services -->|Coordonne| models
    services -->|Utilise| ports_out
    out_jpa -.->|Implémente| ports_out
    out_client -.->|Implémente| ports_out
    out_mail -.->|Implémente| ports_out

    style Domain fill:#e1f5fe,stroke:#0288d1,stroke-width:2px
    style Application fill:#efebe9,stroke:#5d4037,stroke-width:2px
    style Infrastructure fill:#efe8e9,stroke:#b00020,stroke-width:2px
```

### 📂 Structure Modulaire des Packages

Le code est structuré en **modules verticaux** autonomes (ex: `auth`, `payment`, `artwork`, `artist`, etc.) situés sous `com.yowpainter.modules`. 
Chaque module respecte scrupuleusement le découpage hexagonal suivant :

```text
com.yowpainter.modules.<nom-du-module>/
├── domain/                         # Couche Domaine (Cœur Métier)
│   ├── model/                      # Entités pures et objets de valeur (ex: AppUser)
│   └── port/                       # Interfaces définissant les besoins du domaine
│       └── out/                    # Ports de sortie (Interfaces de dépôts, ex: AppUserRepositoryPort)
│
├── application/                    # Couche Application (Orchestration)
│   └── service/                    # Services applicatifs (ex: AuthService, coordinateur des cas d'utilisation)
│
└── infrastructure/                 # Couche Infrastructure (Adapters)
    └── adapter/                    # Implémentations concrètes des interactions externes
        ├── in/                     # Adaptateurs d'entrée (Driving Adapters: REST controllers, DTOs, WebSockets)
        └── out/                    # Adaptateurs de sortie (Driven Adapters: persistence JPA, clients HTTP tiers)
            └── persistence/        # Implémentations concrètes des ports de sortie JPA (ex: AppUserRepositoryRepositoryAdapter)
```

## ✨ Fonctionnalités Clés

### 🔐 Authentification & Sécurité
*   **Inscription & Connexion** : Support des rôles ARTISTE, ACHETEUR et ADMIN.
*   **Multi-Tenant Provisioning** : Chaque nouvel artiste reçoit automatiquement son propre schéma de base de données isolé.
*   **Réinitialisation de mot de passe** : Système complet par jeton UUID envoyé par e-mail sécurisé.

### 💰 Système de Paiement (délégué au Kernel Core)

Le backend **n'intègre plus aucun fournisseur de paiement en direct**. L'encaissement est confié au module *billing* du Kernel Core, qui pilote les PSP (MyCoolPay pour le Mobile Money, Stripe pour la carte).

*   **Initiation** : `POST /api/payments/orders` sur le kernel, avec une **clé d'idempotence** dérivée de la référence métier — relancer un checkout ne crée jamais un second encaissement.
*   **Boutique (Shop)** : Paiement des commandes d'articles d'art.
*   **Événements** : Réservation et paiement de billets pour des vernissages ou expositions.
*   **Abonnements** : Paiement des forfaits artistes.
*   **Callbacks** : `POST /api/payment/callback` reçoit les notifications du kernel. ⚠️ Cet endpoint est **public et son contenu n'est jamais cru sur parole** : statut et montant sont systématiquement reconfirmés auprès du kernel (`POST /api/payments/orders/{id}/refresh`) avant tout effet de bord. Un callback forgé est sans effet.
*   **Rattrapage** : un scheduler rafraîchit les paiements restés `PENDING` (callback perdu).
*   **Retraits (payout)** : indisponibles — le kernel n'expose pas encore d'endpoint de décaissement.

### 🎨 Gestion Artistique
*   **Artwork** : Gestion des collections et des images d'œuvres.
*   **Artiste** : Profil personnalisable avec slug unique (utilisé comme identifiant de tenant).
*   **Recherche** : Système de recherche global et filtré.

### 🔔 Notifications & Abonnements
*   **Alertes** : Système de notification interne pour les nouvelles commandes ou réservations.
*   **Abonnements** : Gestion des abonnés aux profils d'artistes.

### 💬 Messagerie en Temps Réel (WebSockets)
L'application utilise le protocole STOMP sur WebSockets pour le chat instantané.
*   **Endpoint de connexion** : `/ws` (Support SockJS activé).
*   **Authentification** : Envoyer le token JWT dans le header `Authorization: Bearer <token>` lors de la connexion STOMP.
*   **Destination d'envoi** : `/app/chat`
*   **Abonnement réception** : `/user/queue/messages` (pour les messages privés).
*   **Payload (JSON)** : Contient `senderId`, `recipientId`, `content` et `timestamp`.

## ⚙️ Configuration & Installation

### Prérequis
*   Java 17 ou supérieur
*   Maven 3.9+
*   PostgreSQL 18

### Configuration (`src/main/resources/application.yml`)
Vous devez configurer les variables suivantes :

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nom_votre_db
    username: votre_user
    password: votre_password
  mail:
    host: smtp.votre_fournisseur.com
    username: votre_email@exemple.com
    password: votre_mot_de_passe_app

jwt:
  secret: votre_cle_secrete_de_32_caracteres_minimum

ksm:
  kernel:
    base-url: https://kernel-core.yowyob.com/kernel-api
    client-id: votre_client_id
    api-key: votre_api_key
    tenant-id: votre_tenant_id

app:
  frontend-url: http://localhost:3000
  # URL publique du backend : le kernel l'utilise pour notifier les paiements.
  backend-url: http://localhost:8090
  payment:
    provider: MYCOOLPAY   # MYCOOLPAY (Mobile Money) | STRIPE (carte)
    method: MOBILE_MONEY  # MOBILE_MONEY | CARD
```

> Aucun identifiant PSP n'est configuré ici : les secrets des fournisseurs de paiement vivent **dans le kernel**, jamais dans YowPainter.

### Lancement
```bash
mvn clean install
mvn spring-boot:run
```

## 📖 API Documentation
Une fois l'application lancée, la documentation interactive Swagger est accessible à l'adresse :
`http://localhost:8080/swagger-ui.html`

---
*YowPainter - Propulsant la nouvelle génération d'artistes.*
