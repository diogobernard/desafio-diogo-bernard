# Padrões de codificação

## Escopo

Aplicar ao código Kotlin dos módulos `app` e `domain`, em conjunto com [kotlin.md](kotlin.md), [folder-structure.md](folder-structure.md), [logs.md](logs.md) e as rules de testes. Para Gradle Kotlin DSL, seguir também [gradle.md](gradle.md). Para documentar a API HTTP, seguir [spring-rest-docs.md](spring-rest-docs.md); nunca usar anotações do Swagger/OpenAPI no código.

Preservar o pacote-base `com.db.project_mister_study` e a dependência `app → domain`. Os exemplos são ilustrativos; cada trecho representa o arquivo e a camada indicados, sem exigir a criação dos recursos exemplificados na aplicação.

## Não inserir comentários desnecessários

Escrever código que explique sua intenção com nomes expressivos, contratos claros e funções pequenas. Não inserir comentários que repitam o código. Comentários são permitidos somente quando absolutamente necessários para explicar uma restrição, uma expressão complexa ou uma decisão técnica que não possa ser expressa pelos nomes.

```kotlin
fun identificadorValido(atletaId: Long): Boolean = atletaId > 0

fun possuiNomeDeExibicao(nome: String): Boolean = nome.isNotBlank()
```

Documentação de contratos deve explicar comportamento, pré-condições e significado dos dados, sem narrar cada instrução do método.

## Limitar classes e arquivos Kotlin a 150 linhas e 200 para testes

Classes e arquivos `.kt` devem ter, no máximo, 150 linhas, para testes pode ser mais flexível até 200 linhas. Ao atingir o limite, extrair responsabilidades coesas para classes, funções ou arquivos próprios. O limite considera o arquivo completo, incluindo imports e linhas em branco; não se aplica a arquivos Markdown.

Não dividir uma responsabilidade arbitrariamente para satisfazer o número de linhas. Separar, por exemplo, um caso de uso, um cliente HTTP, seus DTOs e uma entidade de persistência, respeitando as camadas do projeto.

Exemplo de caso de uso pequeno no `domain`, dependente de contratos:

```kotlin
interface ListarFavoritos {
    fun executar(): List<Favorito>
}

interface ListarFavoritosGateway {
    fun listar(): List<Favorito>
}

class ListarFavoritosUseCase(
    private val favoritos: ListarFavoritosGateway,
) : ListarFavoritos {
    override fun executar(): List<Favorito> = favoritos.listar()
}
```

Os três tipos do exemplo pertencem a arquivos próprios. Aplicar a organização ao código novo e às responsabilidades alteradas, mantendo o trabalho solicitado focado.

## Limitar métodos e funções a 30 linhas

Métodos e funções devem ter, no máximo, 30 linhas, incluindo a assinatura. Extrair comportamentos maiores em funções com uma responsabilidade clara. Não esconder um fluxo extenso em uma única expressão ou linha.

```kotlin
fun validarIdentificador(atletaId: Long): Long {
    require(atletaId > 0) { "O identificador do atleta deve ser positivo" }
    return atletaId
}
```

As funções extraídas também devem respeitar esse limite. Não criar helpers genéricos para reduzir a contagem de linhas.

## Foramtação de código

Evitar:
```kotlin
val solicitacao =
            solicitarEmissao
                .executar(
                    ComandoEmissao(
                        clienteId = ClienteId(clienteId),
                        chave = chaveId,
                        pedido = request.toDomain(),
                    ),
                ).solicitacao
```

Preferir:
```kotlin
val solicitacao = solicitarEmissao.executar(
    ComandoEmissao(
        clienteId = ClienteId(clienteId),
        chave = chaveId,
        pedido = request.toDomain(),
    ),
).solicitacao
```

## Formatar código para linhas muitos extensas

Evitar:

```kotlin
override fun execute(transaction: Transaction): CreatedTransaction {
    val ids = identifiers.reservePair()
    val receivable = calculator.calculate(transaction.value, transaction.method, ids, OffsetDateTime.now(clock))
    val createdTransaction = transaction.toTransaction(ids.transactionId)
    saveTransaction(createdTransaction)
    saveReceivable(receivable, ids.transactionId)
    return CreatedTransaction(createdTransaction, receivable)
}

Transaction(
    id = null,value = BigDecimal(input.value).setScale(2, RoundingMode.UNNECESSARY),description = input.description,
    method = PaymentMethod.entries.first { it.code == input.method },lastFourDigits = card.lastFourDigits,
    holderName = input.cardHolderName,expiration = parseExpiration(input.cardExpirationDate),
    cvv = Cvv(input.cardCvv),fullCardNumber = card,
)

@Bean
fun topicoNotas(properties: NotaFiscalProperties): NewTopic = topico(properties.kafka.topicoNotas, properties.kafka.replicacao)
```

Preferir:

```kotlin
override fun execute(transaction: Transaction): CreatedTransaction {
    val ids = identifiers.reservePair()
    val receivable = calculator.calculate(
        transaction.value,
        transaction.method,
        ids,
        OffsetDateTime.now(clock),
    )
    val createdTransaction = transaction.toTransaction(ids.transactionId)
    saveTransaction(createdTransaction)
    saveReceivable(receivable, ids.transactionId)
    return CreatedTransaction(createdTransaction, receivable)
}

Transaction(
    id = null,
    value = BigDecimal(input.value).setScale(2, RoundingMode.UNNECESSARY),
    description = input.description,
    method = PaymentMethod.entries.first { it.code == input.method },
    lastFourDigits = card.lastFourDigits,
    holderName = input.cardHolderName,
    expiration = parseExpiration(input.cardExpirationDate),
    cvv = Cvv(input.cardCvv),
    fullCardNumber = card,
)

@Bean
fun topicoNotas(properties: NotaFiscalProperties): NewTopic = 
    topico(properties.kafka.topicoNotas, properties.kafka.replicacao)
```

## Preferir cláusulas de guarda

Não aninhar mais de três níveis de `if`/`else`. Usar `require`, cláusulas de guarda e retornos antecipados para encerrar casos inválidos ou ausentes antes do fluxo principal. Capturar somente exceções esperadas e preservar suas causas.

Evitar:

```kotlin
fun nomeParaExibicao(nome: String?): String? {
    if (nome != null) {
        if (nome.isNotBlank()) {
            return nome.trim()
        }
    }
    return null
}
```

Preferir:

```kotlin
fun nomeParaExibicao(nome: String?): String? {
    if (nome.isNullOrBlank()) return null
    return nome.trim()
}
```

Não usar um retorno antecipado para transformar falhas de integração em sucesso, lista vazia ou score zero.

## Limitar parâmetros a três

Evitar métodos e funções com mais de três parâmetros. Agrupar informações do mesmo contexto em um objeto nomeado, sem reunir dados sem relação apenas para esconder a assinatura. Em construtores de serviços, muitas dependências indicam uma possível mistura de responsabilidades.

```kotlin
data class ConsultaPartidas(
    val periodo: PeriodoConsulta,
    val prazo: PrazoConsulta,
)

interface ConsultarPartidas {
    fun executar(consulta: ConsultaPartidas): SnapshotCampeonato
}
```

Campos de um `data class` que representa um contrato HTTP, um modelo ou um registro persistido seguem a estrutura real desse contrato; não criar agrupamentos artificiais para limitar seu construtor a três campos.

## Evitar linhas em branco dentro de funções

Organizar métodos de produção pela extração de responsabilidades e por nomes claros, evitando linhas em branco dentro do corpo. Manter linhas em branco entre membros de classes e entre funções de um arquivo.

```kotlin
fun nomesParaExibicao(nomes: List<String>): List<String> {
    val nomesValidos = nomes.filter(String::isNotBlank)
    val nomesNormalizados = nomesValidos.map(String::trim)
    return nomesNormalizados.distinct()
}
```

Em testes, manter a separação entre preparação, ação e verificação com linhas em branco, conforme [unit-tests.md](unit-tests.md) e [e2e-tests.md](e2e-tests.md).

## Extrair números e strings mágicos

Extrair regras de negócio, limites, códigos e chaves para constantes com nomes que expliquem seu significado. Usar o escopo mais próximo da responsabilidade: `const val` para constantes de compilação e `val` para objetos imutáveis como formatadores.

```kotlin
private const val LIMITE_DIAS_CONSULTA = 31L
private const val MENSAGEM_PERIODO_EXCEDIDO = "O período deve conter no máximo 31 dias"

fun validarDuracaoPeriodo(inicio: LocalDate, fim: LocalDate) {
    require(!fim.isBefore(inicio)) { "A data final deve ser igual ou posterior à inicial" }
    val diasInclusivos = ChronoUnit.DAYS.between(inicio, fim) + 1
    require(diasInclusivos <= LIMITE_DIAS_CONSULTA) { MENSAGEM_PERIODO_EXCEDIDO }
}
```

Usar enums e constantes de bibliotecas quando já representarem o conceito, como `HttpStatus.NOT_FOUND`. Não inventar uma configuração dinâmica para um valor que pertence à versão fixa de uma regra de negócio.

## Declarar variáveis perto do uso

Declarar variáveis no menor escopo possível e próximas da operação que as utiliza. Preferir `val`; usar `var` somente quando a mudança de estado for necessária. Evitar preparar dados que uma cláusula de guarda pode tornar dispensáveis.

```kotlin
class BuscarFavoritoUseCase(private val favoritos: BuscarFavoritoGateway) {
    fun executar(atletaId: Long): Favorito? {
        require(atletaId > 0) { "O identificador do atleta deve ser positivo" }
        val favorito = favoritos.buscar(atletaId) ?: return null
        return favorito
    }
}
```

## Aplicar SOLID

Aplicar os cinco princípios a responsabilidades e contratos concretos do projeto. Não criar hierarquias, classes base ou abstrações sem um comportamento que as justifique.

| Princípio | Diretriz | Exemplo no projeto |
| --- | --- | --- |
| **S — responsabilidade única** | Cada classe tem uma responsabilidade e um motivo principal para mudar. | Controller cuida do protocolo; caso de uso coordena regras; cliente consulta o provedor; entidade representa persistência. |
| **O — aberto para extensão** | Acrescentar comportamentos por novos contratos ou implementações, sem espalhar condicionais por consumidores. | Uma nova versão da pontuação implementa o contrato de cálculo e preserva a versão anterior. |
| **L — substituição de Liskov** | Implementações da mesma interface preservam pré-condições, resultados, ausência e falhas do contrato. | Um gateway de teste e o adaptador real mantêm a distinção entre lista vazia, cobertura negada e erro da origem. |
| **I — segregação de interfaces** | Expor contratos pequenos para as operações de que cada consumidor precisa. | Uma consulta de favoritos recebe uma interface de leitura, sem métodos esportivos ou de gravação que não utiliza. |
| **D — inversão de dependências** | Casos de uso dependem de abstrações do domínio; detalhes de infraestrutura implementam essas abstrações no `app`. | A implementação PostgreSQL de um gateway é fornecida ao caso de uso por composição, sem importar JPA no `domain`. |

Exemplo de responsabilidade única, segregação e inversão de dependências:

```kotlin
interface BuscarFavoritoGateway {
    fun buscar(atletaId: Long): Favorito?
}

interface BuscarFavorito {
    fun executar(atletaId: Long): Favorito?
}

class BuscarFavoritoUseCase(
    private val favoritos: BuscarFavoritoGateway,
) : BuscarFavorito {
    override fun executar(atletaId: Long): Favorito? {
        require(atletaId > 0) { "O identificador do atleta deve ser positivo" }
        return favoritos.buscar(atletaId)
    }
}
```

Para o princípio aberto/fechado, selecionar a implementação pela composição da aplicação. Para substituição, proteger o comportamento comum com testes de contrato. Não implementar uma interface de leitura lançando `UnsupportedOperationException` porque uma das operações não foi suportada.

## Sempre usar interfaces nas assinaturas e na injeção de dependências

**Sempre usar interfaces para dependências de comportamento recebidas ou expostas por funções, serviços, casos de uso, gateways, clientes e construtores.** Não exigir a implementação concreta quando o consumidor precisa apenas de seu contrato.

Expor também coleções pelas interfaces `List`, `Set`, `Map` ou `Collection`, sem exigir `ArrayList`, `HashSet` ou `HashMap` nas assinaturas. Instanciar implementações concretas somente onde elas são construídas e configuradas.

Evitar:

```kotlin
class ListarFavoritosUseCase(
    private val favoritos: PostgresFavoritosGateway,
)

interface OrdenarFavoritos {
    fun ordenar(favoritos: ArrayList<Favorito>): ArrayList<Favorito>
}
```

Preferir:

```kotlin
class ListarFavoritosUseCase(
    private val favoritos: ListarFavoritosGateway,
) : ListarFavoritos {
    override fun executar(): List<Favorito> = favoritos.listar()
}

fun ordenar(favoritos: List<Favorito>): List<Favorito> =
    favoritos.sortedWith(
        compareByDescending<Favorito> { it.favoritadoEm }.thenBy { it.atletaId },
    )
```

Essa regra trata contratos de comportamento e coleções. Tipos de valor e dados, como `Long`, `String`, `Instant`, modelos, DTOs e entidades, continuam sendo tipos concretos nas assinaturas; não criar interfaces para cada campo de um `data class`. Fábricas e configuração podem construir classes concretas e devem expor o contrato disponível da biblioteca, como `RestClient`, que já é uma interface.

## Usar generics somente quando extremamente necessário

**SOMENTE QUANDO FOR EXTREMAMENTE NECESSÁRIO** criar funções, classes ou interfaces próprias com parâmetros de tipo (`<T>`, `<R>` etc.). Preferir contratos específicos, com entradas e saídas que expressem a operação real.

Não criar um client, service, gateway, mapper ou repositório base genérico para reutilizar poucas linhas de código. Evitar `Any`, casts não verificados, `Class<T>`, `reified`, múltiplos parâmetros de tipo e abstrações que transfiram ao consumidor a escolha do tipo de resposta.

Evitar:

```kotlin
interface ClienteGenerico {
    fun <T> consultar(caminho: String, tipo: Class<T>): T
}
```

Preferir:

```kotlin
interface AtletasClient {
    fun consultarAtleta(atletaId: Long): AtletaApiResponse
}
```

O uso de tipos parametrizados exigidos por coleções e frameworks, como `List<Favorito>` ou `JpaRepository<FavoritoEntity, Long>`, é permitido. Isso não autoriza criar novas abstrações genéricas. Quando uma abstração própria for indispensável, documentar a necessidade concreta, usar o menor número de parâmetros de tipo e testar seus contratos sem casts inseguros.

## Converter com toDomain e fromDomain no próprio tipo de borda

**Não criar classes ou componentes Mapper.** Colocar as conversões nas `data class` que representam requests, responses, payloads externos e registros de persistência, ou na própria entidade quando sua modelagem JPA exigir uma classe regular.

- `fun toDomain()`: método de instância que converte o objeto de borda para o domínio.
- `fun fromDomain(...)`: fábrica dentro de `companion object` que constrói o objeto de borda a partir do domínio.
- Implementar somente as direções necessárias; um request não precisa de uma fábrica de response artificial.
- Passar explicitamente o contexto necessário, como o instante de obtenção; não consultar banco, HTTP ou relógio dentro da conversão.
- Conversões de objetos aninhados usam os métodos desses próprios tipos.

Usar `toDomain` e `fromDomain` em camelCase, conforme a convenção Kotlin, sem criar interfaces genéricas `ToDomain<T>` ou `FromDomain<T>`.

Exemplo de DTO HTTP no `app/entrypoint/model`, usando o modelo `Equipe` do domínio:

```kotlin
package com.db.project_mister_study.entrypoint.model

import com.db.project_mister_study.model.partida.Equipe

data class EquipeResponse(val timeId: Long, val nome: String) {
    fun toDomain(): Equipe = Equipe(timeId = timeId, nome = nome)

    companion object {
        fun fromDomain(equipe: Equipe): EquipeResponse = EquipeResponse(
            timeId = equipe.timeId,
            nome = equipe.nome,
        )
    }
}
```

Exemplo de entidade simples, sem associações, com identificador natural já conhecido. A tabela é ilustrativa e não representa uma nova migração do projeto:

```kotlin
package com.db.project_mister_study.repository.entity

import com.db.project_mister_study.model.partida.Equipe
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "equipe_exemplo")
data class EquipeEntity(
    @field:Id
    var timeId: Long = 0,
    @field:Column(nullable = false)
    var nome: String = "",
) {
    fun toDomain(): Equipe = Equipe(timeId = timeId, nome = nome)

    companion object {
        fun fromDomain(equipe: Equipe): EquipeEntity = EquipeEntity(
            timeId = equipe.timeId,
            nome = equipe.nome,
        )
    }
}
```

Em entidades JPA, avaliar identidade, proxies, associações e o comportamento gerado de `equals`, `hashCode`, `copy` e `toString`, conforme [kotlin.md](kotlin.md). Não usar entidades mutáveis como chaves de coleções hash nem copiar entidades gerenciadas com `copy`. Usar classe regular quando a semântica de valor de uma `data class` não representar corretamente a entidade, mantendo `toDomain` e `fromDomain` nela. Preservar os plugins Kotlin/Spring/JPA configurados no build.

O domínio nunca importa DTOs HTTP, entidades JPA, Jackson ou clientes do `app`. Conversões preservam precisão, nulabilidade e significado dos dados. Não truncar nomes, preencher lacunas com zero ou descartar dados sem uma regra de negócio explícita.

## Criar clientes específicos, com operações e respostas tipadas

Criar clientes por integração e responsabilidade. Os métodos expressam operações específicas, como `consultarAtleta`, `consultarCampeonato` e `consultarPartida`, recebendo seus identificadores e retornando DTOs próprios.

**Não expor um método genérico `get(path, politica, prazo): JsonNode`, nem um método com `<T>` e `Class<T>`.** O consumidor não escolhe caminhos arbitrários, políticas de erro HTTP ou tipos de resposta. Definir essas decisões no cliente da operação.

Evitar:

```kotlin
interface ApiClientGenerico {
    fun get(
        path: String,
        politicaNaoEncontrado: PoliticaNaoEncontrado,
        prazo: PrazoConsulta,
    ): JsonNode
}
```

Preferir o fluxo direto de `RestClient`, com interface de operação e DTO tipado. O exemplo mostra o núcleo da consulta; o `RestClient` recebido deve ser o bean configurado para a API-Futebol:

```kotlin
package com.db.project_mister_study.gateway.middleware.apifutebol

import com.db.project_mister_study.exception.DadosOrigemInvalidosException
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

interface AtletasClient {
    fun consultarAtleta(atletaId: Long): AtletaApiResponse
}

@Service
class ApiFutebolAtletasClient(
    private val restClient: RestClient,
) : AtletasClient {
    override fun consultarAtleta(atletaId: Long): AtletaApiResponse {
        require(atletaId > 0) { "O identificador do atleta deve ser positivo" }
        return restClient.get()
            .uri("/atletas/{atletaId}", atletaId)
            .retrieve()
            .body(AtletaApiResponse::class.java)
            ?: throw DadosOrigemInvalidosException()
    }
}
```

O DTO externo pertence ao `app`, usa os nomes reais do provedor e mantém suas conversões no próprio tipo. Exemplos de campos de identidade, com `posicao` omitida apenas deste trecho didático:

```kotlin
import com.fasterxml.jackson.annotation.JsonProperty

data class AtletaApiResponse(
    @param:JsonProperty("atleta_id")
    val atletaId: Long,
    @param:JsonProperty("nome_popular")
    val nomePopular: String,
)
```

As anotações de Jackson 3 continuam no pacote `com.fasterxml.jackson.annotation`, conforme a [documentação de migração do Jackson](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0); os demais componentes usados pelo projeto estão em `tools.jackson`. Na implementação real, representar os campos necessários e suas ausências sem descartar posição ou cobertura disponível.

Configurar base URL, autenticação, headers, transporte, limites de payload e interceptadores em `app/config`, reutilizando o bean e a infraestrutura existentes. Compartilhar configuração e políticas transversais não significa criar uma API de consultas genérica.

Preservar contagem de cota, deadline total, timeouts, cancelamento, sanitização, logs e métricas exigidos pelo projeto. Quando a operação depender de `PrazoConsulta`, incluí-lo explicitamente na assinatura específica e aplicá-lo antes e durante a chamada. O exemplo reduzido não dispensa essas garantias. Traduzir os erros conforme o contrato da operação: um 404 de atleta não tem o mesmo significado que cobertura de campeonato indisponível.

## Manter dados sensíveis fora do código

Nunca colocar chaves de API, tokens, senhas ou credenciais em código, exemplos, arquivos versionados, logs, respostas HTTP ou evidências. Usar variáveis de ambiente e propriedades tipadas no `app`, mantendo `.env` fora do Git e do contexto de build.

Exemplo para `.env.example`, somente com placeholder:

```env
FUTEBOL_API_KEY=<CHAVE_FICTICIA>
```

Validar a configuração sem revelar seu valor:

```kotlin
fun validarCredencialConfigurada(apiKey: String) {
    require(apiKey.isNotBlank()) { "FUTEBOL_API_KEY não configurada" }
}
```

Seguir integralmente [logs.md](logs.md), incluindo request e response HTTP sanitizados, exclusão de health checks e registro de falhas com mensagem original sanitizada e status da origem quando houver resposta.

## Verificação

Ao implementar esses padrões, verificar os limites de tamanho, a coesão das responsabilidades, os contratos por interface, a necessidade de generics, as conversões locais e os clientes tipados. Mudanças de comportamento exigem testes significativos na camada adequada, conforme o `AGENTS.md`.

Para alterações somente nesta documentação, conferir exemplos, conteúdo, caminhos, links e `git diff --check`. Não afirmar que trechos ilustrativos foram compilados quando a validação foi apenas documental.

## Referências

- Base de adaptação: `/Users/diogobernard/dev/IA/workspace/formacao-inteligencia-artificial/rules/example_3/.agents/rules/code-standards.md`.
- [Kotlin: interfaces](https://kotlinlang.org/docs/interfaces.html).
- [Kotlin: data classes e membros gerados](https://kotlinlang.org/docs/data-classes.html).
- [Spring Framework: RestClient e respostas tipadas](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html).
