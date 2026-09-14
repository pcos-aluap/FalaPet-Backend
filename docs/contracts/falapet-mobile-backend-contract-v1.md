# FalaPet — Contrato Mobile–Backend

**Versão:** 1.1.0  
**Data de aprovação:** 2026-09-14  
**Status:** APROVADO como contrato vinculante para implementação coordenada; operações marcadas como `RESERVADA` não estão autorizadas para implementação  
**Consumidores:** aplicativo mobile FalaPet e backend FalaPet  
**Fontes:** Documento Oficial de Requisitos v1.1, Documento Oficial de Arquitetura v1.1 e Contrato de Autenticação Mobile–Backend v1.0.0

Este documento substitui integralmente o contrato isolado `falapet-authentication-contract-v1.md`. Mobile e backend devem adotar esta versão unificada como única fonte contratual.

## 1. Escopo e precedência

Este contrato define a fronteira HTTP entre o aplicativo do tutor e o backend. Abrange autenticação, conta, preferências do aparelho, pets, botões, áudio, eventos, histórico, contextos, sessões de treinamento, sincronização, Home e insights.

Os documentos oficiais v1.1 definem o escopo do produto e prevalecem em caso de conflito. Este contrato define detalhes de interoperabilidade. Mudanças incompatíveis exigem nova versão principal.

O aplicativo possui exclusivamente **Tutor Mode**. Não existem Hub Mode, `HubNavigator`, `DeviceSession` ou ativação do celular como Hub. ESP32, RF, BLE e firmware usam contratos próprios; somente seus reflexos sincronizados aparecem aqui.

## 2. Itens deliberadamente reservados

Não estão autorizados nesta versão:

- pareamento, credenciais e escopos definitivos da ESP32;
- comandos BLE/RF e protocolo de firmware;
- Wi-Fi fallback da ESP32;
- formato/codificação final do áudio para a Central física;
- OTA da ESP32;
- política definitiva de retenção local ou central;
- mecanismo comercial de autenticação e mitigação de replay no RF;
- exclusão física imediata de dados sujeitos a retenção legal;
- endpoints administrativos do pipeline de ML;
- pagamentos, rotinas, diagnóstico ou planos de treinamento.

Uma operação reservada deve retornar `501 CAPABILITY_NOT_AVAILABLE` quando uma rota provisória existir. O mobile não deve apresentar a capacidade como disponível.

## 3. Convenções HTTP

- Base URL: `https://<ambiente>/api/v1`.
- HTTPS é obrigatório fora de testes locais controlados.
- JSON usa `application/json; charset=utf-8`.
- Instantes usam RFC 3339 em UTC.
- Datas civis usam `YYYY-MM-DD`.
- IDs de domínio são UUIDs textuais, exceto identificadores do protocolo físico.
- Toda requisição envia `X-Request-Id`; a resposta o devolve.
- Operações mutáveis aceitam `Idempotency-Key`; ele é obrigatório onde indicado.
- Rotas autenticadas exigem `Authorization: Bearer <accessToken>`.
- Respostas de sucesso com corpo usam `{ "data": ... }`.
- O backend pode adicionar campos opcionais; clientes ignoram campos desconhecidos.
- Valores monetários não fazem parte desta versão.

### 3.1 Paginação

Listagens usam cursor opaco:

```json
{
  "data": {
    "items": [],
    "page": { "nextCursor": null, "hasMore": false }
  }
}
```

Parâmetros: `cursor` opcional e `limit` entre 1 e 100, padrão 30. Cursores não devem ser interpretados pelo mobile.

### 3.2 Concorrência

Recursos editáveis possuem `version` inteiro crescente. `PATCH` e `PUT` enviam `If-Match: "<version>"`. Conflito retorna `409 VERSION_CONFLICT` com o recurso atual quando sua exposição for segura.

### 3.3 Exclusão lógica

Pets, botões e tipos de contexto que possuam histórico são desativados, nunca apagados destrutivamente. Listagens retornam ativos por padrão e aceitam `status=ACTIVE|INACTIVE|ALL` quando indicado.

## 4. Erros

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Não foi possível concluir a solicitação.",
    "retryable": false,
    "fieldErrors": [{ "field": "name", "code": "REQUIRED" }],
    "details": null
  }
}
```

`fieldErrors` e `details` são opcionais. A lógica do mobile depende de `code`, não de `message`.

| HTTP | Códigos principais |
| --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_CURSOR`, `INVALID_STATE` |
| 401 | `INVALID_CREDENTIALS`, `SESSION_INVALID`, `TOKEN_EXPIRED` |
| 403 | `FORBIDDEN`, `CAPABILITY_DISABLED` |
| 404 | `RESOURCE_NOT_FOUND` |
| 409 | `VERSION_CONFLICT`, `DUPLICATE_RESOURCE`, `ACTIVE_RESOURCE_CONFLICT` |
| 410 | `UPLOAD_EXPIRED` |
| 413 | `PAYLOAD_TOO_LARGE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 422 | `DOMAIN_RULE_VIOLATION`, `UPLOAD_VALIDATION_FAILED` |
| 429 | `RATE_LIMITED` com `Retry-After` |
| 500 | `INTERNAL_ERROR` |
| 501 | `CAPABILITY_NOT_AVAILABLE` |
| 503 | `SERVICE_UNAVAILABLE` |

Erros `5xx`, `429` e falhas de rede podem ser repetidos conforme `retryable` e backoff. Erros de domínio não devem ser repetidos automaticamente.

## 5. Tipos compartilhados

### 5.1 Tutor

```json
{
  "id": "b758d342-8af1-4f18-9c71-1effaf6df106",
  "name": "Paula Oliveira",
  "email": "paula@example.com",
  "createdAt": "2026-09-13T18:00:00Z",
  "version": 1
}
```

### 5.2 Pet

```json
{
  "id": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
  "name": "Marley",
  "species": "DOG",
  "photo": null,
  "birthDate": "2022-05-10",
  "sex": "MALE",
  "status": "ACTIVE",
  "createdAt": "2026-09-13T18:00:00Z",
  "updatedAt": "2026-09-13T18:00:00Z",
  "version": 1
}
```

`name` e `species` são obrigatórios. `photo`, `birthDate` e `sex` são opcionais. Valores iniciais de `species`: `DOG`, `CAT`, `OTHER`; `sex`: `FEMALE`, `MALE`, `UNKNOWN`. `UNKNOWN` expressa informação desconhecida, não ausência do campo.

Quando `photo` não for nulo, seu schema completo é:

```json
{
  "id": "b3a58c0f-ed8e-4a60-840f-dbd6ee06786a",
  "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
  "mediaType": "image/jpeg",
  "sizeBytes": 248731,
  "width": 1024,
  "height": 1024,
  "sha256": "64-caracteres-hexadecimais-minusculos",
  "download": {
    "url": "https://url-temporaria-de-download",
    "expiresAt": "2026-09-14T18:15:00Z"
  },
  "createdAt": "2026-09-14T18:00:00Z",
  "version": 1
}
```

`download.url` é temporária, deve usar HTTPS e não pode ser persistida como identidade da foto. O mobile usa `photo.id` como identidade estável e solicita novamente o Pet quando a URL expirar.

### 5.3 Button

```json
{
  "id": "5a242aa8-b340-4f68-b8f1-48f8381a7ea6",
  "name": "Comida",
  "description": "Pedido relacionado à alimentação",
  "status": "ACTIVE",
  "activeAudio": null,
  "activeBinding": null,
  "createdAt": "2026-09-13T18:00:00Z",
  "updatedAt": "2026-09-13T18:00:00Z",
  "version": 1
}
```

Button é lógico, não possui `petId` obrigatório e preserva sua identidade quando áudio ou hardware muda.

### 5.4 AudioAsset

```json
{
  "id": "85a4d669-e169-4466-be75-d22113467569",
  "buttonId": "5a242aa8-b340-4f68-b8f1-48f8381a7ea6",
  "status": "ACTIVE",
  "mediaType": "audio/mp4",
  "sizeBytes": 241392,
  "sha256": "hexadecimal-em-minusculas",
  "downloadUrl": "https://url-temporaria",
  "downloadUrlExpiresAt": "2026-09-13T18:10:00Z",
  "version": 1
}
```

O formato aceito deve ser descoberto por capacidade; o exemplo não fixa o formato final. Um Button possui no máximo um áudio ativo.

### 5.5 ButtonEvent bruto

```json
{
  "id": "ad1ff15b-5833-4297-b34f-512dc009f46b",
  "buttonId": "5a242aa8-b340-4f68-b8f1-48f8381a7ea6",
  "esp32DeviceId": "0d39ce33-c7bd-465c-8362-c56f59176ef1",
  "physicalButtonId": 42,
  "espSessionId": 11802,
  "sequence": 991,
  "espUptimeMs": 704040,
  "occurredAt": "2026-09-13T18:00:00Z",
  "receivedAt": "2026-09-13T18:00:00.200Z",
  "timeQuality": "ESTIMATED_FROM_MOBILE",
  "transport": "BLE",
  "purpose": "BEHAVIORAL",
  "trainingSessionId": null,
  "createdAt": "2026-09-13T18:00:01Z"
}
```

`timeQuality`: `ESTIMATED_FROM_MOBILE` ou `RECEIVED_TIME_ONLY`. `transport`: `BLE` ou `WIFI`. `purpose`: `BEHAVIORAL` ou `TEST`.

ButtonEvent é imutável. Não contém pet, contexto, classificação humana, intenção, significado, resultado/latência/status de playback ou expiração de playback.

### 5.6 Metadados humanos do evento

```json
{
  "petAttribution": {
    "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
    "source": "AUTOMATIC_SINGLE_ACTIVE_PET",
    "updatedAt": "2026-09-13T18:00:02Z",
    "version": 1
  },
  "classification": {
    "value": "UNCERTAIN",
    "updatedAt": "2026-09-13T18:02:00Z",
    "version": 1
  },
  "context": {
    "contextTypeId": "21e83b96-d1da-48ae-9737-e9426e39baa1",
    "contextTypeNameSnapshot": "Perto da refeição",
    "note": "Estava próximo do horário habitual.",
    "updatedAt": "2026-09-13T18:03:00Z",
    "version": 1
  }
}
```

`source`: `AUTOMATIC_SINGLE_ACTIVE_PET` ou `TUTOR`. Classificação: `APPARENTLY_INTENTIONAL`, `APPARENTLY_ACCIDENTAL` ou `UNCERTAIN`. Contexto é observação humana ligada ao evento, não rotina nem intenção comprovada.

## 6. Autenticação e sessão

Esta seção incorpora integralmente o contrato de autenticação v1.0.0: tokens opacos, access token curto, refresh rotativo, armazenamento seguro, logout idempotente, recuperação sem enumeração de conta e Google condicional.

### 6.1 Tipos de sessão e credenciais

```json
{
  "sessionId": "e7f8f441-7cdb-4573-96b1-edda35cccd93",
  "accessToken": "opaque-access-token",
  "accessTokenExpiresAt": "2026-09-14T18:15:00Z",
  "refreshToken": "opaque-refresh-token",
  "refreshTokenExpiresAt": "2026-10-14T18:00:00Z"
}
```

O mobile não decodifica tokens nem infere sua validade pelo conteúdo. Access token e refresh token são persistidos como uma única sessão versionada exclusivamente em Keystore, Keychain ou mecanismo seguro equivalente. Nunca são armazenados em AsyncStorage, SQLite comum, logs ou estado global persistido inseguro.

Credenciais por senha:

```json
{
  "email": "paula@example.com",
  "password": "string entre 12 e 128 caracteres"
}
```

O backend remove espaços externos do e-mail e faz comparação sem diferença de maiúsculas/minúsculas. Senha nunca retorna nem aparece em logs ou erros.

### 6.2 Consultar capacidades

`GET /auth/capabilities`

Resposta `200`:

```json
{
  "data": {
    "passwordLogin": true,
    "googleLogin": false,
    "passwordRecovery": true
  }
}
```

O mobile só exibe **Continuar com Google** quando `googleLogin=true`. Falha da consulta não habilita Google por suposição.

### 6.3 Criar conta

`POST /auth/register`, com `Idempotency-Key` obrigatório.

```json
{
  "name": "Paula Oliveira",
  "email": "paula@example.com",
  "password": "senha-secreta-com-ao-menos-12-caracteres"
}
```

Resposta `201`:

```json
{
  "data": {
    "tutor": {
      "id": "b758d342-8af1-4f18-9c71-1effaf6df106",
      "name": "Paula Oliveira",
      "email": "paula@example.com",
      "createdAt": "2026-09-14T18:00:00Z",
      "version": 1
    },
    "session": {
      "sessionId": "e7f8f441-7cdb-4573-96b1-edda35cccd93",
      "accessToken": "opaque-access-token",
      "accessTokenExpiresAt": "2026-09-14T18:15:00Z",
      "refreshToken": "opaque-refresh-token",
      "refreshTokenExpiresAt": "2026-10-14T18:00:00Z"
    }
  }
}
```

`name` possui de 1 a 120 caracteres visíveis; e-mail possui no máximo 254 caracteres; senha possui de 12 a 128 caracteres. E-mail já cadastrado retorna `409 EMAIL_ALREADY_REGISTERED`.

### 6.4 Login por senha

`POST /auth/login`

O corpo usa as credenciais da seção 6.1. Resposta `200` usa o mesmo formato de `register`. Conta inexistente, senha incorreta ou credencial inválida retornam indistintamente `401 INVALID_CREDENTIALS`.

### 6.5 Login Google condicional

`POST /auth/google`

Disponível somente quando `/auth/capabilities` publicar `googleLogin=true`.

```json
{ "idToken": "token-de-identidade-do-google" }
```

Resposta `200` usa o mesmo formato de `register`. O backend valida assinatura, emissor, audiência, expiração e vínculo da conta. O mobile somente considera a autenticação concluída após resposta do backend. Quando Google não estiver configurado, retorna `403 GOOGLE_LOGIN_UNAVAILABLE`.

### 6.6 Renovar sessão

`POST /auth/refresh`

```json
{ "refreshToken": "opaque-refresh-token" }
```

Resposta `200` contém uma nova sessão completa. O refresh token é de uso único: a renovação bem-sucedida invalida o anterior e entrega novo par. Token ausente, expirado, inválido, revogado ou reutilizado retorna `401 SESSION_INVALID`. Detecção de reutilização invalida a família correspondente.

### 6.7 Consultar sessão atual

`GET /auth/session`, autenticado.

Resposta `200`:

```json
{
  "data": {
    "tutor": {
      "id": "b758d342-8af1-4f18-9c71-1effaf6df106",
      "name": "Paula Oliveira",
      "email": "paula@example.com",
      "createdAt": "2026-09-14T18:00:00Z",
      "version": 1
    },
    "sessionId": "e7f8f441-7cdb-4573-96b1-edda35cccd93"
  }
}
```

Access token inválido ou expirado retorna `401 SESSION_INVALID` ou `401 TOKEN_EXPIRED`. O mobile pode tentar no máximo um refresh controlado; falhando, limpa a sessão segura e retorna à entrada pública.

### 6.8 Logout

`POST /auth/logout`, autenticado e com `Idempotency-Key` obrigatório.

Corpo opcional:

```json
{ "refreshToken": "opaque-refresh-token" }
```

Retorna `204`, inclusive se a sessão já estiver revogada. Após resposta ou erro definitivo de autorização, o mobile remove localmente todos os tokens e dados de sessão.

### 6.9 Solicitar recuperação de acesso

`POST /auth/password-recovery/request`, com `Idempotency-Key` obrigatório.

```json
{ "email": "paula@example.com" }
```

Resposta sempre `202`:

```json
{ "data": { "accepted": true } }
```

O backend não revela se a conta existe. Quando aplicável, envia instruções com segredo temporário e de uso único.

### 6.10 Confirmar recuperação

`POST /auth/password-recovery/confirm`

```json
{
  "recoveryToken": "segredo-temporario-recebido-pelo-tutor",
  "newPassword": "nova-senha-com-ao-menos-12-caracteres"
}
```

Retorna `204`. Uma redefinição bem-sucedida invalida todos os refresh tokens ativos. Token inválido ou expirado retorna `400 RECOVERY_TOKEN_INVALID`.

### 6.11 Bootstrap e falhas

1. O app lê a sessão somente do armazenamento seguro.
2. Sem sessão, entra no estado público com motivo `absent`.
3. Com refresh token, tenta uma renovação controlada.
4. O novo par só é considerado válido após persistência íntegra da sessão completa.
5. Sessão local incompleta/corrompida ou resposta definitiva de invalidação causa limpeza e estado público seguro.
6. Falha transitória não invalida silenciosamente a sessão; produz erro recuperável.
7. Login, cadastro, refresh, logout e recuperação impedem acionamento duplicado.

Respostas de autenticação usam `Cache-Control: no-store`. O backend armazena senhas com hash resistente e salt individual, aplica rate limiting e exclui credenciais, tokens e segredos de seus logs.

## 7. Conta do tutor e LGPD

### 7.1 Consultar perfil

`GET /users/me` → `200 { "data": { "tutor": Tutor } }`.

### 7.2 Atualizar perfil

`PATCH /users/me`, com `If-Match`.

```json
{ "name": "Paula Oliveira" }
```

E-mail e método de autenticação não são alterados por esta rota.

### 7.3 Solicitar exclusão

`POST /users/me/deletion-requests`, `Idempotency-Key` obrigatório.

Resposta `202`:

```json
{
  "data": {
    "requestId": "618d553f-a334-43ac-b1ea-52eb671b902c",
    "status": "PENDING",
    "requestedAt": "2026-09-13T18:00:00Z",
    "effectiveAt": null
  }
}
```

A política legal de confirmação, cancelamento e retenção é `RESERVADA`. O backend não pode alegar exclusão consumada antes de aplicar política aprovada.

## 8. Aparelhos e preferência de reprodução

### 8.1 Registrar aparelho do tutor

`PUT /mobile-devices/{mobileDeviceId}`, idempotente.

```json
{
  "platform": "ANDROID",
  "appVersion": "1.0.0",
  "localPlaybackEnabled": false
}
```

O `mobileDeviceId` é UUID gerado pela instalação, não identificador invasivo do hardware. A preferência começa obrigatoriamente em `false` para cada novo aparelho.

### 8.2 Consultar e alterar preferência

- `GET /mobile-devices/{mobileDeviceId}/preferences`
- `PATCH /mobile-devices/{mobileDeviceId}/preferences`, com `If-Match`

```json
{ "localPlaybackEnabled": true }
```

O backend sincroniza a preferência, mas não decide o playback em tempo real e não recebe seu resultado.

### 8.3 Listar e desvincular ESP32

- `GET /esp32-devices`
- `DELETE /esp32-devices/{esp32DeviceId}` → `204`

O mecanismo para criar o vínculo é `RESERVADO` até contrato específico de segurança. Desvincular exige reautenticação ou confirmação forte definida pelo backend.

## 9. Pets

### 9.1 Criar

`POST /pets`, `Idempotency-Key` obrigatório.

```json
{
  "name": "Marley",
  "species": "DOG",
  "birthDate": "2022-05-10",
  "sex": "MALE"
}
```

Resposta `201 { "data": { "pet": Pet } }`.

### 9.2 Listar e consultar

- `GET /pets?status=ACTIVE&cursor=&limit=30`
- `GET /pets/{petId}`

Resposta de detalhe `200`:

```json
{
  "data": {
    "pet": {
      "id": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
      "name": "Marley",
      "species": "DOG",
      "photo": {
        "id": "b3a58c0f-ed8e-4a60-840f-dbd6ee06786a",
        "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
        "mediaType": "image/jpeg",
        "sizeBytes": 248731,
        "width": 1024,
        "height": 1024,
        "sha256": "64-caracteres-hexadecimais-minusculos",
        "download": {
          "url": "https://url-temporaria-de-download",
          "expiresAt": "2026-09-14T18:15:00Z"
        },
        "createdAt": "2026-09-14T18:00:00Z",
        "version": 1
      },
      "birthDate": "2022-05-10",
      "sex": "MALE",
      "status": "ACTIVE",
      "createdAt": "2026-09-14T17:00:00Z",
      "updatedAt": "2026-09-14T18:00:00Z",
      "version": 2
    }
  }
}
```

Quando não houver foto, `photo` é `null`. `404 RESOURCE_NOT_FOUND` também cobre recurso inexistente ou pertencente a outro tutor, evitando enumeração de ownership.

### 9.3 Editar

`PATCH /pets/{petId}`, com `If-Match`. Aceita somente os campos de criação. Campos omitidos permanecem inalterados; `null` remove somente valores opcionais.

### 9.4 Desativar e reativar

- `POST /pets/{petId}/deactivation` → `200` com Pet inativo.
- `DELETE /pets/{petId}/deactivation` → `200` com Pet ativo.

Desativação e reativação nunca reatribuem eventos históricos. A criação de um evento com exatamente um pet ativo gera atribuição automática no backend caso ainda não exista atribuição válida enviada pelo mobile.

## 10. Botões lógicos

### 10.1 Criar

`POST /buttons`, `Idempotency-Key` obrigatório.

```json
{ "name": "Comida", "description": null }
```

### 10.2 Listar, consultar e editar

- `GET /buttons?status=ACTIVE&cursor=&limit=30`
- `GET /buttons/{buttonId}`
- `PATCH /buttons/{buttonId}`, com `If-Match`

### 10.3 Desativar e reativar

- `POST /buttons/{buttonId}/deactivation`
- `DELETE /buttons/{buttonId}/deactivation`

Nenhuma operação altera ButtonEvents históricos.

### 10.4 Bindings físicos

- `GET /buttons/{buttonId}/binding`
- criação, substituição e remoção: `RESERVADAS` até o contrato de pareamento/segurança da ESP32.

A futura operação deve garantir exclusividade de `physicalButtonId` ativo dentro da mesma ESP32 e preservar o `buttonId` lógico.

## 11. Áudio

### 11.1 Capacidades

`GET /audio/capabilities`

```json
{
  "data": {
    "acceptedMediaTypes": ["audio/mp4"],
    "maxSizeBytes": 5242880,
    "maxDurationMs": 10000,
    "sha256Required": true
  }
}
```

Os valores são configuráveis e não fixam a decisão arquitetural ainda aberta.

### 11.2 Preparar upload

`POST /buttons/{buttonId}/audio-uploads`, `Idempotency-Key` obrigatório.

```json
{
  "mediaType": "audio/mp4",
  "sizeBytes": 241392,
  "sha256": "hexadecimal-em-minusculas"
}
```

Resposta `201` contém `uploadId`, instruções opacas de upload, expiração e headers permitidos. O mobile não recebe credenciais permanentes do object storage.

### 11.3 Confirmar upload

`POST /buttons/{buttonId}/audio-uploads/{uploadId}/complete`, idempotente.

Após validar tamanho, hash e formato, o backend ativa atomicamente o novo AudioAsset. Até essa confirmação, o áudio anterior permanece ativo.

### 11.4 Consultar áudio ativo

`GET /buttons/{buttonId}/audio` retorna AudioAsset com URL temporária de download, ou `404 RESOURCE_NOT_FOUND`.

Não existe endpoint de telemetria de playback.

## 12. Sincronização de configuração

### 12.1 Manifesto incremental

`GET /sync/configuration?cursor=<cursor>&mobileDeviceId=<uuid>`

```json
{
  "data": {
    "cursor": "cursor-opaco-novo",
    "resetRequired": false,
    "changes": [
      {
        "entityType": "BUTTON",
        "operation": "UPSERT",
        "entityId": "5a242aa8-b340-4f68-b8f1-48f8381a7ea6",
        "version": 4,
        "payload": {}
      }
    ]
  }
}
```

`entityType`: `PET`, `BUTTON`, `AUDIO_ASSET`, `PHYSICAL_BINDING`, `ESP32_DEVICE`, `TRAINING_SESSION` ou `MOBILE_DEVICE_PREFERENCE`. `operation`: `UPSERT` ou `DEACTIVATE`.

O algoritmo definitivo de reconciliação é `RESERVADO`. Até sua aprovação, o backend deve oferecer snapshot completo quando `resetRequired=true`; o mobile nunca faz last-write-wins silencioso.

## 13. Upload idempotente de eventos

`POST /sync/button-events`, `Idempotency-Key` obrigatório por lote.

```json
{
  "events": [
    {
      "id": "ad1ff15b-5833-4297-b34f-512dc009f46b",
      "buttonId": "5a242aa8-b340-4f68-b8f1-48f8381a7ea6",
      "esp32DeviceId": "0d39ce33-c7bd-465c-8362-c56f59176ef1",
      "physicalButtonId": 42,
      "espSessionId": 11802,
      "sequence": 991,
      "espUptimeMs": 704040,
      "occurredAt": "2026-09-13T18:00:00Z",
      "receivedAt": "2026-09-13T18:00:00.200Z",
      "timeQuality": "ESTIMATED_FROM_MOBILE",
      "transport": "BLE",
      "purpose": "BEHAVIORAL",
      "trainingSessionId": null
    }
  ]
}
```

Resposta `200` confirma cada ID individualmente:

```json
{
  "data": {
    "results": [
      { "eventId": "ad1ff15b-5833-4297-b34f-512dc009f46b", "status": "ACCEPTED", "error": null }
    ]
  }
}
```

`status`: `ACCEPTED`, `ALREADY_ACCEPTED` ou `REJECTED`. O backend deduplica por `eventId`; repetição idêntica retorna `ALREADY_ACCEPTED`. Mesmo ID com conteúdo divergente retorna `409 EVENT_ID_CONFLICT`. Aceite parcial é permitido.

O backend cria automaticamente EventPetAttribution quando há exatamente um pet ativo no instante de aceitação e nenhuma atribuição foi fornecida. Mudanças futuras nos pets não reprocessam eventos históricos.

## 14. Histórico e detalhes de evento

### 14.1 Listar histórico

`GET /button-events`

Filtros opcionais: `from`, `to`, `petId`, `buttonId`, `purpose`, `training`, `classification`, `cursor` e `limit`. Ordenação padrão: `occurredAt` decrescente, com `id` como desempate estável.

Cada item contém ButtonEvent, snapshot mínimo de Button e metadados humanos. Nunca contém playback.

### 14.2 Consultar detalhe

`GET /button-events/{eventId}` retorna o evento bruto, metadados humanos separados, dados da TrainingSession e informações técnicas permitidas.

### 14.3 Atribuir pet

`PUT /button-events/{eventId}/pet-attribution`, com `If-Match` quando já existir.

```json
{ "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd" }
```

`DELETE /button-events/{eventId}/pet-attribution` remove a atribuição humana, mas não altera o evento bruto. Com apenas um pet ativo, o backend pode restaurar a atribuição automática conforme regra de domínio; o comportamento deve ser informado na resposta.

### 14.4 Classificação humana

`PUT /button-events/{eventId}/classification`, com `If-Match` quando aplicável.

```json
{ "value": "UNCERTAIN" }
```

`DELETE /button-events/{eventId}/classification` remove a classificação. Nenhuma opção exclui ou altera o fato bruto.

## 15. Tipos de contexto e contexto do evento

### 15.1 ContextType

- `POST /context-types`, com `Idempotency-Key`: `{ "name": "Perto da refeição" }`.
- `GET /context-types?status=ACTIVE&cursor=&limit=30`.
- `PATCH /context-types/{contextTypeId}`, com `If-Match`.
- `POST /context-types/{contextTypeId}/deactivation`.
- `DELETE /context-types/{contextTypeId}/deactivation`.

Nomes têm entre 1 e 80 caracteres visíveis. Desativação preserva o nome histórico usado nos eventos.

### 15.2 EventContext

`PUT /button-events/{eventId}/context`, com `If-Match` quando já existir.

```json
{
  "contextTypeId": "21e83b96-d1da-48ae-9737-e9426e39baa1",
  "note": "Estava próximo do horário habitual."
}
```

`contextTypeId` e `note` são individualmente opcionais, mas ao menos um deve existir. `note` possui até 1000 caracteres. `DELETE /button-events/{eventId}/context` remove o metadado, não o ButtonEvent.

O backend nunca converte contexto em rotina, fato comprovado, intenção ou causalidade.

## 16. Sessões de treinamento

### 16.1 Iniciar

`POST /training-sessions`, `Idempotency-Key` obrigatório.

```json
{
  "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
  "startedAt": "2026-09-13T18:00:00Z",
  "note": null
}
```

Somente uma sessão ativa por tutor é permitida. Conflito retorna `409 ACTIVE_RESOURCE_CONFLICT` com a sessão ativa.

### 16.2 Consultar, listar e encerrar

- `GET /training-sessions/active`.
- `GET /training-sessions?petId=&from=&to=&cursor=&limit=30`.
- `GET /training-sessions/{trainingSessionId}`.
- `POST /training-sessions/{trainingSessionId}/completion`, com `{ "endedAt": "..." }` e `If-Match`.

TrainingSession apenas marca intervalo de uso induzido. Não contém plano, aula, recomendação ou diagnóstico. Eventos permanecem no histórico bruto.

## 17. Home

`GET /home/summary?date=2026-09-13&timezone=America%2FRecife`

```json
{
  "data": {
    "date": "2026-09-13",
    "timezone": "America/Recife",
    "eventCount": 8,
    "latestEvents": [],
    "central": {
      "state": "UNKNOWN",
      "message": "Estado da Central indisponível."
    },
    "activeTrainingSession": null
  }
}
```

`central.state`: `OPERATIONAL`, `ATTENTION`, `OFFLINE`, `UNPAIRED` ou `UNKNOWN`. A Home é factual e não cria registros de rotina.

## 18. Insights

### 18.1 Listar e consultar

- `GET /insights?status=ACTIVE&petId=&cursor=&limit=30`.
- `GET /insights/{insightId}`.

```json
{
  "id": "cf43b790-3cea-490d-bc86-176d0460163",
  "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
  "kind": "OBSERVED_ASSOCIATION",
  "title": "Possível padrão observado",
  "summary": "Foi observada uma associação recorrente nos dados do período.",
  "evidence": {
    "analysisRunId": "c2285eb6-392a-45e7-9673-f54709bef2ca",
    "periodStart": "2026-08-01T00:00:00Z",
    "periodEnd": "2026-08-31T23:59:59Z",
    "eventCount": 42,
    "support": 0.18,
    "confidence": 0.71
  },
  "createdAt": "2026-09-01T12:00:00Z",
  "version": 1
}
```

Insights são pré-computados, versionados e rastreáveis a AnalysisRun. A linguagem não afirma intenção, fala, causalidade ou diagnóstico. Eventos TEST são excluídos por padrão e a política para treinamento pertence ao AnalysisRun.

### 18.2 Validação do tutor

`PUT /insights/{insightId}/validation`, com `If-Match` quando já existir.

```json
{ "value": "USEFUL", "note": null }
```

Valores: `USEFUL`, `NOT_USEFUL`, `UNSURE`. Validação não altera fatos brutos nem retroage como rótulo verdadeiro.

## 19. Upload de fotos de pet

### 19.1 Capacidades de mídia

`GET /media/capabilities`

Resposta `200`:

```json
{
  "data": {
    "petPhoto": {
      "enabled": true,
      "acceptedMediaTypes": ["image/jpeg", "image/png", "image/webp"],
      "maxSizeBytes": 5242880,
      "minWidth": 128,
      "minHeight": 128,
      "maxWidth": 4096,
      "maxHeight": 4096,
      "sha256Required": true
    },
    "buttonAudio": {
      "enabled": true,
      "acceptedMediaTypes": ["audio/mp4"],
      "maxSizeBytes": 5242880,
      "maxDurationMs": 10000,
      "sha256Required": true
    }
  }
}
```

Esses valores são limites iniciais versionados pelo contrato. Uma capacidade com `enabled=false` não deve ser apresentada na interface. O backend valida novamente conteúdo real, assinatura do arquivo, dimensões, tamanho e hash; não confia somente nos metadados enviados.

### 19.2 Preparar upload da foto

`POST /pets/{petId}/photo-uploads`, autenticado e com `Idempotency-Key` obrigatório.

Requisição:

```json
{
  "mediaType": "image/jpeg",
  "sizeBytes": 248731,
  "width": 1024,
  "height": 1024,
  "sha256": "64-caracteres-hexadecimais-minusculos"
}
```

Resposta `201`:

```json
{
  "data": {
    "upload": {
      "id": "62a9bc20-28e0-4c32-b74d-5580407640d0",
      "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
      "status": "PENDING",
      "expiresAt": "2026-09-14T18:10:00Z",
      "instruction": {
        "method": "PUT",
        "url": "https://url-temporaria-de-upload",
        "headers": {
          "Content-Type": "image/jpeg",
          "x-upload-content-sha256": "64-caracteres-hexadecimais-minusculos"
        }
      }
    }
  }
}
```

Regras do wire format:

- `instruction.method` é `PUT` nesta versão;
- o corpo enviado à URL é exclusivamente o binário da imagem, sem multipart;
- o mobile envia exatamente os headers retornados, sem acrescentar `Authorization` do FalaPet;
- `instruction.url` e seus headers são segredos temporários e não devem aparecer em logs;
- `headers` é um mapa de strings e pode receber campos adicionais do provedor;
- repetir a mesma `Idempotency-Key` e o mesmo pedido retorna o mesmo upload enquanto válido;
- mídia ou dimensões incompatíveis retornam `415 UNSUPPORTED_MEDIA_TYPE` ou `422 DOMAIN_RULE_VIOLATION`;
- pet inexistente ou de outro tutor retorna `404 RESOURCE_NOT_FOUND`.

O upload binário bem-sucedido não ativa a foto; a confirmação é obrigatória.

### 19.3 Confirmar upload e substituir foto

`POST /pets/{petId}/photo-uploads/{uploadId}/complete`, autenticado e com `Idempotency-Key` obrigatório. Corpo vazio.

O backend verifica ownership, validade, existência do objeto, tamanho, tipo, dimensões e SHA-256. Em sucesso, ativa a nova foto e substitui atomicamente a referência anterior.

Resposta `200`:

```json
{
  "data": {
    "pet": {
      "id": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
      "name": "Marley",
      "species": "DOG",
      "photo": {
        "id": "b3a58c0f-ed8e-4a60-840f-dbd6ee06786a",
        "petId": "2ac43cf7-c428-4a72-964a-90070b2a69fd",
        "mediaType": "image/jpeg",
        "sizeBytes": 248731,
        "width": 1024,
        "height": 1024,
        "sha256": "64-caracteres-hexadecimais-minusculos",
        "download": {
          "url": "https://url-temporaria-de-download",
          "expiresAt": "2026-09-14T18:15:00Z"
        },
        "createdAt": "2026-09-14T18:00:00Z",
        "version": 1
      },
      "birthDate": "2022-05-10",
      "sex": "MALE",
      "status": "ACTIVE",
      "createdAt": "2026-09-14T17:00:00Z",
      "updatedAt": "2026-09-14T18:00:00Z",
      "version": 2
    }
  }
}
```

A repetição idempotente retorna o mesmo resultado. Upload expirado retorna `410 UPLOAD_EXPIRED`; binário ausente retorna `409 UPLOAD_NOT_FOUND`; divergência de hash/metadados retorna `422 UPLOAD_VALIDATION_FAILED`. Em qualquer falha, a foto anterior permanece ativa.

### 19.4 Remover foto

`DELETE /pets/{petId}/photo`, autenticado, com `Idempotency-Key` e `If-Match` da versão atual do Pet.

Resposta `200` contém o Pet completo com `photo: null` e `version` incrementada. Se já estiver sem foto, a repetição com a mesma chave retorna o mesmo resultado. A remoção da referência é imediata; a eliminação física do objeto segue a política de retenção e privacidade aplicável.

### 19.5 Ciclo de vida do upload

Estados do upload: `PENDING`, `COMPLETED`, `EXPIRED` e `REJECTED`. Uploads pendentes/expirados não aparecem como `Pet.photo`. O backend pode eliminar objetos temporários sem confirmação. O mobile não reutiliza URL expirada e deve iniciar novo upload.

## 20. Regras de segurança e privacidade

- TLS, autenticação e autorização por tutor são obrigatórios.
- Cada consulta deve ser restrita aos recursos pertencentes ao tutor autenticado.
- Tokens, senhas, segredos de recuperação, cabeçalho Authorization, áudio, notas privadas e URLs assinadas não entram em logs.
- O backend valida tipo, tamanho, ownership e estado de todo recurso referenciado.
- Rate limiting protege autenticação, uploads e operações sensíveis.
- Object storage usa URLs temporárias e escopo mínimo.
- PostgreSQL armazena metadados de áudio, não o arquivo binário.
- Logs estruturados usam `requestId` e, quando necessário, IDs técnicos; evitam conteúdo privado.
- Dados raw e derivados permanecem semanticamente separados.
- O worker de ML não altera ButtonEvent.
- Permissões e coleta seguem minimização e finalidade da LGPD.

## 21. Cache e operação offline

- Requisições de autenticação usam `Cache-Control: no-store`.
- GETs podem usar `ETag`; dados privados não usam cache público.
- Indisponibilidade do backend não bloqueia recepção, persistência local nem playback opcional.
- O mobile mantém eventos não sincronizados até confirmação individual.
- O backend não participa de ACK BLE nem da decisão de iniciar áudio.
- Playback local começa desativado por aparelho.
- Nenhuma API recebe ou retorna status de playback.

## 22. Testes de contrato obrigatórios

Mobile e backend devem compartilhar fixtures sem compartilhar código-fonte. Cobertura mínima:

1. autenticação, rotação de refresh, logout idempotente e Google desabilitado;
2. isolamento de ownership entre tutores;
3. validação e concorrência otimista;
4. criação, edição, desativação e preservação histórica de Pet/Button/ContextType;
5. um único áudio ativo e confirmação atômica de upload;
6. upload de eventos com lote parcial, repetição idêntica e conflito de ID;
7. ButtonEvent imutável e metadados separados;
8. atribuição automática com exatamente um pet ativo;
9. ausência de reatribuição histórica;
10. TrainingSession sem plano de treinamento;
11. histórico paginado e filtros estáveis;
12. ausência de qualquer campo/status de playback;
13. insights rastreáveis, cautelosos e sem mutação de raw;
14. capacidades de mídia, preparação, upload binário, confirmação, substituição, remoção, expiração e falhas de validação da foto;
15. falhas transitórias, `Retry-After`, cursores inválidos e URLs expiradas;
16. compatibilidade aditiva dentro de `/api/v1`.

## 23. Versionamento e governança

- A versão deste documento segue SemVer.
- Alteração incompatível de rota, tipo ou semântica exige `/api/v2` e versão principal nova.
- Campos opcionais podem ser adicionados em versão menor.
- Correções textuais sem mudança semântica incrementam patch.
- Toda mudança registra motivação, requisitos afetados, migração e janela de compatibilidade.
- Mobile e backend versionam uma cópia idêntica deste documento em `docs/contracts/`.
- Testes de contrato devem fixar a versão suportada.
- Uma capacidade não implementada não pode ser anunciada como disponível.

## 24. Matriz de cobertura

| Domínio | Requisitos principais | Seção |
| --- | --- | --- |
| Experiência única | RF-001–RF-005, AC-06 | 1 |
| Autenticação/conta | RF-006–RF-011 | 6–7 |
| Preferência local | RF-012–RF-015 | 8, 21 |
| Pets | RF-016–RF-021, AC-09 | 9 |
| Botões/áudio | RF-022–RF-033, AC-07 | 10–11 |
| Central/transporte | RF-034–RF-042, AC-01–AC-05 | reservado e refletido em 8, 12–13, 21 |
| Eventos/histórico | RF-043–RF-053 | 13–14 |
| Contextos | RF-054–RF-061, AC-08 | 15 |
| Treinamento | RF-062–RF-068, AC-12 | 16 |
| Home | RF-069–RF-072 | 17 |
| Offline/sync | RF-073–RF-082 | 12–13, 21 |
| Backend/storage | RF-083–RF-088 | 11, 19–20 |
| ML/insights | RF-089–RF-099, AC-10 | 18 |

## 25. Decisões e pendências da versão 1.1.0

### Aprovadas

- API REST versionada em `/api/v1` (herdada do contrato de autenticação).
- Tutor como única identidade do aplicativo.
- Tokens opacos e refresh rotativo.
- Idempotência de eventos por UUID.
- Cursor opaco para paginação e configuração.
- Versionamento de recursos mutáveis.
- Arquivos em object storage com instruções temporárias.
- ButtonEvent raw imutável e metadados humanos separados.
- Nenhum estado persistido de playback.

Também ficam aprovadas as decisões de interoperabilidade desta versão: enums de Pet, limites de texto e arquivo, concorrência por `version`, IDs da instalação, upload em duas etapas, filtros/paginação e unicidade de TrainingSession.

### Pendentes antes dos incrementos correspondentes

- contrato de segurança e pareamento da ESP32;
- formato final e limites comerciais de áudio;
- reconciliação detalhada de configuração;
- retenção central e fluxo jurídico completo de exclusão;
- operação da Central física futura;
- administração e agendamento do pipeline de ML.

Essas pendências não bloqueiam pets, botões lógicos, histórico, contextos ou treinamento quando seus endpoints e invariantes desta versão forem suficientes; bloqueiam apenas a capacidade diretamente dependente.

## 26. Histórico de versões

| Versão | Data | Alteração |
| --- | --- | --- |
| 1.0.0 | 2026-09-14 | Contrato unificado aprovado, incluindo autenticação e domínios do aplicativo. |
| 1.1.0 | 2026-09-14 | Emenda aditiva: fecha os schemas de detalhe do Pet, `Pet.photo`, capacidades de mídia e ciclo completo de upload, confirmação, substituição e remoção da foto. |
