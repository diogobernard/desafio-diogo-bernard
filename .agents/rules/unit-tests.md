# Regras para testes unitários em Kotlin

## Escopo

Aplicar ao criar, revisar ou alterar testes unitários Kotlin de modelos, funções, serviços e casos de uso. Seguir também `kotlin.md`, `gradle.md` e `folder-structure.md`.

Os exemplos são ilustrativos e usam o pacote deste projeto. Os tipos do primeiro exemplo são reutilizados nos testes seguintes; adaptar nomes e contratos ao código real.

## Organização e ferramentas

- Usar JUnit Jupiter para organizar e executar testes e `kotlin.test` para asserções, respeitando as versões gerenciadas pelo projeto.
- Manter testes em `../../src/test/kotlin`, espelhando o pacote de produção. Na estrutura modular definida em `folder-structure.md`, testar casos de uso e modelos em `domain` e componentes de aplicação em `app`.
- Enquanto o build continuar com um único módulo, usar `../../src/test/kotlin` na raiz; não criar módulos apenas para adicionar um teste.
- Nomear arquivos e classes como `<ClasseTestada>Test.kt` e `<ClasseTestada>Test`. Preservar nomes existentes quando a alteração não exigir padronização.
- Manter fixtures e provedores em código de teste; arquivos de apoio ficam em `src/test/resources` do respectivo módulo.
- Preferir as ferramentas já adotadas. MockK é uma opção adequada para Kotlin, mas não misturar bibliotecas de mocks no mesmo teste nem adicionar dependências sem necessidade.
- Não copiar versões antigas dos artigos. Conferir compatibilidade com Kotlin, JDK, Gradle e o gerenciamento de dependências do Spring Boot.

O build atual já declara `kotlin-test-junit5`, o launcher do JUnit Platform e starters de teste Spring, além de `useJUnitPlatform()`. Não declara MockK nem `kotlinx-coroutines-test`; exemplos que os utilizam exigem essas dependências.

Exemplo de configuração para um módulo que precise dessas tarefas e dependências, usando o gerenciamento de versões existente:

```kotlin
// build.gradle.kts do módulo que contém os testes.
dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Garantir também que o engine Jupiter esteja no runtime de teste. Os starters atuais já fornecem suporte; em um módulo independente, verificar e declarar as dependências Jupiter necessárias. Testes parametrizados precisam de `junit-jupiter-params`. MockK e testes de coroutines devem ter versões explicitamente gerenciadas antes de serem usados.

## Limites do teste unitário

- Instanciar a classe testada diretamente pelo construtor. Usar dependências reais simples, fakes ou mocks locais para isolar fronteiras externas.
- Não carregar contexto Spring com `@SpringBootTest` nem usar `@Autowired` para montar um teste unitário.
- Banco de dados real, servidor HTTP, filas, Testcontainers e configuração de filtros Spring Security pertencem a testes de integração.
- `@WebMvcTest` e `@DataJpaTest` carregam partes do contexto e verificam integração com o framework; não classificá-los como testes unitários puros.
- Uma chamada direta a um controller não comprova roteamento HTTP, serialização, Bean Validation, transações ou autorização por filtros.
- Testar decisões de autorização que sejam funções ou políticas independentes como unidades; validar a cadeia de segurança e a aplicação de anotações em testes de integração apropriados.

O teste `StudyKubernetesApplicationTests.contextLoads` é um teste de carregamento de contexto, não substitui a cobertura unitária de comportamento.

## Cenários e legibilidade

- Nomear métodos em português entre backticks, descrevendo cenário e resultado, como `retorna pedido quando o identificador existe`.
- Organizar cada teste em preparação, ação e verificação (Arrange, Act, Assert ou Given, When, Then). Usar linhas em branco; comentários só quando ajudarem a leitura.
- Manter um comportamento por teste. Várias asserções são adequadas quando descrevem o mesmo resultado.
- Verificar saída, estado observável ou efeito externo relevante. Não testar métodos privados por reflexão nem espelhar o algoritmo de produção no cálculo do resultado esperado.
- Cobrir caminho de sucesso, ausência de dados, entradas inválidas, limites e falhas de dependências quando fizerem parte do contrato.
- Testar `null` somente em contratos anuláveis; não introduzir coerções ou `!!` apenas para fabricar entradas impossíveis.
- Não criar testes que apenas comprovem getters, código gerado de `data class`, construtores triviais ou o próprio comportamento de um mock.
- Ao corrigir um defeito, adicionar um cenário que reproduza a regressão e diferencie o comportamento incorreto do esperado.

## Cobertura obrigatória

- Exigir **100% de cobertura de linhas e de ramificações (branches)** do código de produção mantido pelo projeto em `domain` e `app`, por testes unitários. Verificar cada módulo separadamente; a média entre módulos não substitui a meta individual.
- Cobrir todos os comportamentos e decisões com asserções significativas, incluindo sucesso, limites, validações, ausência de dados e falhas previstas no contrato. Executar uma linha sem verificar seu resultado não comprova a qualidade do teste.
- Excluir da medição apenas código gerado automaticamente, com exclusões explícitas e justificadas. Não excluir código de produção escrito manualmente nem reduzir a meta para alcançar o percentual.
- Usar relatórios de cobertura para identificar linhas e ramificações sem proteção. Não criar testes artificiais, testar detalhes privados ou fabricar estados impossíveis apenas para atingir 100%.
- Comprovar a meta por relatório da ferramenta de cobertura configurada no build, registrando os percentuais de linhas e ramificações por módulo. Uma suíte passando, um teste de contexto ou a contagem de testes não comprova 100% de cobertura.

O build atual ainda não configura uma ferramenta de cobertura. A exigência de 100% permanece; enquanto não houver medição, registrar essa limitação e não declarar a meta atingida. Configurar a ferramenta exige verificar compatibilidade com as versões do projeto e validar as alterações de build.

## Exemplo sem mocks: caso de uso e fake

Código de produção ilustrativo. Em uma estrutura modular, distribuir os tipos entre `domain/model`, `domain/gateway`, `domain/exception` e `domain/usecase`, mantendo seus pacotes e imports correspondentes.

```kotlin
package com.db.project_mister_study.usecase

data class Pedido(val id: Long, val descricao: String)

interface BuscarPedidoGateway {
    fun buscarPorId(id: Long): Pedido?
}

class PedidoNaoEncontradoException(val id: Long) :
    RuntimeException("Pedido $id não encontrado")

class BuscarPedidoUseCase(private val gateway: BuscarPedidoGateway) {
    fun executar(id: Long): Pedido {
        require(id > 0) { "O identificador deve ser positivo" }
        return gateway.buscarPorId(id) ?: throw PedidoNaoEncontradoException(id)
    }
}
```

Teste com fake em memória. Comparar o pedido inteiro verifica seu contrato de valor; testar a exceção verifica o comportamento quando não há resultado.

```kotlin
package com.db.project_mister_study.usecase

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

private class BuscarPedidoGatewayFake(
    private val pedidos: Map<Long, Pedido> = emptyMap(),
) : BuscarPedidoGateway {
    override fun buscarPorId(id: Long): Pedido? = pedidos[id]
}

class BuscarPedidoUseCaseTest {
    @Test
    fun `retorna pedido quando o identificador existe`() {
        val esperado = Pedido(id = 7L, descricao = "Pedido de exemplo")
        val gateway = BuscarPedidoGatewayFake(mapOf(7L to esperado))
        val useCase = BuscarPedidoUseCase(gateway)

        val resultado = useCase.executar(7L)

        assertEquals(esperado, resultado)
    }

    @Test
    fun `informa identificador quando o pedido nao existe`() {
        val useCase = BuscarPedidoUseCase(BuscarPedidoGatewayFake())

        val erro = assertFailsWith<PedidoNaoEncontradoException> {
            useCase.executar(99L)
        }

        assertEquals(99L, erro.id)
    }
}
```

## Asserções, fixtures e ciclo de vida

- Usar `assertEquals(esperado, resultado)` na ordem correta. Preferir asserções específicas como `assertNull`, `assertNotNull`, `assertTrue` e `assertFailsWith<T>` a verificações genéricas que dificultem o diagnóstico.
- Comparar objetos por igualdade quando essa igualdade representar o contrato; comparar campos específicos quando a identidade ou outros campos não fizerem parte do cenário.
- Verificar tipo e dados relevantes de exceções. Exigir o texto exato da mensagem somente quando ele fizer parte do contrato.
- Usar funções de fixture com argumentos padrão para objetos complexos, destacando no teste apenas valores relevantes. Cada chamada deve criar objetos mutáveis novos, quando houver mutabilidade.
- Preservar o ciclo padrão `PER_METHOD` do JUnit: cada teste recebe uma nova instância. Inicializar dependências simples em propriedades `val` ou dentro do método.
- Usar `@BeforeEach` quando existir preparação comum que precise ser refeita. Evitar fixtures extensas que escondam o cenário.
- Usar `@Nested` com `inner class` quando houver grupos de comportamentos que facilitem navegação e relatórios.
- Não alterar globalmente o ciclo para `PER_CLASS` apenas por conveniência. Quando esse ciclo for necessário, garantir a limpeza do estado e o isolamento entre métodos, considerando execução paralela.

Exemplo de fixture, reutilizando o tipo `Pedido` definido acima:

```kotlin
private fun umPedido(
    id: Long = 7L,
    descricao: String = "Pedido de exemplo",
): Pedido = Pedido(id = id, descricao = descricao)
```

## Testes parametrizados

- Parametrizar cenários com o mesmo comportamento e diferentes entradas; manter cenários com preparação distinta em testes separados.
- Usar `@ValueSource`, `@CsvSource` ou `@MethodSource` conforme a complexidade dos dados. Para contratos anuláveis, avaliar `@NullSource` e `@NullAndEmptySource`.
- Em `@MethodSource`, usar fábrica estática via `companion object` e `@JvmStatic` com o ciclo padrão, ou uma configuração local de ciclo de vida devidamente justificada.
- Informar casos que expliquem a regra e seus limites; não gerar massas grandes de valores equivalentes apenas para aumentar a contagem de testes.

Exemplo, que requer `junit-jupiter-params` no classpath de teste:

```kotlin
package com.db.project_mister_study.usecase

import kotlin.test.assertFailsWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BuscarPedidoValidacaoTest {
    @ParameterizedTest(name = "rejeita identificador {0}")
    @ValueSource(longs = [0L, -1L, Long.MIN_VALUE])
    fun `rejeita identificadores nao positivos`(id: Long) {
        val gateway = object : BuscarPedidoGateway {
            override fun buscarPorId(id: Long): Pedido? = null
        }
        val useCase = BuscarPedidoUseCase(gateway)

        assertFailsWith<IllegalArgumentException> {
            useCase.executar(id)
        }
    }
}
```

## Mocks e verificação de efeitos

- Mockar fronteiras ou colaboradores quando isso tornar o cenário mais claro. Manter a classe testada real; modelos e coleções simples normalmente não precisam de mocks.
- Preferir injeção explícita pelo construtor à montagem automática que esconda as dependências.
- Com MockK, usar `mockk`, `every` e `verify` para funções comuns; usar variantes `coEvery` e `coVerify` para funções suspensas.
- Definir apenas stubs necessários e preferir argumentos concretos quando forem relevantes. Usar `any()` quando o valor não importar ao cenário.
- Evitar `relaxed = true` como padrão, pois respostas automáticas podem esconder preparação ausente.
- Verificar quantidade, ordem ou ausência de chamadas somente quando fizerem parte do comportamento, como impedir persistência após validação inválida.
- Não exigir `confirmVerified` ou verificar todas as chamadas por rotina; isso pode acoplar o teste a detalhes internos.
- Evitar mocks globais, estáticos e spies sem necessidade. Se forem indispensáveis, restaurar o estado após o teste e avaliar interferência em execução paralela.

Exemplo com MockK, opcional até a biblioteca ser configurada. A ausência de chamada representa a regra de rejeitar entradas antes de consultar uma dependência:

```kotlin
package com.db.project_mister_study.usecase

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class BuscarPedidoInteracoesTest {
    private val gateway = mockk<BuscarPedidoGateway>()
    private val useCase = BuscarPedidoUseCase(gateway)

    @Test
    fun `nao consulta gateway quando o identificador e invalido`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.executar(0L)
        }

        verify(exactly = 0) { gateway.buscarPorId(any()) }
    }

    @Test
    fun `propaga falha do gateway sem transformar em pedido ausente`() {
        val falha = IllegalStateException("Gateway indisponível")
        every { gateway.buscarPorId(7L) } throws falha

        val erro = assertFailsWith<IllegalStateException> {
            useCase.executar(7L)
        }

        assertSame(falha, erro)
    }
}
```

## Determinismo e código assíncrono

- Não depender de rede, dados de produção, horário real, ordem de execução ou variáveis de ambiente para um teste unitário.
- Injetar `Clock` para decisões de tempo e usar `Clock.fixed` nos testes. Injetar geradores de identificadores ou aleatoriedade quando afetarem a regra.
- Evitar `Thread.sleep` e esperas arbitrárias. Se o filesystem fizer parte da unidade, usar diretórios temporários isolados, como `@TempDir`.
- Para coroutines, usar `runTest` de `kotlinx-coroutines-test`; controlar dispatchers e compartilhar o scheduler de teste quando houver execução concorrente.
- Não supor que `runTest` controla dispatchers reais como `Dispatchers.IO`; injetar dispatchers apropriados no código que inicia trabalho assíncrono.
- Não introduzir coroutines ou Reactor apenas para testar código síncrono. Bibliotecas adicionais só se aplicam ao código que já usa esses modelos.

Exemplo independente de função suspensa com `runTest`, que requer `kotlinx-coroutines-test`:

```kotlin
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class PrepararMensagem {
    suspend fun executar(nome: String): String {
        delay(1_000)
        return "Olá, $nome"
    }
}

class PrepararMensagemTest {
    @Test
    fun `prepara mensagem apos espera simulada`() = runTest {
        val servico = PrepararMensagem()

        val resultado = servico.executar("Ana")

        assertEquals("Olá, Ana", resultado)
    }
}
```

O `delay` deste exemplo usa tempo virtual; o teste não precisa esperar um segundo real.

## Execução e revisão

- Executar os testes afetados pelo Wrapper. Após alterações que alcancem vários componentes, executar a suíte correspondente.
- Conferir a existência dos módulos antes de usar caminhos de tarefas. No build atual, o comando é `./gradlew test`; depois da modularização, usar `:domain:test` ou `:app:test`.
- Se houver seleção por tags para separar testes unitários e de integração, garantir que os comandos e o CI executem ambos os grupos adequadamente.
- Verificar a meta obrigatória de 100% de cobertura de linhas e ramificações conforme a seção “Cobertura obrigatória”; registrar lacunas e limitações de medição.
- Informar comandos executados, resultados e limitações. Não considerar ausência de testes descobertos como evidência de que a alteração foi validada.

```sh
# Build atual com um módulo: selecionar um teste que realmente exista.
./gradlew test --tests 'com.db.project_mister_study.usecase.BuscarPedidoUseCaseTest'

# Após os módulos app e domain existirem no build:
./gradlew :domain:test
./gradlew :app:test
```

Os testes ilustrados neste documento não são adicionados automaticamente ao projeto. Substituir o filtro pelo nome da classe efetivamente criada.

## Referências

Documentação oficial usada para validar as APIs e seu comportamento:

- [Kotlin e JUnit](https://kotlinlang.org/docs/jvm-test-using-junit.html).
- [JUnit: ciclo de vida](https://docs.junit.org/current/writing-tests/test-instance-lifecycle.html).
- [JUnit: testes parametrizados](https://docs.junit.org/current/writing-tests/parameterized-classes-and-tests.html).
- [MockK](https://mockk.io/).
- [Testes de coroutines](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/).
- [Testes de aplicações Spring Boot](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html).

Leituras complementares indicadas pelo usuário:

- [Kotlin Brasil: testes unitários](https://kotlin.dev.br/tutoriais/kotlin-testes-unitarios/).
- [Philipp Hauer: boas práticas de testes Kotlin](https://phauer.com/2018/best-practices-unit-testing-kotlin/). As recomendações de reutilizar instâncias e alterar globalmente o ciclo de vida não são adotadas como padrão neste projeto.
- [Smita Kothari: testes unitários em Spring Boot](https://medium.com/@smita.s.kothari/unit-testing-in-spring-boot-best-practices-with-sample-code-e483eaa4cc19).
- [Rafael Developer: integração com Spring Boot, Kotlin e Spring Security](https://rafael-developer.com/2026/07/01/teste-de-integracao-com-spring-boot-kotlin-e-spring-security/). A página não pôde ser acessada durante a elaboração; o link permanece como leitura indicada, sem atribuição de conteúdo não verificado.
