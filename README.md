# Selldown Service

Standalone Spring Boot WebFlux service within this repository.

Build:
- mvn -f selldown/pom.xml clean package

Run:
- mvn -f selldown/pom.xml spring-boot:run

Configuration:
- Edit selldown/src/main/resources/application.yml for DB and Liquibase

Liquibase:
- Master: db/changelog/db.changelog-master.yaml
- Example: db/changelog/db.changelog-001-init.yaml
