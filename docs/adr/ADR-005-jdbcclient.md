# ADR-005: SQL explícito com JdbcClient

- **Status:** substituído por [ADR-006 — Spring Data JPA](ADR-006-spring-data-jpa.md)
- **Contexto:** persistência requer `ON CONFLICT`, `RETURNING`, bloqueios seletivos e
  inserções em lote.
- **Decisão:** usar `JdbcClient` e SQL explícito nos gateways PostgreSQL.
- **Consequências:** consultas e limites transacionais ficam visíveis e previsíveis;
  mapeamento e evolução de schema ficam sob responsabilidade do código e do Flyway.
- **Alternativa rejeitada:** JPA/Hibernate, cuja abstração não simplifica estes
  padrões SQL e adicionaria dirty checking e proxies sem benefício neste modelo.
