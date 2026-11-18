# Regras para Kotlin

## Escopo

Aplicar ao criar, revisar ou alterar arquivos `*.kt` de produção e testes. Para scripts de build, consultar também `gradle.md`.

Os exemplos são ilustrativos. Adaptar nomes e contratos ao domínio; cada bloco pode representar um arquivo ou trecho independente.

## Estilo e nomenclatura

- Usar `PascalCase` para classes e interfaces, `camelCase` para funções e propriedades e `SCREAMING_SNAKE_CASE` para constantes.
- Usar nomes descritivos e manter o vocabulário já adotado pelo projeto.
- Nomear em inglês classes, interfaces, funções, métodos, propriedades, parâmetros e variáveis (`Invoice`, `IssuanceRequest`, `execute`, `findConsistent`, `taxRate`). Os contratos externos continuam em português e são fixados explicitamente na borda: `@JsonProperty("valor_frete")` nos DTOs e eventos, `@Column(name = "...")` nas entidades, nomes de tópicos, métricas, eventos de log e valores de enum (`POR_FORA`, `FISICA`), que fazem parte do JSON.
- Exceções em português: os pacotes, a classe `NotaFiscalProperties` e suas classes aninhadas (suas propriedades definem as chaves `nota-fiscal.*` e as variáveis de ambiente), os nomes de testes entre crases, conforme [unit-tests.md](unit-tests.md), e as mensagens exibidas ao cliente. Os exemplos das rules em português ilustram estrutura, não a língua dos identificadores.
- Nomear novos pacotes com letras minúsculas, sem underscores. Preservar os pacotes existentes; não renomeá-los incidentalmente.
- Seguir a formatação configurada no repositório. Na ausência de configuração, adotar quatro espaços, sem tabs, para código novo; evitar reformatar arquivos sem relação com a tarefa.
- Preferir inferência de tipos quando o significado for claro; explicitar tipos de retorno em APIs públicas e quando facilitar a leitura.
- Escrever funções pequenas, com responsabilidades claras. Comentários devem explicar decisões ou restrições relevantes.

Exemplo de nomenclatura, constante e retorno explícito:

```kotlin
package com.db.studykubernetes.pedido

const val LIMITE_DE_ITENS = 100

data class PedidoResumo(val id: Long, val quantidadeDeItens: Int)

fun podeAdicionarItem(pedido: PedidoResumo): Boolean =
    pedido.quantidadeDeItens < LIMITE_DE_ITENS
```

O pacote acima exemplifica novos nomes; não implica renomear o pacote existente `com.db.study_kubernetes`.

## Estado, coleções e nulabilidade

- Preferir `val`; usar `var` apenas quando a reatribuição fizer parte do comportamento esperado.
- Expor coleções por interfaces de leitura (`List`, `Set`, `Map`). Uma referência `val` e uma interface de leitura não garantem imutabilidade profunda; controlar referências mutáveis compartilhadas.
- Usar tipos anuláveis somente quando a ausência de valor fizer parte do contrato.
- Tratar valores ausentes com verificações explícitas, chamadas seguras (`?.`) e Elvis (`?:`), conforme o significado do domínio.
- Evitar `!!`. Validar pré-condições com `require`/`requireNotNull` e invariantes com `check`/`checkNotNull` quando uma falha explícita for apropriada.
- Ao consumir APIs Java, verificar contratos de nulabilidade e tratar tipos de plataforma nas fronteiras.

Exemplo de nulabilidade: evitar uma afirmação forçada quando existe um valor padrão no contrato.

```kotlin
// Evitar: falha quando o nome está ausente.
fun nomeParaExibicaoInseguro(nome: String?): String = nome!!.trim()

// Preferir: ausência e texto em branco têm tratamento explícito.
fun nomeParaExibicao(nome: String?): String =
    nome?.trim()?.takeIf { it.isNotEmpty() } ?: "Não informado"

// Quando a ausência viola uma pré-condição, falhar com mensagem clara.
fun validarIdentificador(id: Long?): Long {
    val identificador = requireNotNull(id) { "O identificador é obrigatório" }
    require(identificador > 0) { "O identificador deve ser positivo" }
    return identificador
}
```

Exemplo de coleção: retornar uma cópia da estrutura para não expor a lista interna. Os elementos deste exemplo também são valores imutáveis.

```kotlin
class HistoricoDeEventos {
    private val eventos = mutableListOf<String>()

    fun registrar(evento: String) {
        eventos.add(evento)
    }

    fun listar(): List<String> = eventos.toList()
}
```

## Código idiomático e modelagem

- Preferir expressões `if` e `when` para produzir valores. Usar `when` para múltiplas alternativas quando melhorar a leitura.
- Modelar alternativas fechadas com `sealed class` ou `sealed interface` e manter o tratamento exaustivo de `when` sem `else` desnecessário.
- Usar `data class` para objetos com semântica de valor, como DTOs. Avaliar quais propriedades participam de igualdade, cópia e representação textual.
- Usar parâmetros padrão e argumentos nomeados quando esclarecerem chamadas, principalmente com booleanos ou parâmetros do mesmo tipo.
- Preferir templates de string e funções de extensão pequenas e específicas.
- Usar `let`, `run`, `with`, `apply` e `also` conforme seu receptor e retorno; evitar encadeamentos que escondam o fluxo ou tornem `this` e `it` ambíguos.
- Escolher operações de coleção pela clareza; usar `Sequence` quando houver benefício concreto, considerando tamanho dos dados e operações intermediárias.
- Capturar exceções específicas e preservar sua causa; não transformar falhas inesperadas em resultados de sucesso.

Exemplo de DTO, alternativas fechadas e `when` exaustivo:

```kotlin
data class PedidoDto(val id: Long, val descricao: String)

sealed interface ResultadoDaBusca {
    data class Encontrado(val pedido: PedidoDto) : ResultadoDaBusca
    data object NaoEncontrado : ResultadoDaBusca
}

fun mensagemDoResultado(resultado: ResultadoDaBusca): String = when (resultado) {
    is ResultadoDaBusca.Encontrado -> "Pedido ${resultado.pedido.id} encontrado"
    ResultadoDaBusca.NaoEncontrado -> "Pedido não encontrado"
}
```

Exemplo de parâmetros padrão, argumentos nomeados e função de extensão:

```kotlin
data class OpcoesDeConsulta(
    val limite: Int = 20,
    val incluirInativos: Boolean = false,
)

fun String.normalizarDescricao(): String = trim().replace(Regex("\\s+"), " ")

val opcoes = OpcoesDeConsulta(limite = 50, incluirInativos = true)
val descricao = "  Pedido   prioritário  ".normalizarDescricao()
```

Exemplo de função de escopo: usar `apply` para configurar um objeto mutável e retornar esse objeto, sem escopos aninhados.

```kotlin
fun montarCabecalhos(token: String?): Map<String, String> {
    val tokenValido = token?.trim()?.takeIf { it.isNotEmpty() }
    return mutableMapOf("Accept" to "application/json").apply {
        if (tokenValido != null) {
            put("Authorization", "Bearer $tokenValido")
        }
    }.toMap()
}
```
Exemplo de função com retorno: Para melhorar a legibilidade do código, sempre colocar o tipo de retorno a menos que seja explícito.

Evitar
```kotlin
fun statusHttp(erro: ResponseStatusException, request: HttpServletRequest) =
    when (erro.statusCode.value()) {
        HttpStatus.UNAUTHORIZED.value() -> {
            resposta(
                HttpStatus.UNAUTHORIZED,
                "Cliente não identificado",
                "O header X-Cliente-Id é obrigatório ou inválido.",
                request,
            )
        }

        HttpStatus.NOT_FOUND.value() -> {
            resposta(
                HttpStatus.NOT_FOUND,
                "Recurso não encontrado",
                "O recurso solicitado não foi encontrado.",
                request,
            )
        }

        else -> {
            resposta(
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "Revise os headers enviados.",
                request,
                listOf(ViolacaoResponse("Idempotency-Key", "idempotency_key_ausente", "Informe um UUID válido.")),
            )
        }
    }

fun fromDomain(
    n: NotaFiscal,
    conteudo: String,
): NotaFiscalEntity =
    NotaFiscalEntity()
```

Preferir

```kotlin
fun statusHttp(erro: ResponseStatusException, request: HttpServletRequest): ResponseEntity<ProblemDetails> =
    when (erro.statusCode.value()) {
        HttpStatus.UNAUTHORIZED.value() -> {
            resposta(
                HttpStatus.UNAUTHORIZED,
                "Cliente não identificado",
                "O header X-Cliente-Id é obrigatório ou inválido.",
                request,
            )
        }

        HttpStatus.NOT_FOUND.value() -> {
            resposta(
                HttpStatus.NOT_FOUND,
                "Recurso não encontrado",
                "O recurso solicitado não foi encontrado.",
                request,
            )
        }

        else -> {
            resposta(
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "Revise os headers enviados.",
                request,
                listOf(ViolacaoResponse("Idempotency-Key", "idempotency_key_ausente", "Informe um UUID válido.")),
            )
        }
    }

fun fromDomain(
    n: NotaFiscal,
    conteudo: String,
) = NotaFiscalEntity()
```

**NUNCA** usar o caminho completo da dependência. Em casos de homônimos, usar o `as`. 

Evitar
```kotlin
import java.sql.Date

private fun violacao(
    erro: com.meu_negocio.application.usecase.validations.Violacao,
    date: import java.util.Date,
): ViolacaoResponse {}
```
Preferir
```kotlin
import java.util.Date as UtilDate
import java.sql.Date as SqlDate
import com.meu_negocio.application.usecase.validations.Violacao

private fun violacao(erro: Violacao, date: UtilDate): ViolacaoResponse {}
```

## Aplicação neste projeto

- Em componentes Spring, preferir injeção por construtor e dependências em propriedades `val`.
- Não aplicar `data class` automaticamente a entidades JPA. Considerar identidade persistente, proxies, associações e efeitos de `equals`, `hashCode` e `toString`.
- Preservar a integração dos plugins Kotlin com Spring/JPA e as opções de nulabilidade configuradas no build.

Exemplo de injeção por construtor. `PedidoDto` é o DTO definido no exemplo de modelagem; uma implementação do repositório deve ser registrada como bean Spring.

```kotlin
import org.springframework.stereotype.Service

interface RepositorioDePedidos {
    fun buscarPorId(id: Long): PedidoDto?
}

@Service
class ConsultarPedido(private val repositorio: RepositorioDePedidos) {
    fun executar(id: Long): PedidoDto? {
        require(id > 0) { "O identificador deve ser positivo" }
        return repositorio.buscarPorId(id)
    }
}
```

## Verificação

- Cobrir mudanças de comportamento com testes focados, incluindo limites e ausência de valores quando relevantes.
- Executar os testes afetados pelo Wrapper, por exemplo `./gradlew test --tests 'pacote.ClasseDeTeste'`.
- Executar ktlint e detekt quando estiverem configurados. Conferir as tarefas disponíveis antes de invocá-las; este projeto ainda não declara esses plugins.

Exemplo de teste unitário para o contrato de `nomeParaExibicao`, sem carregar o contexto Spring:

```kotlin
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class NomeParaExibicaoTests {
    @Test
    fun `usa valor padrao quando o nome esta ausente ou em branco`() {
        assertEquals("Não informado", nomeParaExibicao(null))
        assertEquals("Não informado", nomeParaExibicao("   "))
    }

    @Test
    fun `remove espacos nas extremidades do nome`() {
        assertEquals("Ana", nomeParaExibicao("  Ana  "))
    }
}
```

## Referências

- [Boas práticas em Kotlin — Kotlin Brasil (referência temática)](https://kotlin.dev.br/tutoriais/boas-praticas-kotlin/).
- [Convenções oficiais de Kotlin](https://kotlinlang.org/docs/coding-conventions.html).
- [Coleções e interfaces de leitura](https://kotlinlang.org/docs/collections-overview.html).
- [Segurança contra valores nulos](https://kotlinlang.org/docs/null-safety.html).
- [Funções de escopo](https://kotlinlang.org/docs/scope-functions.html).
