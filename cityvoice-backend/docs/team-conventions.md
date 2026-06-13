# Conventions d'équipe

## Structure des packages
tn.cityvoice.[service-name]
├── controller/ # REST Controllers
├── service/ # Interfaces
├── repository/ # JPA Repositories
├── entity/ # Entités JPA
│ └── enums/ # Énumérations

## Ports des services

| Service | Port |
|---------|------|
| gateway-service | 8080 |
| user-service | 8081 |
| signalement-service | 8082 |
| actualite-service | 8083 |
| evenement-service | 8084 |
| ressource-service | 8085 |
| personnel-service | 8086 |
| projet-service | 8087 |

## Bases de données

Chaque service a SA PROPRE base de données MySQL:
- cityvoice_user
- cityvoice_signalement
- cityvoice_actualite
- cityvoice_evenement
- cityvoice_ressource
- cityvoice_personnel
- cityvoice_projet

## Git workflow

1. Créer une branche par feature: `feature/nom-feature`
2. Commits conventionnels: `feat: message`, `fix: message`
3. Pull Request vers `develop`
4. Review avant merge