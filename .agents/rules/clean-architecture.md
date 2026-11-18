# Clean Architecture

## Escopo e referências

Aplicar ao criar, alterar e revisar código dos módulos `app` e `domain`. Preservar o pacote-base `com.itau.desafio_diogo_bernard`, a estrutura implementada e os requisitos do produto.

Esta rule sintetiza princípios pesquisados nas fontes originais e define sua aplicação neste repositório. As decisões sobre pacotes, interfaces, conversões e testes seguem também [code-standards.md](code-standards.md), [folder-structure.md](folder-structure.md), [kotlin.md](kotlin.md), [unit-tests.md](unit-tests.md), [e2e-tests.md](e2e-tests.md) e [spring-rest-docs.md](spring-rest-docs.md). Para os caminhos atuais, consultar [AGENTS.md](../../AGENTS.md).

Os exemplos Kotlin são ilustrativos, com tipos e operações do projeto. Cada bloco representa um arquivo ou trecho da camada indicada; não exige criar novas classes na aplicação. Imports e membros sem relação com o exemplo podem estar omitidos.

## 1. Direcionar dependências para as regras de negócio

Dependências de código apontam para o núcleo: regras internas não conhecem mecanismos externos. O fluxo de execução pode sair do núcleo por interfaces implementadas na infraestrutura. Esse é o princípio central da [regra de dependência de Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html).

Neste projeto:

- Manter somente `app → domain` como dependência entre módulos.
- Não importar classes de `app`, Spring, JPA, HTTP ou Jackson em `domain`.
- Não retornar tipos de infraestrutura em contratos do domínio, mesmo que a implementação esteja no `app`.
- Manter a direção também dentro de `domain`: modelos e regras gerais não dependem de casos de uso específicos.
- Verificar imports, assinaturas e dependências Gradle; a localização de um arquivo, sozinha, não garante isolamento.

```text
Dependências de código:
controller (app) ────────────────→ contrato de caso de uso (domain)
caso de uso (domain) ────────────→ contrato de gateway (domain)
implementação de gateway (app) ─→ contrato de gateway (domain)

Execução:
controller → caso de uso → implementação injetada → banco/API
```

Evitar no `domain`:

```kotlin
import com.db.project_mister_study.repository.FavoritoRepository

class ListarFavoritosUseCase(private val favoritos: FavoritoRepository)
```

O caso de uso deve receber `ListarFavoritosGateway`, definido no próprio `domain`.

## 2. Separar responsabilidades das camadas

Clean Architecture distingue regras gerais, casos de uso, adaptação de interfaces e mecanismos externos. A [descrição original das camadas](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) não exige quatro módulos físicos. A aplicação local é:

| Responsabilidade | Local neste projeto | Exemplo |
| --- | --- | --- |
| Modelos, invariantes e políticas de negócio | `domain/model`, `domain/exception` | `PeriodoConsulta`, `Equipe`, `RegraPontuacaoV1` |
| Coordenação de uma operação do produto | `domain/usecase` | `SalvarFavoritoUseCase`, `ConsultarPartidasUseCase` |
| Contratos de interação com o núcleo | `domain/usecase`, `domain/gateway` | `ListarFavoritos`, `ListarFavoritosGateway` |
| Tradução de HTTP, dados externos e persistidos | `app/entrypoint`, `app/gateway`, `app/repository/entity` | Controllers, `ApiFutebolGateway`, `FavoritoEntity` |
| Frameworks, acesso técnico e composição | `app/config`, `app/repository`, `app/gateway/middleware/apifutebol` | Beans Spring, repositórios JPA, cliente HTTP |

Os caminhos Kotlin da tabela são relativos a `src/main/kotlin/com/db/project_mister_study` do módulo indicado. Um adaptador pode usar o framework necessário à sua função; o isolamento obrigatório protege o `domain`.

Uma entidade de negócio não é uma entidade JPA. `Favorito` representa o conceito do produto; `FavoritoEntity` representa seu armazenamento.

O módulo `domain` é o núcleo e contém duas camadas: a de **domínio** (`model`, `exception`, `gateway`, `usecase/validations`) e a de **aplicação** (`usecase/<funcionalidade>`). Essa separação corresponde a Entities × Use Cases na Clean Architecture e a Domain × Application no DDD. As duas camadas ficam livres de framework; a distinção é feita por pacote, conforme [folder-structure.md](folder-structure.md).

## 3. Manter invariantes no domínio e coordenação nos casos de uso

A arquitetura deve comunicar e sustentar as operações do produto, conforme [Screaming Architecture](https://blog.cleancoder.com/uncle-bob/2011/09/30/Screaming-Architecture.html). Neste backend, regras de período, elegibilidade e pontuação pertencem ao `domain`.

- Validar invariantes no modelo ou política responsável, para protegê-las em qualquer entrada.
- Usar casos de uso para coordenar gateways e regras; não concentrar todas as regras em controllers ou gateways.
- Manter o cálculo de score dependente somente do snapshot normalizado, sem banco, cliente HTTP ou relógio.
- Preservar décimos inteiros, versão da regra, contribuições e estados de cobertura definidos na TechSpec.

Exemplo de objeto do domínio com invariantes, em `domain/model/partida`:

```kotlin
data class Equipe(val timeId: Long, val nome: String) {
    init {
        require(timeId > 0) { "O identificador da equipe deve ser positivo" }
        require(nome.isNotBlank()) { "O nome da equipe é obrigatório" }
    }
}
```

Esse modelo continua válido quando criado por um teste, um adaptador de API ou outro caso de uso. Uma anotação de validação HTTP não deve ser sua única proteção.

## 4. Definir portas pelo que o caso de uso precisa

Uma porta descreve uma interação da aplicação; adaptadores realizam essa interação para uma tecnologia. Esse princípio vem de [Ports & Adapters, de Alistair Cockburn](https://alistair.cockburn.us/hexagonal-architecture), arquitetura relacionada à Clean Architecture.

Neste projeto, definir contratos de entrada em `domain/usecase` e contratos de saída em `domain/gateway`. Receber dependências de comportamento por interface, conforme `code-standards.md`.

Exemplo de porta de entrada, em `domain/usecase/favoritos/ListarFavoritos.kt`:

```kotlin
interface ListarFavoritos {
    fun executar(): List<Favorito>
}
```

Exemplo de porta de saída, em `domain/gateway/ListarFavoritosGateway.kt`:

```kotlin
interface ListarFavoritosGateway {
    fun listar(): List<Favorito>
}
```

Exemplo de coordenação, em `domain/usecase/favoritos/ListarFavoritosUseCase.kt`:

```kotlin
class ListarFavoritosUseCase(
    private val favoritos: ListarFavoritosGateway,
) : ListarFavoritos {
    override fun executar(): List<Favorito> = favoritos.listar()
}
```

O contrato de leitura permite cumprir a exigência de listar favoritos somente pelo banco. Não adicionar uma consulta à API-Futebol a esse fluxo.

Expressar operações e garantias necessárias, como `salvarUmaVez`, em vez de copiar a API de um framework. Não expor `JpaRepository`, `ResponseEntity`, `JsonNode`, SQL ou caminhos HTTP nas portas do domínio. Não criar gateways genéricos com `Any` ou métodos `<T>` para integrar serviços distintos.

Manter a porta de entrada e a implementação do caso de uso no `domain`; implementar no `app` somente as portas de saída. Um caso de uso não chama outro caso de uso: extrair a lógica compartilhada para uma política do domínio ou, para uma responsabilidade transversal como idempotência, envolver o contrato do caso de uso com um decorador em `domain/usecase`, cujas portas são implementadas no `app`.

Distinguir dois tipos de responsabilidade transversal. As que alteram a semântica do contrato, como a idempotência (mesma chave, mesmo resultado, `409`, `422`), são regra da aplicação e ficam em `domain/usecase`. As proteções técnicas de infraestrutura, como limite de concorrência diante de uma dependência saturável, não mudam regra de negócio e ficam em `app/config/resilience`, como decoradores da porta de entrada aplicados na composição. O `app` pode implementar esse decorador técnico, nunca a lógica do caso de uso; a posição na composição (por exemplo, dentro do decorador de idempotência) é o que garante a ordem exigida pelos requisitos.

## 5. Converter dados nas fronteiras

Controllers traduzem entradas do protocolo para dados simples e devolvem respostas a partir do resultado do caso de uso. Essa separação é descrita em [Clean Architecture, de Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2011/11/22/Clean-Architecture.html).

Neste repositório, colocar `toDomain` e `fromDomain` no próprio tipo de borda, conforme `code-standards.md`. O domínio não fornece métodos que importam DTOs HTTP ou entidades JPA.

Exemplo de resposta em `app/entrypoint/model/EquipeResponse.kt`:

```kotlin
data class EquipeResponse(val timeId: Long, val nome: String) {
    companion object {
        fun fromDomain(equipe: Equipe): EquipeResponse = EquipeResponse(
            timeId = equipe.timeId,
            nome = equipe.nome,
        )
    }
}
```

Exemplo de conversão na leitura persistida, como trecho de um gateway no `app`:

```kotlin
override fun listar(): List<Favorito> =
    repository.findAllByOrderByFavoritadoEmDescAtletaIdAsc()
        .map { it.toDomain() }
```

O gateway retorna `Favorito`, sem deixar entidades gerenciadas ou carregamento preguiçoso escaparem para o domínio. Conversões preservam identidade, nulabilidade, precisão e significado; não fazem chamadas HTTP, consultas ao banco ou leituras implícitas do relógio.

Na API-Futebol, manter DTOs tipados e a interpretação do formato externo no `app`. Entregar snapshots normalizados ao domínio. Campo individual ausente permanece desconhecido; total coletivo não vira estatística individual nem score zero.

## 6. Manter controllers restritos ao protocolo

No `app/entrypoint`, validar formato, parâmetros e conteúdo HTTP; delegar a operação ao contrato do caso de uso e converter o resultado. Não calcular score, executar SQL ou consultar diretamente o provedor no controller.

Exemplo de trecho do GET de favoritos; `listarFavoritos` é uma dependência do tipo `ListarFavoritos`:

```kotlin
@GetMapping
fun listar(request: HttpServletRequest): ListarFavoritosResponse {
    rejeitarParametros(request)
    return ListarFavoritosResponse.fromDomain(listarFavoritos.executar())
}
```

`rejeitarParametros` trata uma exigência do contrato HTTP. A regra de período inclusivo de 1 a 31 dias permanece no domínio, além da validação de formato da entrada.

Controllers e DTOs não recebem anotações do Swagger/OpenAPI. A documentação do contrato HTTP é gerada por testes com Spring REST Docs, conforme [spring-rest-docs.md](spring-rest-docs.md).

Definir respostas de sucesso no controller e traduzir falhas para status HTTP em `app/entrypoint/handlers`. Exceções do domínio expressam o motivo da falha sem depender de `HttpStatus`. O cliente externo interpreta status do provedor, preserva a causa e registra erros conforme [logs.md](logs.md); falhas externas não são convertidas em listas vazias bem-sucedidas.

## 7. Compor dependências na borda da aplicação

Separar configuração de serviços de seu uso e receber dependências pelo construtor. A fundamentação está em [Dependency Injection, de Martin Fowler](https://martinfowler.com/articles/injection.html). Injeção facilita a composição; interfaces orientadas ao núcleo preservam a direção das dependências.

Configurar beans, implementações, credenciais e clientes em `app/config`. Instanciar o caso de uso nessa borda, expondo seu contrato:

```kotlin
@Configuration
class FavoritosConfiguration {
    @Bean
    fun listarFavoritosUseCase(
        favoritos: ListarFavoritosGateway,
    ): ListarFavoritos = ListarFavoritosUseCase(favoritos)
}
```

O exemplo mostra somente o bean de leitura. Preservar os outros beans e configurações existentes.

Não usar `ApplicationContext`, busca global de serviços ou criação de clientes concretos dentro dos casos de uso. Quando uma regra depender da hora, receber `Clock` pela composição; não ler a hora global implicitamente no cálculo ou na conversão.

## 8. Isolar mecanismos e preservar garantias do produto

Manter Spring, PostgreSQL, JPA, Flyway, Jackson, cache e comunicação com a API-Futebol no `app`. As tecnologias já adotadas continuam em uso; mudanças nelas devem ficar no escopo solicitado.

Definir a garantia de negócio no contrato e implementá-la no adaptador responsável:

| Garantia do produto | Responsabilidade do domínio | Mecanismo no `app` |
| --- | --- | --- |
| Um favorito por atleta, mesmo sob concorrência | Solicitar inclusão atômica por `salvarUmaVez` | Restrição única, tratamento de conflito e transação no banco |
| Score reproduzível e explicável | Calcular sobre snapshot normalizado e regra versionada | Obter e converter os dados do provedor |
| Consulta esportiva com limites operacionais | Respeitar o contrato de prazo recebido | Timeouts, cota, limites de payload e cache |
| Erros compreensíveis para o consumidor | Expressar motivos e estados do negócio | Sanitização, logs e tradução HTTP |

Não trocar a inclusão atômica por uma sequência de consulta e inserção sem proteção no banco. Não mover anotações `@Transactional` para `domain` para resolver a coordenação; manter o mecanismo no `app`, com uma operação de gateway que cumpra a garantia exigida.

## 9. Testar regras sem depender da infraestrutura

A substituição de adaptadores permite testar a aplicação isoladamente, conforme [Ports & Adapters](https://alistair.cockburn.us/hexagonal-architecture). Aplicar as suítes já existentes:

- `domain`: modelos, score, estados de cobertura e casos de uso com gateways simulados, sem Spring, Docker ou provedor real.
- `app:test`: conversões e comportamentos locais dos adaptadores.
- `app:integrationTest`: contratos dos adaptadores, persistência, transações e concorrência com a infraestrutura adequada.
- `app:e2eTest`: jornadas HTTP observáveis com PostgreSQL descartável e provedor simulado na fronteira externa.

Exemplo de teste de limite de uma regra real em `domain/src/test/kotlin/com/db/project_mister_study/model/partida`:

```kotlin
import com.db.project_mister_study.model.partida.PeriodoConsulta
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class PeriodoConsultaTest {
    @Test
    fun aceitaTrintaEUmDiasInclusivos() {
        val inicio = LocalDate.of(2026, 1, 1)
        val fim = inicio.plusDays(30)

        val periodo = PeriodoConsulta(inicio, fim)

        assertEquals(fim, periodo.dataFinal)
    }

    @Test
    fun rejeitaTrintaEDoisDiasInclusivos() {
        val inicio = LocalDate.of(2026, 1, 1)

        assertFailsWith<IllegalArgumentException> {
            PeriodoConsulta(inicio, inicio.plusDays(31))
        }
    }
}
```

Proteger também zero, score parcial e score indisponível, falha externa, período invertido e duplicidade concorrente de favoritos nas camadas adequadas. Um teste de contexto Spring não substitui essas verificações. Conferir os comandos atuais e a descoberta dos testes em [AGENTS.md](../../AGENTS.md).

## 10. Organizar pelo negócio e manter abstrações específicas

Usar nomes como `partidas`, `score` e `favoritos` para explicar as capacidades da aplicação, seguindo [Screaming Architecture](https://blog.cleancoder.com/uncle-bob/2011/09/30/Screaming-Architecture.html). Preservar as pastas estruturais atuais e criar novos subpacotes em minúsculas, sem novos underscores.

Cada interface deve expressar uma responsabilidade e seu comportamento. Conforme as regras locais, usar interfaces para dependências de comportamento; modelos, DTOs e tipos de valor continuam concretos.

Não criar um módulo para cada círculo, classes `BaseUseCase<T>` ou componentes Mapper para copiar o desenho arquitetural. Acrescentar abstrações quando houver uma responsabilidade identificável, mantendo os limites de tamanho e as restrições de generics de `code-standards.md`.

Ao revisar uma alteração, verificar a direção das dependências, os tipos que cruzam fronteiras, a localização das regras e os testes do comportamento afetado. Mudanças em um JSON externo ou em um mapeamento JPA devem poder ficar nos adaptadores quando o significado do negócio permanecer igual.

## Fontes primárias consultadas

Pesquisa realizada em 09/10/2026. Os textos abaixo fundamentam os princípios; exemplos e decisões locais foram adaptados ao projeto.

- Robert C. Martin — [The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html), 13/08/2012: regra de dependência e responsabilidades das camadas.
- Robert C. Martin — [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2011/11/22/Clean-Architecture.html), 22/11/2011: separação entre protocolo e regras de negócio.
- Robert C. Martin — [Screaming Architecture](https://blog.cleancoder.com/uncle-bob/2011/09/30/Screaming-Architecture.html), 30/09/2011: arquitetura orientada às capacidades e aos casos de uso.
- Alistair Cockburn — [Hexagonal Architecture / Ports & Adapters](https://alistair.cockburn.us/hexagonal-architecture), artigo original de 2005: portas, adaptadores e execução isolada.
- Martin Fowler — [Inversion of Control Containers and the Dependency Injection pattern](https://martinfowler.com/articles/injection.html), 23/01/2004: injeção e separação entre configuração e uso.
