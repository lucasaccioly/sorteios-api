create table tickets (
    id           uuid primary key,
    raffle_id    uuid        not null references raffles (id),
    user_id      uuid        not null references users (id),
    number       int         not null,
    purchased_at timestamptz not null,
    constraint uq_tickets_raffle_number unique (raffle_id, number)
);

create index idx_tickets_raffle_user on tickets (raffle_id, user_id);
create index idx_tickets_user on tickets (user_id);
