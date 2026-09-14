# FalaPet Backend

Fundação executável do backend FalaPet (Épico 0), construída como monólito modular com Java 21, Spring Boot, PostgreSQL, Flyway e Spring Modulith. A API de domínio futura será versionada sob `/api/v1`; este épico não implementa endpoints de domínio nem autenticação contratual.

## Pré-requisitos

- JDK 21 (`java -version`)
- Docker Desktop ou Docker Engine com Compose
- Portas locais 5432 (PostgreSQL) e 8080 (aplicação), ou valores alternativos no `.env`

Não é necessário instalar Maven: o Maven Wrapper 3.9.16 está versionado.

## Testes e build

PowerShell:

```powershell
.\mvnw.cmd clean verify
```

Shell Unix:

```bash
./mvnw clean verify
```

Os testes de integração iniciam PostgreSQL real com Testcontainers; não exigem PostgreSQL instalado, mas requerem Docker disponível. H2 não é dependência do projeto.

## PostgreSQL local

Opcionalmente copie `.env.example` para `.env` e ajuste apenas valores locais. `.env` é ignorado pelo Git.

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

Para encerrar sem apagar dados:

```bash
docker compose down
```

Para recriar **somente o banco local**, removendo seu volume e todos os dados locais:

```bash
docker compose down -v
docker compose up -d postgres
```

## Executar a aplicação

Com o PostgreSQL local ativo, use o perfil de desenvolvimento:

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
.\mvnw.cmd spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Verifique o único endpoint operacional público:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

```bash
curl -i http://localhost:8080/actuator/health
```

O health check responde com `X-Request-Id`. Um identificador válido enviado nesse header é propagado; valor ausente, inválido ou maior que 128 caracteres é substituído por UUID. O valor fica no MDC durante a requisição e é removido ao final.

## Container da aplicação

O Dockerfile empacota o JAR previamente verificado, evitando repetir ou ocultar testes durante o build da imagem:

```powershell
.\mvnw.cmd clean verify
docker compose --profile app up --build -d
```

```bash
./mvnw clean verify
docker compose --profile app up --build -d
```

Depois, consulte `http://localhost:8080/actuator/health` e encerre com `docker compose --profile app down`.

## Configuração

| Variável | Obrigatória | Uso |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Sim fora dos testes | `dev` ou `prod` |
| `DB_URL` | Em produção | URL JDBC PostgreSQL |
| `DB_USERNAME` | Em produção | Usuário PostgreSQL |
| `DB_PASSWORD` | Em produção | Senha PostgreSQL |
| `DB_MAX_POOL_SIZE` | Não | Pool de produção; padrão 10 |
| `LOG_LEVEL` | Não | Nível raiz de produção; padrão `INFO` |

O perfil `dev` possui apenas defaults locais coerentes com o Compose. Produção não contém credenciais padrão. Configurações futuras sensíveis devem seguir o mesmo padrão de variáveis de ambiente ou secret manager do ambiente, quando este for decidido.

Instantes são serializados em UTC, o JDBC usa UTC, o schema é validado pelo Hibernate (`ddl-auto=validate`) e criado exclusivamente pelo Flyway. Shutdown é gracioso. Em produção não há SQL verboso, detalhes sensíveis do Actuator ou stack traces em respostas.

## Estrutura modular

O package base é `com.falapet`, conforme a arquitetura oficial v1.1. Os módulos de topo são `auth`, `user`, `pet`, `device`, `central`, `button`, `event`, `audio`, `context`, `training`, `sync`, `insight` e `shared`. Cada limite é declarado com Spring Modulith e verificado automaticamente; módulos funcionais poderão evoluir internamente para `api`, `application`, `domain` e `infrastructure` quando houver código real.

Não existem pacotes globais `controller`, `service` ou `repository`. `shared` contém, por enquanto, somente infraestrutura transversal de segurança e correlação HTTP.

## Migrations

As migrations ficam em `src/main/resources/db/migration`. `V1__create_application_metadata.sql` cria apenas uma estrutura técnica mínima e comprova bootstrap de banco vazio. Uma migration aplicada é imutável: toda mudança de schema futura deve ser uma nova migration versionada. IDs de domínio futuros usarão UUID, mas nenhuma entidade de domínio foi antecipada neste épico.

## Limites do Épico 0

Ainda **não estão implementados**: autenticação real, usuários, pets, aparelhos, Central, botões, eventos, áudio/mídia, contextos, treinamento, sincronização, insights, object storage, worker ML, ESP32, tokens, login Google, recuperação de senha e rate limiting.

A segurança atual é provisória e explícita: somente o health check é público e todas as outras requisições são negadas. Não existe usuário temporário nem credencial gerada. Essa configuração será substituída ou ampliada no épico de autenticação, sem anunciar capacidades contratuais antes de sua implementação.
