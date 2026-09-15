# Épico 4 — classificação, escopo e rastreabilidade

## Classificação vigente

| Entrega | Situação | Motivo |
| --- | --- | --- |
| Épico 4A — Gestão de Pets | APROVADO | CRUD, ownership, idempotência, cursor, versionamento e desativação passaram pelos gates aplicáveis. |
| Épico 4B — Fotos de Pets | BLOQUEADO/ADIADO | Não há provider, modelo operacional ou configuração de object storage aprovados. |
| Épico 4 completo | REPROVADO | O contrato Mobile–Backend v1.1.0 ainda exige fotos de Pets, que não podem ser implementadas sem emenda contratual e decisão de storage. |

Os documentos oficiais e o contrato aprovado não foram alterados por este incremento.

## Épico 4A — Gestão de Pets

As rotas abaixo usam `/api/v1`, exigem Bearer token opaco e devolvem `X-Request-Id`.

| Método | Rota | Resultado |
| --- | --- | --- |
| `POST` | `/pets` | `201 {"data":{"pet":Pet}}`; `Idempotency-Key` obrigatória |
| `GET` | `/pets` | `200 {"data":{"items":[Pet],"page":{"nextCursor":...,"hasMore":...}}}` |
| `GET` | `/pets/{petId}` | `200 {"data":{"pet":Pet}}` |
| `PATCH` | `/pets/{petId}` | `200 {"data":{"pet":Pet}}`; `If-Match: "<version>"` obrigatório |
| `POST` | `/pets/{petId}/deactivation` | `200 {"data":{"pet":Pet}}` inativo |
| `DELETE` | `/pets/{petId}/deactivation` | `200 {"data":{"pet":Pet}}` ativo |

Todo Pet pertence exclusivamente ao tutor da sessão. Recurso inexistente ou de outro tutor retorna `404 RESOURCE_NOT_FOUND`. A criação é idempotente; chave reutilizada com payload divergente retorna `409 DUPLICATE_RESOURCE`. O PATCH compara a versão no PostgreSQL e retorna `409 VERSION_CONFLICT` com o Pet atual em `error.details.pet` quando há conflito.

A listagem aceita `status=ACTIVE|INACTIVE|ALL`, `cursor` e `limit` de 1 a 100. A ordem estável é `created_at DESC, id DESC`; o cursor é opaco, assinado e vinculado ao tutor e filtro. Cursor inválido, adulterado ou usado sob outro tutor/filtro retorna `400 INVALID_CURSOR`.

Desativação é lógica e idempotente no estado final. O módulo não cria, consulta, altera ou reatribui `ButtonEvent`; eventos históricos permanecem intocados. `Pet.photo` é sempre `null` neste estágio.

## Épico 4B — Fotos de Pets

As rotas de preparação, confirmação e remoção de foto não estão registradas. Não há adapter, SDK, bucket, configuração, URL assinada, instrução temporária, upload binário, confirmação, download ou remoção de foto. PostgreSQL não persiste binário de foto.

`GET /api/v1/media/capabilities` preserva o schema de capabilities já implementado, com `petPhoto.enabled=false` e `buttonAudio.enabled=false`. Uma capability desabilitada não deve ser apresentada pela interface e não oferece URL, header ou instrução que leve o mobile a iniciar upload. Os limites presentes no schema não anunciam uma operação ativa.

O contrato vigente prevê fotos por object storage. Sem decisão operacional e emenda contratual aprovada, não se deve mudar `enabled` para `true`, registrar endpoints de foto, escolher provider ou inventar wire formats.

## Áudios de botões

Áudio não integra o backend de mídia neste estágio. O binário fica local ao celular do tutor no Tutor Mode. No futuro, a distribuição ocorrerá para o cartão de memória da Central física. O backend não armazena ou recebe binário de áudio; também não decide reprodução em tempo real e não recebe nem retorna telemetria de tocado, não tocado, falha, expiração ou latência.

## Gates que sustentam a aprovação 4A

Os testes usam PostgreSQL real com Testcontainers e cobrem criação, ownership entre dois tutores, criação idempotente concorrente, listagem por status, cursor inválido, paginação, PATCH parcial, limpeza de campos opcionais, `If-Match`, conflito concorrente, desativação, reativação, `photo: null`, `X-Request-Id`, fixtures de contrato e Spring Modulith. Os gates `./mvnw clean verify` e `.\mvnw.cmd clean verify` devem passar para manter esta classificação.

## Fora do escopo

Não foram adicionados exclusão física de Pet ou foto, eventos, atribuições históricas, botões, armazenamento ou upload remoto de áudio, aparelhos, ESP32, contexto, sincronização, Home, insights, ML, telemetria de playback, pagamentos, rotinas, diagnósticos ou planos de treinamento.
