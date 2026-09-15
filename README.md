# FalaPet Backend

Backend FalaPet em monólito modular, com Java 21, Spring Boot, PostgreSQL, Flyway e Spring Modulith. Os Épicos 0 e 1 estabelecem a fundação e o contrato HTTP transversal. O Épico 2 adiciona autenticação local e sessões opacas no módulo `auth`; o Épico 3 implementa conta do tutor e solicitação técnica LGPD no módulo `user`.

## Pré-requisitos

- JDK 21 (`java -version`)
- Docker Desktop ou Docker Engine com Compose
- Portas 5432 (PostgreSQL) e 8080 (aplicação), ou alternativas configuradas no `.env`

O Maven Wrapper 3.9.16 está versionado, portanto não é preciso instalar Maven.

## Testes e build

PowerShell:

```powershell
.\mvnw.cmd clean verify
```

Shell Unix:

```bash
./mvnw clean verify
```

Os testes de integração criam um PostgreSQL 17.6 vazio com Testcontainers, executam todas as migrations e validam persistência e concorrência reais. Docker deve estar disponível; PostgreSQL local não é necessário. H2 não é dependência do projeto.

## Ambiente local

Copie `.env.example` para `.env` e ajuste somente valores locais. `.env` é ignorado pelo Git.

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
docker compose ps
```

```bash
cp .env.example .env
docker compose up -d postgres
docker compose ps
```

Para encerrar preservando dados, use `docker compose down`. Para recriar somente o ambiente local e apagar o volume local do PostgreSQL, use `docker compose down -v` e depois `docker compose up -d postgres`.

## Executar a aplicação

Com o PostgreSQL local ativo:

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
.\mvnw.cmd spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Verificações disponíveis:

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8080/api/v1/auth/capabilities
```

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/v1/auth/capabilities
```

O único endpoint Actuator exposto é `GET /actuator/health`. As rotas públicas de autenticação são capacidades, cadastro, login, refresh, Google provisório e recuperação. As demais rotas registradas exigem `Authorization: Bearer <accessToken>`. Capacidades anunciam `passwordLogin=true`, `googleLogin=false` e `passwordRecovery=false` enquanto o provedor de entrega de recuperação não for aprovado/configurado.

## Container da aplicação

O Dockerfile empacota o JAR previamente verificado:

```powershell
.\mvnw.cmd clean verify
docker compose --profile app up --build -d
```

```bash
./mvnw clean verify
docker compose --profile app up --build -d
```

Consulte os endpoints acima e encerre com `docker compose --profile app down`.

## Configuração

| Variável | Obrigatória | Uso |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Sim fora dos testes | `dev` ou `prod` |
| `DB_URL` | Em produção | URL JDBC PostgreSQL |
| `DB_USERNAME` | Em produção | Usuário PostgreSQL |
| `DB_PASSWORD` | Em produção | Senha PostgreSQL |
| `CURSOR_SIGNING_KEY` | Em produção | Segredo com ao menos 32 bytes para assinar cursores opacos |
| `AUTH_TOKEN_HASH_KEY` | Em produção | Chave com ao menos 32 bytes para HMAC dos tokens opacos |
| `AUTH_IDEMPOTENCY_ENCRYPTION_KEY` | Em produção | Chave de exatamente 32 bytes para cifrar replay de cadastro |
| `AUTH_ACCESS_TTL` | Em produção | Duração ISO-8601 do access token, até 30 min |
| `AUTH_REFRESH_TTL` | Em produção | Duração ISO-8601 do refresh token, maior que access e até 90 dias |
| `AUTH_RECOVERY_TTL` | Em produção | Duração ISO-8601 do token de recuperação, até 1 hora |
| `AUTH_ARGON2_SALT_LENGTH`, `AUTH_ARGON2_HASH_LENGTH`, `AUTH_ARGON2_PARALLELISM`, `AUTH_ARGON2_MEMORY_KIB`, `AUTH_ARGON2_ITERATIONS` | Não | Parâmetros Argon2id; mínimos 16, 32, 1, 19456 KiB, 2 |
| `AUTH_RATE_WINDOW`, `AUTH_RATE_MAX_ATTEMPTS` | Não | Janela e tentativas por IP/rota; padrões 1 min e 10 |
| `AUTH_COMPROMISED_PASSWORD_SHA256` | Não | Hashes SHA-256 hexadecimais separados por vírgula para política local configurada |
| `MAX_JSON_PAYLOAD_BYTES` | Não | Limite técnico JSON; padrão 1 MiB |
| `DB_MAX_POOL_SIZE` | Não | Pool de produção; padrão 10 |
| `LOG_LEVEL` | Não | Nível raiz de produção; padrão `INFO` |

Os defaults de banco e assinatura presentes no perfil `dev` são exclusivamente locais. Produção exige variáveis externas e não contém credenciais padrão.

## Contrato HTTP transversal

- Base da API: `/api/v1`.
- JSON: `application/json; charset=utf-8`.
- IDs de domínio: UUID textual.
- Instantes: RFC 3339 em UTC, por exemplo `2026-09-14T18:00:00Z`.
- Sucesso com corpo: `{ "data": ... }`.
- Erro: `{ "error": { "code", "message", "retryable", "fieldErrors?", "details?" } }`.
- Rotas inexistentes retornam JSON com `404 RESOURCE_NOT_FOUND`; não há página HTML ou detalhe interno.

O catálogo tipado contém os códigos aprovados na versão 1.1.0: `VALIDATION_ERROR`, `INVALID_CURSOR`, `INVALID_STATE`, `RECOVERY_TOKEN_INVALID`, `INVALID_CREDENTIALS`, `SESSION_INVALID`, `TOKEN_EXPIRED`, `FORBIDDEN`, `CAPABILITY_DISABLED`, `GOOGLE_LOGIN_UNAVAILABLE`, `RESOURCE_NOT_FOUND`, `VERSION_CONFLICT`, `DUPLICATE_RESOURCE`, `ACTIVE_RESOURCE_CONFLICT`, `EMAIL_ALREADY_REGISTERED`, `EVENT_ID_CONFLICT`, `UPLOAD_NOT_FOUND`, `UPLOAD_EXPIRED`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `DOMAIN_RULE_VIOLATION`, `UPLOAD_VALIDATION_FAILED`, `RATE_LIMITED`, `INTERNAL_ERROR`, `CAPABILITY_NOT_AVAILABLE` e `SERVICE_UNAVAILABLE`.

O tratamento central cobre validação, JSON inválido, parâmetros obrigatórios ou de tipo incorreto, rota inexistente, método não permitido, mídia não suportada, payload excessivo, conflito técnico e erro inesperado. Respostas não incluem stack trace, classe Java, SQL, segredo nem corpo recebido.

### Correlação

Toda resposta inclui `X-Request-Id`. Valores enviados são aceitos quando têm de 1 a 128 caracteres e seguem `[A-Za-z0-9][A-Za-z0-9._:-]*`; valores ausentes, excessivos ou inválidos são substituídos por UUID. O identificador fica no MDC durante a requisição e é removido ao final.

Cada conclusão de request registra `requestId`, método, padrão de rota (sem query string), status, latência e código técnico de erro. Corpos, `Authorization`, `Idempotency-Key`, tokens e dados privados não são registrados.

### Idempotência

A infraestrutura não é aplicada automaticamente a endpoints. Ela oferece aquisição atômica, indicação de processamento concorrente, replay de resposta concluída e detecção de reutilização com fingerprint de payload divergente. JSON semanticamente equivalente quanto a espaços e ordem de propriedades produz o mesmo SHA-256.

A migration `V2__create_http_idempotency_record.sql` usa chave única por operação, sujeito e chave. Sujeito e `Idempotency-Key` são persistidos somente como SHA-256; a resposta armazenada é limitada a 1 MiB. O chamador deve fornecer um escopo técnico estável e nunca persistir mídia, token, senha ou conteúdo privado como resposta idempotente.

O contrato v1.1.0 não define formato ou tamanho geral de `Idempotency-Key`, código público genérico para chave reutilizada com payload divergente, nem prazo de retenção. Por isso esta fundação não inventa esses itens: a divergência permanece um sinal interno a ser mapeado pelo endpoint futuro para um código já aprovado, e não existe limpeza automática. O índice por `created_at` prepara uma política de retenção posterior.

### Paginação por cursor

`CursorPageRequest` aplica o padrão 30 e o intervalo contratual de 1 a 100. `PageData` produz `items` e `page` com `nextCursor` e `hasMore`. O cursor é versionado, determinístico, Base64 URL-safe e autenticado com HMAC-SHA-256; alteração ou formato inválido gera `INVALID_CURSOR`.

Cada endpoint futuro deve definir sua ordenação estável, escopo, fingerprint de filtros e componentes de posição. Dados sensíveis não podem compor o cursor. Offset não é usado como substituto.

## Autenticação e sessão

`POST /api/v1/auth/register` exige `Idempotency-Key` e retorna tutor e sessão com `201`. `POST /api/v1/auth/login` retorna a mesma estrutura com `200`; falhas de credencial usam `401 INVALID_CREDENTIALS` uniformemente. `GET /api/v1/auth/session` exige Bearer e retorna apenas tutor e `sessionId`. `POST /api/v1/auth/refresh` é o bootstrap da sessão e entrega novo par opaco. O token anterior é consumido em transação; seu reuso revoga a família e a sessão. `POST /api/v1/auth/logout` exige Bearer e chave idempotente, revoga a sessão e retorna `204`, inclusive no replay da mesma solicitação. O mobile conserva o par apenas em armazenamento seguro e não interpreta o conteúdo dos tokens.

`POST /api/v1/auth/password-recovery/request` exige chave idempotente e responde `202 {"data":{"accepted":true}}` sem indicar a existência da conta. `POST /api/v1/auth/password-recovery/confirm` valida token de uso único e nova senha, retorna `204` e revoga as sessões do tutor. Não existe provedor de entrega aprovado/configurado: a solicitação não gera nem divulga tokens, e `passwordRecovery=false` permanece publicado. A porta de entrega permite integrar um provedor quando a decisão externa estiver disponível. `POST /api/v1/auth/google` retorna `403 GOOGLE_LOGIN_UNAVAILABLE`; não há audiência, emissor e configuração Google aprovados para habilitar a capacidade.

Cadastro, login, refresh e recuperação usam buckets PostgreSQL por IP e rota. Esgotamento retorna `429 RATE_LIMITED` com `Retry-After`; uma janela encerrada volta a aceitar pedidos. As respostas de autenticação usam `Cache-Control: no-store`.

## Conta do tutor e solicitação LGPD

As três rotas exigem `Authorization: Bearer <accessToken>` opaco; a identidade vem somente da sessão autenticada. `GET /api/v1/users/me` retorna `200 {"data":{"tutor":Tutor}}`, com exatamente `id`, `name`, `email`, `createdAt` e `version`. Credenciais, hashes, tokens e sessões não fazem parte do perfil.

`PATCH /api/v1/users/me` aceita apenas `{ "name": "Paula Oliveira" }` e exige `If-Match: "<version>"`, com aspas. O nome é aparado nas bordas e deve ter de 1 a 120 caracteres visíveis; e-mail e método de autenticação não podem ser alterados por essa rota. Uma atualização válida retorna `200 {"data":{"tutor":Tutor}}` com a versão incrementada. O banco atualiza condicionalmente pela versão informada; uma versão desatualizada retorna `409 VERSION_CONFLICT` com o próprio Tutor atual em `error.details.tutor`, sem last-write-wins. Ausência ou formato inválido de `If-Match` retorna `400 VALIDATION_ERROR`.

`POST /api/v1/users/me/deletion-requests` exige `Idempotency-Key` e retorna `202` com `requestId`, `status: "PENDING"`, `requestedAt` e `effectiveAt: null`. A mesma chave e requisição reproduzem a resposta; reutilizar a chave com conteúdo diferente retorna `409 DUPLICATE_RESOURCE`. Uma solicitação já `PENDING` é reproduzida também para nova chave, evitando múltiplos registros técnicos para o mesmo tutor. A chave e o identificador do tutor são guardados como hash no registro transversal de idempotência. `PENDING` significa apenas que o pedido foi registrado: os dados não foram excluídos, anonimizados ou bloqueados, e as sessões não são revogadas.

Confirmação, cancelamento, prazo de retenção, análise jurídica, execução física, exportação e fluxo administrativo permanecem reservados. O Épico 4 cuidará de pets e fotos após a definição de seus próprios limites; nenhum desses recursos é criado pelo módulo `user`.

Para validar este épico, execute os comandos de `clean verify` acima com Docker disponível. Os testes criam PostgreSQL vazio, aplicam as migrations Flyway e exercitam `If-Match`, concorrência, idempotência, ownership e limites Spring Modulith. Não há variável de ambiente nova nem provedor externo necessário para as três rotas.

## Estrutura modular e migrations

Os módulos de topo continuam `auth`, `user`, `pet`, `device`, `central`, `button`, `event`, `audio`, `context`, `training`, `sync`, `insight` e `shared`. Spring Modulith verifica limites e ciclos. Não existem pacotes globais `controller`, `service` ou `repository`.

`shared.contract` contém apenas wire format, cursor e portas de idempotência; implementações web e JDBC transversais ficam em `shared.infrastructure`. `auth` reúne API, aplicação, domínio e infraestrutura de autenticação, inclusive o filtro Bearer e os adapters JDBC/Argon2id. A API interna `auth.TutorIdentity` expõe somente o UUID do tutor ao módulo `user`; este módulo contém controllers, casos de uso, domínio e adapters próprios para perfil e solicitação LGPD.

As migrations são imutáveis e ficam em `src/main/resources/db/migration`:

- `V1__create_application_metadata.sql`: metadado técnico inicial.
- `V2__create_http_idempotency_record.sql`: registro técnico mínimo de idempotência.
- `V3__create_auth_identity_and_sessions.sql`: identidade local, sessões, tokens por hash e rate limiting.
- `V4__create_auth_registration_idempotency.sql`: replay cifrado de cadastro.
- `V5__create_auth_logout_idempotency.sql`: replay técnico do logout.
- `V6__create_user_deletion_request.sql`: registro técnico único `PENDING` da solicitação LGPD, vinculado ao usuário.

O schema é criado exclusivamente pelo Flyway e validado pelo Hibernate (`ddl-auto=validate`). JDBC e serialização usam UTC.

## Fora do escopo

Pets, aparelhos, Central, botões, eventos, áudio, fotos, contextos, treinamento, sincronização, insights, object storage, worker ML e ESP32 continuam fora desta entrega. Google Login e envio de recuperação dependem das configurações e decisões externas indicadas acima. A política jurídica e operacional para executar uma exclusão de conta ainda não está definida.
