# CityVoice — Backend

Plateforme de civic engagement basée sur une architecture microservices.

## Stack
- Spring Boot 3 · Spring Cloud Gateway · Eureka
- JWT Auth · WebSocket (STOMP) · PostgreSQL
- Docker · Kubernetes (OpenStack) · Jenkins CI/CD

## Services
| Service | Port | Rôle |
|---------|------|------|
| gateway | 8080 | API Gateway |
| user-service | 8081 | Auth, profil, gamification |
| complaint-service | 8082 | Signalements citoyens |
| notification-service | 8083 | WebSocket, alertes |

## Run local
```bash
docker-compose up --build
```

## Live
https://cityvoice.website