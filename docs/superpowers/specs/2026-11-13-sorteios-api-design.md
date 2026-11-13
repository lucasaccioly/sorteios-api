# Sorteios API — Design

Data: 2026-11-13 · Status: aguardando aprovação

## Por quê
Segundo projeto do portfólio do Lucas (busca de vaga). O FinTrack mostra React + Node; este mostra
**Java + Spring Boot** num nível que aguenta entrevista técnica de back-end: regra de negócio de verdade,
segurança, concorrência, testes de integração, migrações, Docker, CI e uma **demo online clicável (Swagger)**.
Substitui os repositórios antigos `SistemaSorteio`, `SistemaSorteio1` e `SorteioProjeto` (CRUD simples de
faculdade, sem auth, sem testes, senha no código).

## Objetivos
- API REST de rifas/sorteios com demo pública no Swagger UI.
- Diferenciais que rendem conversa em entrevista: **sorteio auditável (commit-reveal)**, **compra de números
  segura sob concorrência** (com teste que prova), **agendamento** do sorteio.
- README que vende em 30 s: link da demo, credenciais de demo, diagrama de estados, decisões técnicas.

## Não-objetivos
- Pagamento real ou simulado (o preço do bilhete é informativo; a compra é imediata).
- Front-end (o Swagger é a interface da demo).
- Notificações por e-mail, multi-tenant, rate limiting (citado no README como próximo passo).

## Anti-objetivos (falha mesmo se funcionar)
- Dois participantes ficarem com o mesmo número.
- O vencedor poder ser escolhido/alterado por alguém depois de aberta a venda.
- Demo que trava ou fica vazia quando o recrutador abre (sem rifa aberta para testar).
- Qualquer menção à empresa onde o Lucas trabalha.

## Abordagens consideradas (hospedagem da demo)
| | Como | Trade-off |
|---|---|---|
| **A (escolhida pelo Lucas)** | Swagger no Render (free, Docker) + Postgres no Neon | Grátis, sem cartão; o serviço hiberna após 15 min e o 1º acesso leva ~1 min |
| B | Sem demo; README com prints/GIF + `docker compose up` | Menos trabalho, menos impacto |
| C | A + página React pública do sorteio | Mais trabalho, repete o que o FinTrack já mostra |

Postgres do Render free expira em 30 dias, por isso o banco fica no **Neon** (projeto/banco separado do FinTrack).

## Stack (minhas decisões)
- **Java 21** (LTS) · **Spring Boot 4.x** (3.5 saiu do suporte OSS em jun/2026) · Maven Wrapper.
- Spring Web MVC, Spring Data JPA, Spring Security + **OAuth2 Resource Server** (JWT HS256 via Nimbus,
  sem lib extra), Bean Validation, **Flyway**, Actuator (health para o Render), **springdoc-openapi 3.x**.
- Sem Lombok: DTOs como `record`; entidades JPA com getters/setters explícitos.
- Erros no padrão **RFC 9457 `ProblemDetail`** (nativo do Spring), mensagens em pt-BR.
- Testes: JUnit 5 + Spring Boot Test + MockMvc contra **PostgreSQL real embutido** (`io.zonky.test:embedded-postgres`)
  — esta máquina não tem Docker; o mesmo roda no CI. Testcontainers fica de fora pelo mesmo motivo.
- Docker multi-stage (build com JDK, runtime com JRE 21, `-XX:MaxRAMPercentage=75` para caber em 512 MB).
- CI: GitHub Actions `./mvnw verify`.
- Código e commits em inglês (`Raffle`, `Ticket`, `User`); mensagens da API e README em pt-BR.
- Pacote base `com.lucasaccioly.sorteios`; repo `lucasaccioly/sorteios-api`, licença MIT.

## Domínio

### Usuários e papéis
- Cadastro (nome, e-mail único, senha ≥ 8, BCrypt) e login → JWT Bearer (expira em 24 h).
  Swagger tem o botão **Authorize**.
- Papéis: `USER` (padrão) e `ADMIN`. Qualquer `USER` pode criar rifas (vira o **organizador** delas) e comprar
  números de rifas de outros. `ADMIN` pode cancelar a rifa de **qualquer organizador**, mas com a mesma regra
  de todos: **só sem vendas** — ninguém, nem o operador, aborta um sorteio que já tem participantes
  (anti-objetivo 2). *(minha decisão)*
- O primeiro `ADMIN` nasce de variáveis de ambiente opcionais `ADMIN_EMAIL`/`ADMIN_PASSWORD`: se definidas,
  na inicialização o usuário é criado (ou promovido) de forma idempotente. Não há admin público na demo;
  o papel é coberto pelos testes. *(minha decisão)*
- Autorização por **dono do recurso**: editar/publicar/cancelar só o organizador (ou ADMIN). Recurso de
  outro usuário que não é público → 404.

### Rifa (`Raffle`)
Campos: título, descrição, prêmio, preço do bilhete (centavos, inteiro), quantidade de números
(`1..N`, N entre 10 e 10.000), limite de números por participante, data/hora do sorteio (`drawAt`, UTC,
no futuro), status, organizador.

Estados:
```
DRAFT --publicar--> OPEN --drawAt chegou--> DRAWN (com vencedor)
  |                  |                  \-> CLOSED_NO_SALES (nenhum número vendido)
  \--cancelar--> CANCELLED <--cancelar--/  (só sem vendas)
```
- `DRAFT`: só o organizador vê e edita.
- `OPEN`: pública, aceita compras, não pode mais ser editada (exceto cancelar se não houver vendas).
- Ao **publicar**, o servidor gera uma semente aleatória de 32 bytes (`SecureRandom`) e publica só o
  `seedHash = SHA-256(seed)`. A semente fica guardada e nunca sai pela API antes do sorteio.

### Bilhetes (`Ticket`)
- `POST /api/raffles/{id}/tickets` com `{ "numbers": [7, 42] }` (escolhidos) **ou** `{ "quantity": 3 }`
  (aleatórios entre os livres). Máx. 50 por requisição.
- Só em rifa `OPEN` e antes de `drawAt`; organizador não compra da própria rifa.
- Respeita o limite por participante.
- **Concorrência:** `UNIQUE (raffle_id, number)` no banco + lock pessimista na linha da rifa
  (`SELECT … FOR UPDATE`) durante a compra, para o limite por participante e a escolha aleatória não
  sofrerem corrida. Número já vendido → `409` com a lista dos números indisponíveis; nada é gravado
  (tudo ou nada).
- Teste obrigatório: 20 threads tentando o mesmo número ao mesmo tempo → exatamente 1 sucesso, 19 × `409`.

### Sorteio auditável (commit-reveal)
- No sorteio, `ticketsDigest = SHA-256` (UTF-8, hex minúsculo) da lista `numero:userId` dos bilhetes vendidos,
  em ordem crescente de número, separados por vírgula — assim a prova cobre **quem** comprou cada número, e
  trocar o dono no banco depois do sorteio quebra a verificação. *(minha decisão)*
- `winnerIndex = uint64(primeiros 8 bytes de SHA-256(seed || ticketsDigest)) mod quantidadeVendida`;
  o vencedor é o número na posição `winnerIndex` da lista ordenada.
- `GET /api/raffles/{id}/proof` (público, após o sorteio): `seed`, `seedHash`, `ticketsDigest`,
  lista `numero:userId` (UUIDs, sem nome/e-mail), algoritmo descrito em texto, número vencedor e
  primeiro nome do vencedor.
- O README traz um script curto (`jshell` ou Python) que recalcula o vencedor a partir do `/proof`.
- Limitação assumida e documentada: quem opera o servidor conhece a semente (sem fonte externa de
  entropia — fora do escopo).

### Agendamento
- `@Scheduled` a cada 60 s sorteia rifas `OPEN` com `drawAt <= agora`.
- Idempotente: a transição usa `UPDATE … WHERE status = 'OPEN'` (ou lock) para nunca sortear duas vezes.
- **Catch-up ao acordar:** como o Render hiberna, roda também na inicialização; e `GET /api/raffles/{id}`
  de uma rifa vencida e ainda `OPEN` dispara o sorteio antes de responder.

## Endpoints (resumo)
| Método | Rota | Acesso |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | público |
| GET | `/api/me` | autenticado |
| GET | `/api/raffles?status=&q=&page=&size=&sort=` | público (só OPEN/DRAWN/CLOSED_NO_SALES/CANCELLED) |
| GET | `/api/raffles/{id}` | público (DRAFT só o dono) |
| POST/PUT | `/api/raffles`, `/api/raffles/{id}` | autenticado (dono, só DRAFT) |
| POST | `/api/raffles/{id}/publish`, `/api/raffles/{id}/cancel` | dono / ADMIN |
| GET | `/api/raffles/mine` | autenticado (organizador) |
| POST | `/api/raffles/{id}/tickets` | autenticado |
| GET | `/api/raffles/{id}/numbers` | público — mapa livre/vendido (sem dados pessoais) |
| GET | `/api/tickets/mine` | autenticado |
| GET | `/api/raffles/{id}/proof` | público, após o sorteio |

Paginação com `Pageable` (size máx. 100). Swagger UI na raiz (`/` redireciona para `/swagger-ui.html`).

## Demo online
- Perfil `demo`: na inicialização garante (idempotente) usuários de demo com senha pública no README
  (`organizador@demo.dev`, `participante@demo.dev`) e rifas em cada estado — inclusive uma `DRAWN`
  com prova verificável e **sempre ao menos uma `OPEN` com `drawAt` no futuro** (recria se faltar).
  Como o Render reinicia ao acordar, a demo se auto-repara.
- **Manutenção contínua da demo** *(minha decisão)*: além da inicialização, um job a cada 10 min (só no perfil
  `demo`) garante que exista ao menos uma rifa `OPEN` do organizador de demo com ≥ 10 números livres e
  `drawAt` ≥ 1 h no futuro; se não houver, cria uma nova com `drawAt` = agora + 24 h. Cobre rifa esgotada,
  cancelada ou já sorteada. O README incentiva o visitante a **criar a própria conta** (2 cliques no Swagger);
  a conta `participante@demo.dev` é atalho, e se o limite dela esgotar numa rifa, a próxima rifa criada pela
  manutenção resolve.
- Health check do Render em `/actuator/health`.
- Variáveis: `DATABASE_URL` (JDBC do Neon), `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`,
  `SPRING_PROFILES_ACTIVE=demo`.
- README avisa: "primeiro acesso pode levar ~1 min (plano gratuito hiberna)".

## Fica a critério do builder
- Nomes de classes/pacotes internos (organizar por feature: `auth`, `raffle`, `ticket`, `draw`).
- Formato exato dos DTOs e exemplos do OpenAPI.
- Texto e conteúdo das rifas de demo.

## Depois de publicado (fora deste projeto)
- Atualizar o README do perfil (linha "API de Sorteios" com link do repo e da demo).
- Perguntar ao Lucas se apaga/arquiva `SistemaSorteio`, `SistemaSorteio1` e `SorteioProjeto`.
