# Decisão pendente — emenda contratual para fotos de Pets

**Status:** PENDENTE — não aprovada para implementação.

O contrato Mobile–Backend v1.1.0 prevê fotos de Pets por object storage. O backend não possui uma decisão de provider, modelo operacional ou configuração de ambiente para executar esse fluxo. Esta nota não altera o contrato, OpenAPI ou os documentos oficiais; registra os pré-requisitos mínimos para uma futura emenda aprovada.

Antes de implementar Fotos de Pets, a emenda deve definir integralmente:

- provider e protocolo de object storage para fotos, com modelo de configuração por ambiente;
- schema completo de capabilities e a semântica de `enabled`;
- schema de preparação do upload;
- formato, privilégio, validade e headers de URLs temporárias;
- schema e semântica de confirmação;
- schema e semântica de remoção;
- política de expiração, rejeição, retenção e limpeza;
- schema de `Pet.photo` não nulo, incluindo a referência de download temporária;
- erros, status HTTP e idempotência completos para cada etapa.

Após a emenda e a decisão operacional, a integração deve validar tipo real, tamanho, dimensões e SHA-256 antes de ativar uma foto. A troca deve preservar a foto anterior quando falhar e a remoção de referência não deve prometer apagar imediatamente o objeto físico.

Esta decisão não propõe object storage para áudios. Áudios permanecem locais ao Tutor Mode e, futuramente, serão distribuídos ao cartão de memória da Central física. O backend não é caminho crítico de reprodução e não recebe binários ou telemetria de playback.
