# Regras para logs

## Escopo

Aplicar ao criar, revisar ou alterar logs da aplicação, integrações, configuração de observabilidade e scripts de execução. Seguir também [kotlin.md](kotlin.md) e [folder-structure.md](folder-structure.md), preservando a independência do módulo `domain`.

## Regra obrigatória: proteção de dados sensíveis

> **NUNCA REGISTRE VALORES SENSÍVEIS EM LOGS**, não os inclua em mensagens de erro e nunca confirme chaves reais em exemplos ou documentação.

- Nunca registrar senhas, chaves de API, tokens, cookies, identificadores de sessão, credenciais de banco ou dados pessoais sensíveis. Isso inclui `FUTEBOL_API_KEY`, `DB_PASSWORD` e os valores de cabeçalhos como `Authorization`, `Cookie` e `Set-Cookie`.
- Não registrar dumps de variáveis de ambiente, arquivos `.env` ou objetos de configuração. Os requests e responses das integrações HTTP devem seguir a captura sanitizada descrita abaixo; nunca registrar seus headers, URLs ou corpos brutos. Strings de conexão também podem conter segredos.
- Sanitizar mensagens recebidas de terceiros antes de registrá-las. A obrigação de registrar a mensagem original de uma integração nunca autoriza expor valores sensíveis.
- Aplicar a mesma proteção a mensagens de exceção, causas encadeadas, stack traces, respostas HTTP de erro, logs de clientes HTTP, testes e evidências. Não passar uma exceção bruta ao logger quando sua mensagem ou suas causas puderem conter dados sensíveis.
- Substituir valores sensíveis integralmente por `[REMOVIDO]`; não revelar prefixos, sufixos, tamanho ou outros fragmentos de uma chave real. Quando não for possível sanitizar com segurança, omitir o conteúdo e registrar o motivo da omissão.
- Usar apenas placeholders explícitos, como `<CHAVE_FICTICIA>`, em exemplos e documentação. Nunca copiar, reproduzir, validar ou confirmar uma chave real nesses materiais.

## Características dos registros

- Registrar eventos com finalidade operacional clara: o que aconteceu, em qual componente e operação, qual foi o resultado e quais dados seguros permitem investigar o problema.
- Usar o mecanismo de logging já disponível no `app`, com SLF4J e logger associado à classe. Não usar `println`, `System.out`, `System.err` ou `printStackTrace` como logging da aplicação; nos scripts, manter mensagens controladas e sem exposição de credenciais.
- Preferir mensagens parametrizadas e campos com nomes estáveis. Evitar interpolação de objetos inteiros e chamadas a `toString()` que possam expor dados ou executar trabalho desnecessário.
- Incluir timestamp com fuso explícito, nível, componente/logger, evento, operação e identificador de correlação quando disponível. Para integrações, incluir provedor, duração em milissegundos, resultado e tentativa quando houver retry.
- Reutilizar o identificador de correlação da operação entre entrada e integração. Validar identificadores recebidos externamente e limpar o contexto de logging ao concluir a execução, evitando misturar requisições.
- Manter mensagens objetivas e consistentes, com unidades explícitas e tamanho limitado. Neutralizar quebras de linha e caracteres de controle de entradas externas para evitar falsificação de registros.
- Evitar volume desnecessário, repetição do mesmo erro em várias camadas e logs por item de coleções extensas. Logs não substituem métricas e não devem alterar o resultado da operação.
- Configurar níveis por ambiente e componente. Habilitar diagnóstico detalhado somente quando necessário, mantendo a sanitização em todos os níveis, inclusive `DEBUG` e `TRACE`.

## Níveis de log

| Nível | Uso |
| --- | --- |
| `ERROR` | Falha definitiva de integração, operação interrompida ou erro inesperado que exige investigação. |
| `WARN` | Falha de integração em tentativa que será repetida, recuperação por fallback ou condição degradada que exige atenção. |
| `INFO` | Inicialização, encerramento, eventos operacionais relevantes e request/response sanitizados das integrações HTTP. |
| `DEBUG` / `TRACE` | Diagnóstico técnico controlado, com campos seguros e sem dumps de payloads ou credenciais. |

Validações esperadas de entrada e ausência legítima de dados não devem gerar `ERROR` ou stack trace por rotina. Uma falha de integração deve sempre gerar um registro, mesmo quando for tratada ou convertida em uma resposta de negócio.

## Request e response de integrações HTTP

- **REGISTRAR EM INFO O REQUEST ENVIADO E O RESPONSE RECEBIDO**, tanto em sucesso quanto em falha. Capturar na fronteira HTTP os dados efetivamente usados pelo cliente: método, URL com query string sanitizada, headers e corpo; na resposta, registrar também o status recebido, duração e requestId.
- Registrar o corpo recebido antes da conversão para DTOs ou modelos. Preservar o conteúdo completo dentro do limite de payload da integração, incluindo formatação e precisão dos dados seguros; não substituí-lo por uma projeção dos modelos da aplicação.
- **OMITIR INTEGRALMENTE A API_KEY**, inclusive em Authorization, headers próprios, parâmetros da URL, campos aninhados, mensagens e representações escapadas ou codificadas. Aplicar a mesma remoção a tokens, senhas, cookies e demais valores sensíveis. Usar `[REMOVIDO]` no lugar do valor completo.
- Sanitizar apenas a cópia destinada ao log. O request enviado ao provedor e o response consumido pela aplicação devem manter seus bytes originais. Não realizar uma nova chamada ou uma segunda leitura da origem para produzir o registro.
- Manter os limites existentes de leitura e payload. Se o corpo exceder o limite, estiver incompleto, for inválido ou tiver formato sem sanitização segura, registrar um marcador explícito de omissão, preservando o status e os headers seguros. Não inventar um response quando não houver resposta HTTP, nem registrar como enviada uma chamada bloqueada localmente.
- Serializar os dados do registro em uma única linha, escapando quebras de linha e caracteres de controle do corpo. Não habilitar wire logging bruto de bibliotecas HTTP.
- Esses registros complementam o diagnóstico obrigatório de erros abaixo e continuam sujeitos à exclusão de health checks.

## Health checks

- **NÃO REGISTRAR LOGS DE HEALTH CHECKS.** Excluir `/actuator/health` e seus subcaminhos, incluindo readiness e liveness, de logs de acesso, filtros de requisição, interceptadores e registros automáticos de entrada e saída.
- Não registrar chamadas, respostas, payloads, duração ou exceções produzidas exclusivamente pela execução de um health check, em nenhum nível. A exclusão deve considerar o caminho mesmo quando houver query string.
- Acompanhar a saúde pelo estado do endpoint, métricas e alertas. Manter o health check sem chamadas à API-Futebol. Falhas de integração observadas em operações da aplicação fora do health check continuam sujeitas ao registro obrigatório abaixo.

## Erros de integração

- **SEMPRE REGISTRAR ERROS DE INTEGRAÇÃO**, incluindo falhas tratadas, retries e fallbacks, na fronteira que possui o contexto do provedor. No projeto, os adaptadores ficam no `app`; não adicionar dependência de logging de infraestrutura ao `domain`.
- Registrar provedor, operação, tipo de erro, identificador de correlação e **mensagem original do erro, sanitizada**. Preservar o significado e o texto seguro da mensagem do provedor ou da exceção; não substituí-la apenas por um texto genérico como `Erro na integração`.
- Em integrações HTTP, **registrar o HTTP status efetivamente recebido do provedor** no campo `httpStatus`. Não confundir esse status com o status retornado pela nossa API ao cliente. Registrar também o método e o caminho seguro, sem credenciais ou query string sensível.
- Se não houver resposta HTTP, como em timeout, falha de DNS, conexão recusada ou falha de TLS, registrar `httpStatus=sem_resposta` e a causa sanitizada. Não inventar um código HTTP. Em falhas de leitura ou desserialização após receber uma resposta, preservar o status recebido, inclusive quando for `2xx`.
- Quando o provedor não fornecer mensagem, registrar explicitamente essa ausência e o tipo da falha. Se a mensagem contiver dados que não possam ser sanitizados com segurança, registrar `mensagemOriginal=[OMITIDA_POR_SEGURANCA]`; a proteção de dados sensíveis tem prioridade.
- Em retries, registrar cada tentativa com seu número e resultado. Após esgotar as tentativas, registrar a falha definitiva em `ERROR`. Não aplicar amostragem ou supressão que elimine o registro obrigatório dos erros de integração.
- Registrar o erro uma vez por tentativa na camada responsável. Ao propagar ou traduzir a exceção, preservar a causa para tratamento interno sem repetir o mesmo evento no controller ou handler.
- Incluir stack trace somente quando acrescentar informação útil e houver garantia de que mensagens, causas e detalhes estejam sanitizados. As respostas HTTP ao cliente devem usar mensagens seguras do contrato da aplicação, sem repassar diretamente a mensagem do provedor.

Exemplos ilustrativos, com mensagens fictícias e sem valores sensíveis:

```text
ERROR evento=falha_integracao provedor=api-futebol operacao=consultar_partidas metodo=GET caminho=/partidas httpStatus=503 tentativa=1 duracaoMs=840 correlacaoId=exemplo-001 tipoErro=resposta_http mensagemOriginal="Serviço temporariamente indisponível"
ERROR evento=falha_integracao provedor=api-futebol operacao=consultar_partidas metodo=GET caminho=/partidas httpStatus=sem_resposta tentativa=1 duracaoMs=2000 correlacaoId=exemplo-002 tipoErro=timeout mensagemOriginal="Tempo limite de leitura excedido"
```

## Verificação

- Ao implementar ou alterar logging, verificar os registros emitidos nos cenários de falha de integração: mensagem original sanitizada, status do provedor e ausência de resposta HTTP.
- Verificar request/response em INFO, corpo completo e inalterado na integração, remoção de segredos em URLs/headers/campos aninhados, correlação e omissões por formato ou limite. Comprovar que cache e bloqueios locais não geram registros fictícios de envio.
- Usar valores exclusivamente fictícios nos testes e comprovar que dados sensíveis não aparecem nas mensagens, causas, stack traces nem respostas de erro.
- Verificar que chamadas ao health e a seus subcaminhos não geram logs, incluindo respostas de falha e chamadas com query string.
- Executar os testes afetados da camada apropriada conforme [unit-tests.md](unit-tests.md) e [e2e-tests.md](e2e-tests.md). Para alterações apenas nesta documentação, conferir conteúdo, caminhos e links.
