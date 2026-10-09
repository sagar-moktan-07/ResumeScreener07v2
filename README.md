# Resume Screener

A Spring Boot + PostgreSQL recruitment management system for storing vacancies, uploading candidate resumes, and preparing the foundation for an AI-assisted screening workflow.

## Stack

- Java 17
- Maven
- Spring Boot 3.3.5
- Spring Security
- Spring Data JPA
- PostgreSQL
- Thymeleaf
- PDFBox

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 15+ with `pgvector` available

## Local database setup

1. Create a PostgreSQL database named `resumedb`.
2. Update the PostgreSQL connection in `src/main/resources/application.properties` if needed.
3. Ensure the `vector` extension is available in PostgreSQL.
4. This project is configured for the local Docker instance on port `5433` to avoid conflicts with any existing PostgreSQL on port `5432`.

Example SQL:

```sql
CREATE DATABASE resumedb;
\c resumedb;
CREATE EXTENSION IF NOT EXISTS vector;
```

## Run the app

```bash
mvn spring-boot:run
```

Then open:

- http://localhost:8080/login

Default admin login:

- username: `Sagar`
- password: `moktan07`

## Notes

This project intentionally focuses on the Java + PostgreSQL foundation first and leaves the FastAPI AI/service layer for a separate implementation step.
