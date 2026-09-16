# FalaPet — Emenda Contratual 001

## Ingestão de ButtonEvents, vínculo físico e sessões de treinamento

**Status:** Aprovada  
**Data:** 2026-09-16  
**Contrato alterado:** `falapet-mobile-backend-contract-v1.md`  
**Aplicação:** pré-requisito para o Épico 7

---

## 1. Objetivo e precedência

Esta emenda completa as regras necessárias para a ingestão idempotente de `ButtonEvent` e estabelece os modelos mínimos de `Esp32Device`, `PhysicalButtonBinding` e `TrainingSession`.

Em caso de conflito, esta emenda prevalece sobre trechos anteriores relativos a limite e resposta de lote, conflito de `eventId`, validação de hardware, referência a sessões de treinamento e atribuição automática de Pet. As demais regras do contrato permanecem inalteradas.

## 2. Regras comuns

- URL-base: `/api/v1`.
- As rotas exigem `Authorization: Bearer <accessToken>`.
- IDs são UUIDs textuais; instantes usam RFC 3339 em UTC.
- Toda resposta mantém `X-Request-Id`.
- Respostas bem-sucedidas usam `{ "data": ... }`.
- Recurso inexistente ou de outro tutor responde `404 RESOURCE_NOT_FOUND`.
- Nenhuma API recebe, armazena ou retorna telemetria de reprodução.

`ButtonEvent` continua um fato bruto, imutável e separado de metadados humanos e inferências.

## 3. Contrato mínimo de ESP32 e binding físico

### 3.1 Esp32Device

Uma `Esp32Device` representa uma Central/ESP32 já vinculada à conta. Criação e pareamento permanecem fora de escopo.

```json
{
  "id": "uuid",
  "status": "ACTIVE",
  "createdAt": "2026-09-16T00:00:00Z"
}
```

Valores de `status`: `ACTIVE`, `UNLINKED`.

Somente uma ESP32 `ACTIVE` pertencente ao tutor autenticado pode ser referenciada por um evento.

### 3.2 PhysicalButtonBinding

`PhysicalButtonBinding` conecta identidade física a Button lógico, sem alterar a identidade do Button.

```json
{
  "id": "uuid",
  "esp32DeviceId": "uuid",
  "physicalButtonId": "string",
  "buttonId": "uuid",
  "status": "ACTIVE",
  "createdAt": "2026-09-16T00:00:00Z"
}
```

Regras:

- Um `physicalButtonId` ativo é exclusivo dentro da mesma ESP32.
- Um evento só é aceito com binding ativo para `(esp32DeviceId, physicalButtonId)`.
- O `buttonId` enviado deve ser igual ao `buttonId` do binding ativo.
- Button, ESP32 e binding pertencem ao tutor autenticado.
- Criação, troca e remoção de bindings continuam reservadas.

## 4. Contrato mínimo de TrainingSession

`TrainingSession` somente demarca eventos de uso induzido. Não é plano, aula, recomendação, diagnóstico ou rotina.

```json
{
  "id": "uuid",
  "petId": "uuid",
  "status": "ACTIVE",
  "startedAt": "2026-09-16T00:00:00Z",
  "completedAt": null,
  "note": null
}
```

Valores de `status`: `ACTIVE`, `COMPLETED`.

Regras para a ingestão:

- `trainingSessionId` é opcional.
- Quando informado, deve existir, pertencer ao tutor autenticado e estar `ACTIVE`.
- O Pet da sessão não altera o fato bruto.
- Eventos vinculados permanecem preservados após o encerramento.
- `purpose=TEST` não exige sessão.
- A ingestão não cria nem encerra sessões.

Os endpoints de criação, listagem e encerramento de sessão pertencem ao Épico 9.

## 5. Ingestão em lote de ButtonEvents

### 5.1 Endpoint

```http
POST /sync/button-events
```

### 5.2 Limite

Uma requisição aceita no máximo **100 eventos**.

Um array com mais de 100 itens retorna:

```http
400 Bad Request
```

com `VALIDATION_ERROR`. Nenhum item desse lote é processado.

### 5.3 Request

```json
{
  "events": [
    {
      "id": "uuid",
      "buttonId": "uuid",
      "esp32DeviceId": "uuid",
      "physicalButtonId": "button-a",
      "espSessionId": "uuid",
      "sequence": 42,
      "espUptimeMs": 184920,
      "occurredAt": "2026-09-16T00:00:00Z",
      "receivedAt": "2026-09-16T00:00:01Z",
      "timeQuality": "ESTIMATED_FROM_MOBILE",
      "transport": "BLE",
      "purpose": "BEHAVIORAL",
      "trainingSessionId": null
    }
  ]
}
```

Todos os campos são obrigatórios, exceto `trainingSessionId`, que é opcional e pode ser `null`.

Valores permitidos:

```text
timeQuality: ESTIMATED_FROM_MOBILE | RECEIVED_TIME_ONLY
transport: BLE | WIFI
purpose: BEHAVIORAL | TEST
```

O request não pode incluir `petId`, contexto, classificação, intenção, significado, diagnóstico ou campos de playback.

### 5.4 Resposta de lote processável

Quando o envelope do lote for válido — inclusive em lote misto — a resposta é sempre:

```http
200 OK
```

```json
{
  "data": {
    "results": [
      {
        "eventId": "uuid",
        "status": "ACCEPTED"
      },
      {
        "eventId": "uuid",
        "status": "ALREADY_ACCEPTED"
      },
      {
        "eventId": "uuid",
        "status": "REJECTED",
        "error": {
          "code": "EVENT_ID_CONFLICT",
          "message": "O identificador do evento já foi usado com conteúdo diferente."
        }
      }
    ]
  }
}
```

Os resultados mantêm a ordem dos itens enviados.

Valores de `status`: `ACCEPTED`, `ALREADY_ACCEPTED`, `REJECTED`.

### 5.5 Resultado individual

| Situação | Resultado |
|---|---|
| Evento válido e novo | `ACCEPTED` |
| Mesmo `id` e conteúdo canônico idêntico | `ALREADY_ACCEPTED` |
| Mesmo `id` com conteúdo diferente | `REJECTED` + `EVENT_ID_CONFLICT` |
| Button, ESP32 ou binding inválido/de outro tutor | `REJECTED` + `RESOURCE_NOT_FOUND` |
| TrainingSession inválida, de outro tutor ou não ativa | `REJECTED` + `TRAINING_SESSION_INVALID` |
| Campo, enum, timestamp ou vínculo inválido | `REJECTED` + `VALIDATION_ERROR` |

`409 EVENT_ID_CONFLICT` não é resposta HTTP para lote misto: o conflito é individual e os outros itens válidos continuam processados.

### 5.6 Erros de envelope

| Situação | HTTP | Código |
|---|---:|---|
| JSON inválido, `events` ausente, não-array, vazio ou acima de 100 | 400 | `VALIDATION_ERROR` |
| Token ausente, inválido, expirado ou revogado | conforme contrato de autenticação | código aplicável |
| Falha interna inesperada | 500 | `INTERNAL_ERROR` |
| Indisponibilidade técnica temporária | 503 | `SERVICE_UNAVAILABLE` |

Uma falha individual não transforma lote processável em erro HTTP global.

## 6. Deduplicação e imutabilidade

- A deduplicação é global pelo UUID `ButtonEvent.id`.
- O backend armazena fingerprint canônico dos campos brutos.
- Diferença em qualquer campo bruto normalizado é relevante.
- Restrição única no PostgreSQL e transação apropriada impedem duplicação sob concorrência.
- Reenvio após timeout ou falha de rede é seguro.
- Evento aceito não pode ser alterado ou apagado pelas APIs normais.

## 7. Atribuição automática de Pet

Após persistir evento válido:

1. o backend conta Pets ativos do tutor;
2. com exatamente um Pet ativo, cria `EventPetAttribution` separada com `origin=AUTOMATIC_SINGLE_ACTIVE_PET`;
3. com zero ou mais de um Pet ativo, não cria atribuição;
4. mudanças posteriores em Pets não reprocessam eventos anteriores.

A atribuição é criada na mesma transação lógica e não adiciona `petId` ao fato bruto.

## 8. Exclusões

Esta emenda não autoriza:

- edição, exclusão ou reprocessamento de evento bruto;
- atribuição manual, contexto ou classificação humana;
- criação ou encerramento de TrainingSession;
- criação ou modificação de ESP32/binding;
- pareamento ESP32, credenciais, BLE, RF, Wi-Fi fallback, OTA ou replay mitigation;
- áudio remoto, object storage ou telemetria de reprodução;
- insights, ML, Home ou histórico de eventos.

## 9. Compatibilidade e próximos passos

Esta emenda é aditiva e deve ser refletida no OpenAPI e nas fixtures compartilhadas antes da implementação do Épico 7.

O próximo trabalho autorizado é implementar os modelos, migrations, portas internas e testes necessários para esta emenda, seguido pelo Épico 7.

