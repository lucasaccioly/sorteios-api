create table raffles (
    id                 uuid primary key,
    organizer_id       uuid         not null references users (id),
    title              varchar(120) not null,
    description        varchar(2000),
    prize              varchar(200) not null,
    ticket_price_cents bigint       not null check (ticket_price_cents >= 0),
    total_numbers      int          not null check (total_numbers between 10 and 10000),
    max_per_user       int          not null check (max_per_user >= 1),
    draw_at            timestamptz  not null,
    status             varchar(20)  not null,
    seed               bytea,
    seed_hash          varchar(64),
    tickets_digest     varchar(64),
    winning_number     int,
    winner_id          uuid references users (id),
    drawn_at           timestamptz,
    created_at         timestamptz  not null,
    version            bigint       not null default 0
);

create index idx_raffles_status_draw_at on raffles (status, draw_at);
create index idx_raffles_organizer on raffles (organizer_id);
