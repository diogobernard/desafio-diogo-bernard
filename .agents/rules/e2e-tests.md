# Regras para testes E2E — API de emissão fiscal

## Escopo e ambiente

Os E2E validam jornadas HTTP completas da API de emissão de notas fiscais. Leia
`README.md`, `AGENTS.md`, `kotlin.md`, `gradle.md`, `logs.md` e
`unit-tests.md`. O contrato vem do PRD e da TechSpec; não invente respostas ou
regras de negócio.

- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` e um cliente HTTP real.
- Os E2E usam PostgreSQL 17 para primária e réplica e Kafka KRaft por Testcontainers.
- Configure conexões e bootstrap Kafka com `@DynamicPropertySource`; não use serviços
  compartilhados da máquina nem dados de produção.
- Os testes preparam e limpam seus dados somente no banco descartável. Não altere
  `docker-compose.yml`, `.env` ou dados locais compartilhados para preparar E2E.
- A tarefa Gradle é `:app:e2eTest` e seleciona a tag `e2e`. Carga usa a tag `load` e
  a tarefa `:app:loadTest`; não misture essas jornadas na suíte E2E.

## Jornadas

Cubra, conforme o contrato da TechSpec:

- Pedido aceito, emissão finalizada, nota consultável e valores calculados.
- Regimes de tributação suportados e modo `POR_DENTRO` configurado.
- Idempotência por cliente e rejeição de reuso da chave com conteúdo diferente.
- Consulta imediata sem falso `404`, isolamento entre clientes e ETag/`304`.
- Limite de 500 itens, publicação e processamento de eventos e notificações dos
  quatro consumidores.
- Falhas definitivas, retries e DLQs quando pertinentes à mudança.

Um teste deve representar uma jornada observável. Verifique resposta pública e estado
persistido por JDBC somente quando necessário para validar efeitos assíncronos ou
garantias transacionais. Não use inspeção direta de arquivos como asserção.

## Legibilidade e segurança

- Use nomes em português entre crases, Given/When/Then ou Arrange/Act/Assert e
  relógio controlado quando a data fizer parte do resultado.
- Não use `Thread.sleep`; aguarde estados observáveis com timeout limitado.
- Nunca inclua CPF/CNPJ sem máscara, payload sensível, credenciais ou chaves em nomes,
  mensagens, logs ou relatórios. Fixtures de documentos devem ser fictícias.
- Não exija ordem concorrente de UUIDs ou mensagens se o contrato não a garantir.

Execute `./gradlew :app:e2eTest` para as jornadas afetadas. Informe resultado e
limitações de ambiente; uma suíte que descobre zero testes não valida a mudança.
