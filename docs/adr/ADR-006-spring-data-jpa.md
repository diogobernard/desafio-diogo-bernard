# ADR-006: Persistência com Spring Data JPA

- **Status:** aceito; substitui o ADR-005.
- **Contexto:** a persistência PostgreSQL precisa manter idempotência, transações
  atômicas, numeração sem lacunas, leitura roteada e publicação do outbox com
  `SKIP LOCKED`, além de gravar itens em lote.
- **Decisão:** mapear as tabelas mantidas pelo Flyway com entidades JPA regulares e
  usar repositórios Spring Data. Consultas nativas ficam nos repositórios somente
  para `ON CONFLICT`, `UPDATE ... RETURNING`, seleção `SKIP LOCKED` e métricas sem
  equivalente JPQL. IDs atribuídos implementam `Persistable`; o outbox usa sequência
  com alocação de 50. JSONB é representado como `String` com `SqlTypes.JSON`.
- **Transações:** os gateways continuam sendo a fronteira transacional do `app`.
  O `EntityManagerFactory` e o `JpaTransactionManager` usam o `DataSource` primário,
  um `LazyConnectionDataSourceProxy` que escolhe a réplica em transações
  `readOnly`. Cálculo permanece fora da transação de emissão e o contador é
  bloqueado por último.
- **Schema:** Flyway continua dono das tabelas; Hibernate usa `ddl-auto=validate` e
  `open-in-view=false`. A migração V2 ajusta o incremento da sequência ao
  `allocationSize`, sem reiniciar o valor existente.
- **Consequências:** mapeamento declarativo, menos SQL de infraestrutura nos
  gateways, inserções em lote e validação do schema no início. Os testes TI-20 e
  TI-21 protegem contra `SELECT` de `merge` para IDs atribuídos e contra a perda de
  batching. Métricas e regressões transacionais continuam verificadas por testes
  de integração.
- **Alternativa rejeitada:** manter todo o SQL explícito com `JdbcClient`, conforme
  ADR-005. Consultas SQL específicas permanecem pontuais nos repositórios Spring
  Data quando necessárias às garantias PostgreSQL.
