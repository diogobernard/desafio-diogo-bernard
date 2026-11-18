# ADR-004: Leitura em réplica com fallback filtrado

- **Status:** aceito
- **Contexto:** consultas podem usar réplica com atraso, mas uma solicitação recém
  aceita não deve parecer inexistente.
- **Decisão:** direcionar transações read-only à réplica. Consultas que podem estar
  atrasadas usam os sinais de UUIDv7 e do último número para permitir fallback à
  primária; ids antigos ou inexistentes permanecem somente na réplica.
- **Consequências:** reduz carga de leitura na primária e evita falso `404` para dados
  recentes; sinais e fallback precisam ser monitorados.
- **Alternativa rejeitada:** cache Redis atualizado após commit, que introduziria
  escrita dupla e outro modo de falha.
