CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS admin_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(150),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_credentials (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    candidate_code VARCHAR(20) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vacancy_post (
    id BIGSERIAL PRIMARY KEY,
    vacancy_code VARCHAR(20),
    job_title VARCHAR(200) NOT NULL,
    department VARCHAR(150),
    job_description TEXT,
    required_skills TEXT,
    preferred_skills TEXT,
    qualifications TEXT,
    education_requirements TEXT,
    experience_requirements TEXT,
    responsibilities TEXT,
    other_requirements TEXT,
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS candidate_resume (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    candidate_code VARCHAR(20),
    file_name VARCHAR(255),
    full_name VARCHAR(200),
    email VARCHAR(200),
    phone VARCHAR(50),
    raw_text TEXT,
    skills TEXT,
    qualifications TEXT,
    experience TEXT,
    status VARCHAR(80) DEFAULT 'uploaded',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vacancy_candidate (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    match_score DOUBLE PRECISION,
    status VARCHAR(80) DEFAULT 'pending',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS candidate_embedding (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    embedding vector(384),
    source TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vacancy_embedding (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT NOT NULL,
    embedding vector(384),
    source TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS screening_run (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT,
    run_name VARCHAR(200),
    status VARCHAR(80) DEFAULT 'started',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    summary TEXT
);

CREATE TABLE IF NOT EXISTS screening_result (
    id BIGSERIAL PRIMARY KEY,
    screening_run_id BIGINT,
    candidate_id BIGINT,
    vacancy_id BIGINT,
    overall_score DOUBLE PRECISION,
    reasoning TEXT,
    status VARCHAR(80) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS semifinalized_candidate (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT,
    candidate_id BIGINT,
    score DOUBLE PRECISION,
    status VARCHAR(80) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS finalized_candidate (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT,
    candidate_id BIGINT,
    final_score DOUBLE PRECISION,
    decision VARCHAR(80),
    conclusion TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vacancy_application (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    vacancy_id BIGINT NOT NULL,
    vacancy_code VARCHAR(20),
    candidate_resume_id BIGINT NOT NULL,
    candidate_code VARCHAR(20),
    status VARCHAR(80) DEFAULT 'applied',
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, vacancy_id)
);
