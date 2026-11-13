create table users (
    id            uuid primary key,
    name          varchar(100) not null,
    email         varchar(254) not null unique,
    password_hash varchar(100) not null,
    role          varchar(10)  not null,
    created_at    timestamptz  not null
);
