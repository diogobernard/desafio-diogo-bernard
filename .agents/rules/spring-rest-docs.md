# Regras para documentação de API com Spring REST Docs

## Escopo

Aplicar ao criar, revisar ou alterar a documentação dos contratos HTTP do módulo `app`, os controllers em `app/entrypoint`, os testes que geram snippets e a configuração Gradle de documentação. Seguir também [kotlin.md](kotlin.md), [gradle.md](gradle.md), [code-standards.md](code-standards.md), [logs.md](logs.md), [unit-tests.md](unit-tests.md) e [e2e-tests.md](e2e-tests.md).

Os exemplos são ilustrativos e adaptados da [documentação oficial do Spring REST Docs](https://docs.spring.io/spring-restdocs/index.html) para Kotlin. Rotas, campos e headers devem seguir o contrato real do PRD e da TechSpec; não inventar respostas ou regras de negócio para documentar.

## Regra obrigatória: nunca usar anotações do Swagger/OpenAPI no código

> **NUNCA USE ANOTAÇÕES DO SWAGGER/OPENAPI NO CÓDIGO.** A documentação da API é produzida pelos testes com Spring REST Docs, não por metadados espalhados em controllers, DTOs e configurações.

- Não importar nem usar nada de `io.swagger.v3.oas.annotations`, incluindo `@Operation`, `@Parameter`, `@Parameters`, `@ApiResponse`, `@ApiResponses`, `@Schema`, `@ArraySchema`, `@Content`, `@ExampleObject`, `@RequestBody` do Swagger, `@Tag`, `@OpenAPIDefinition`, `@Info`, `@Contact`, `@License`, `@SecurityScheme` e `@Hidden`.
- Não usar as anotações legadas `io.swagger.annotations` (`@Api`, `@ApiOperation`, `@ApiModel`, `@ApiModelProperty`, `@ApiParam`).
- Não substituir anotações por classes de "exemplos OpenAPI" ou constantes de payload em `src/main` cuja única finalidade seja alimentar a documentação.
- Motivo: essas anotações poluem a legibilidade, multiplicam linhas nos controllers (violando os limites de [code-standards.md](code-standards.md)), misturam documentação com protocolo e **não são verificadas**: um exemplo ou schema anotado pode divergir do comportamento real sem que nenhum teste falhe.
- Controllers contêm apenas o mapeamento HTTP (`@RestController`, `@RequestMapping`, `@GetMapping`, `@PathVariable`, `@RequestHeader`, `@RequestBody` do Spring etc.) e a delegação ao caso de uso, conforme [clean-architecture.md](clean-architecture.md).
- Ao alterar um controller ou DTO que ainda contenha essas anotações, removê-las e cobrir o contrato correspondente com um teste de documentação. Não acrescentar novas anotações "por consistência" com código legado.

Evitar:

```kotlin
@Operation(summary = "Consulta uma nota fiscal")
@ApiResponses(
    ApiResponse(responseCode = "200", description = "Nota encontrada"),
    ApiResponse(responseCode = "404", description = "Nota não encontrada"),
)
@GetMapping("/{serie}/{numero}")
fun consultar(
    @Parameter(description = "Série da nota") @PathVariable serie: String,
    @Parameter(description = "Número da nota") @PathVariable numero: Long,
): ResponseEntity<NotaFiscalResponse>
```

Preferir:

```kotlin
@GetMapping("/{serie}/{numero}")
fun consultar(
    @PathVariable serie: String,
    @PathVariable numero: Long,
): ResponseEntity<NotaFiscalResponse>
```

A descrição de `serie`, `numero`, dos status e do corpo passa a viver no teste que exercita a rota.

## Princípios do Spring REST Docs

- A documentação combina **texto escrito à mão em Asciidoctor** com **snippets gerados por testes** de Spring MVC Test (`MockMvc`) ou `WebTestClient`.
- Um snippet só é gerado quando o teste passa. Se a documentação ficar errada, o teste falha: a documentação acompanha o contrato real.
- Documentar a **API**, não a implementação. Descrever recursos, campos, parâmetros, headers e status observáveis; não expor nomes de classes, pacotes ou tabelas internas.
- Neste projeto, usar `MockMvc`, pois o `app` usa Spring MVC (`spring-boot-starter-webmvc`).

## Versões e dependências

- A versão estável consultada é a 4.0.2, que exige Java 17+ e Spring Framework 7. O projeto usa toolchain 21 e Spring Boot 4, compatíveis com esses requisitos.
- Preferir a versão gerenciada pelo Spring Boot. Declarar versão explícita no catálogo `gradle/libs.versions.toml` somente se o BOM não a fornecer, conforme [gradle.md](gradle.md).
- Declarar `spring-restdocs-mockmvc` em `testImplementation` e `spring-restdocs-asciidoctor` em uma configuração dedicada ao Asciidoctor. A extensão Asciidoctor exige AsciidoctorJ 3.0.
- Ao adotar REST Docs, remover `springdoc-openapi-starter-webmvc-ui` do `app` e a configuração OpenAPI associada, desde que nenhum requisito do PRD/TechSpec exija a interface Swagger UI. Se houver essa exigência, registrar a decisão na TechSpec antes de manter as duas abordagens; mesmo assim, **nenhuma anotação Swagger entra no código**.

Exemplo de catálogo:

```toml
[versions]
asciidoctor = "4.0.5"

[libraries]
spring-restdocs-mockmvc = { module = "org.springframework.restdocs:spring-restdocs-mockmvc" }
spring-restdocs-asciidoctor = { module = "org.springframework.restdocs:spring-restdocs-asciidoctor" }

[plugins]
asciidoctor = { id = "org.asciidoctor.jvm.convert", version.ref = "asciidoctor" }
```

## Configuração Gradle

Configurar em `app/build.gradle.kts` com Kotlin DSL e APIs preguiçosas (`tasks.named`, providers), declarando entradas e saídas para preservar o build incremental:

```kotlin
plugins {
    id("convention.spring-application")
    alias(libs.plugins.asciidoctor)
}

val asciidoctorExt: Configuration by configurations.creating

val snippetsDir = layout.buildDirectory.dir("generated-snippets")

dependencies {
    asciidoctorExt(libs.spring.restdocs.asciidoctor)
    testImplementation(libs.spring.restdocs.mockmvc)
}

tasks.named<Test>("test") {
    outputs.dir(snippetsDir)
}

tasks.named<org.asciidoctor.gradle.jvm.AsciidoctorTask>("asciidoctor") {
    inputs.dir(snippetsDir)
    configurations(asciidoctorExt.name)
    dependsOn(tasks.named("test"))
}
```

- Os snippets ficam em `app/build/generated-snippets`; os fontes Asciidoctor, em `app/src/docs/asciidoc/`; o HTML gerado, em `app/build/docs/asciidoc/`.
- Gerar os snippets na tarefa que executa os testes de documentação. Se eles forem marcados com uma tag de integração, conectar o `asciidoctor` à tarefa correspondente, sem duplicar execuções.
- Para servir a documentação pela aplicação, copiar a saída do `asciidoctor` para `static/docs` no `bootJar`, dependendo do provider da tarefa, sem `afterEvaluate`.
- Validar com `./gradlew help --warning-mode=all` e `./gradlew :app:asciidoctor` após alterar o build.

## Testes de documentação

- Colocar os testes em `app/src/test/kotlin/com/itau/desafio_diogo_bernard/entrypoint/<funcionalidade>/`, nomeados `<Controller>DocumentationTest`.
- Usar `@WebMvcTest` com o controller testado e dublês dos casos de uso, ou `MockMvc` com contexto completo quando a jornada exigir. Testes com contexto Spring são de integração, conforme [unit-tests.md](unit-tests.md); não classificá-los como unitários.
- Configurar `MockMvc` com `documentationConfiguration(restDocumentation)` e `RestDocumentationExtension`, ou com `@AutoConfigureRestDocs` do Spring Boot.
- Usar `RestDocumentationRequestBuilders` para montar as requisições, permitindo documentar parâmetros de caminho.
- Cada chamada a `document("identificador")` gera, por padrão, `curl-request`, `http-request`, `http-response`, `httpie-request`, `request-body` e `response-body` em `generated-snippets/identificador/`.
- Nomear identificadores em kebab-case por recurso e cenário, como `notas-fiscais/consultar` e `notas-fiscais/consultar-nao-encontrada`.
- Documentar sucesso e respostas de erro do contrato (`400`, `404`, `409`, `422`, `304` etc.), incluindo o corpo `ProblemDetails` quando existir.
- Seguir nomes em português entre crases e Arrange/Act/Assert. Verificar o status e os campos com asserções além de documentar.

Exemplo, com rota e campos ilustrativos:

```kotlin
@WebMvcTest(NotaFiscalController::class)
@AutoConfigureRestDocs
class NotaFiscalControllerDocumentationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var consultarNotaFiscal: ConsultarNotaFiscal

    @Test
    fun `documenta consulta de nota fiscal emitida`() {
        given(consultarNotaFiscal.executar(any())).willReturn(umaNotaFiscal())

        val resultado = mockMvc.perform(
            get("/invoices/{serie}/{numero}", "1", 10L)
                .header(CABECALHO_CLIENTE, CLIENTE_FICTICIO),
        )

        resultado
            .andExpect(status().isOk)
            .andDo(
                document(
                    "notas-fiscais/consultar",
                    pathParameters(
                        parameterWithName("serie").description("Série da nota fiscal"),
                        parameterWithName("numero").description("Número da nota na série"),
                    ),
                    requestHeaders(
                        headerWithName(CABECALHO_CLIENTE).description("Identificador do cliente"),
                    ),
                    responseFields(camposNotaFiscal()),
                ),
            )
    }
}
```

Os imports estáticos vêm de `MockMvcRestDocumentation`, `RestDocumentationRequestBuilders`, `RequestDocumentation`, `HeaderDocumentation` e `PayloadDocumentation`. Extrair descritores reutilizados para funções de fixture no código de teste, respeitando o limite de 30 linhas por função.

## Campos, parâmetros e headers

- Preferir o modo estrito: `requestFields` e `responseFields` falham quando um campo do payload não é documentado ou quando um campo documentado está ausente. Isso protege o contrato contra mudanças acidentais.
- Usar `relaxedRequestFields`/`relaxedResponseFields` apenas para documentar um subconjunto com justificativa no próprio teste. Não usar o modo relaxado para esconder campos não documentados.
- Marcar com `.optional()` somente campos cuja ausência faz parte do contrato; usar `.ignored()` apenas para campos que não pertencem à documentação pública.
- Declarar `.type(JsonFieldType.X)` quando o tipo não puder ser inferido, como em campos opcionais ou nulos.
- Usar `subsectionWithPath` para objetos opacos e `beneathPath` para documentar partes de um payload. Reutilizar descritores com `andWithPrefix("itens[].", ...)` em listas.
- Documentar parâmetros de caminho com `pathParameters`, query string com `queryParameters` e headers com `requestHeaders`/`responseHeaders` (incluindo `ETag`, `Location`, `Cache-Control` e chaves de idempotência quando fizerem parte do contrato). Parâmetros não documentados falham o teste.
- Escrever descrições curtas, em português, com significado, unidade, formato, limites e valores possíveis (por exemplo, enums de status e regimes de tributação).

## Pré-processamento e dados sensíveis

- Configurar pré-processadores padrão com `operationPreprocessors().withRequestDefaults(...)` e `withResponseDefaults(...)`, aplicando `prettyPrint()` em requisição e resposta.
- Remover dos snippets, com `modifyHeaders().remove(...)`, headers sem valor documental ou sensíveis, como `Authorization`, `Cookie` e `Set-Cookie`.
- Usar `replacePattern` ou `modifyUris` somente para tornar a saída estável (por exemplo, host público e datas fixas), sem alterar a semântica do exemplo.
- **Snippets são documentação versionável e publicada.** Usar apenas dados fictícios: CPF/CNPJ fictícios ou mascarados, `<CHAVE_FICTICIA>` e identificadores sintéticos. Nunca usar credenciais, documentos reais ou payloads de produção, conforme [logs.md](logs.md).
- Usar relógio e geradores de identificadores controlados para que os snippets sejam determinísticos entre execuções.

## Documentos Asciidoctor

- Manter um índice em `app/src/docs/asciidoc/index.adoc` e, quando crescer, um arquivo por recurso incluído pelo índice.
- Incluir snippets com `include::{snippets}/invoices/consultar/http-request.adoc[]`; o atributo `snippets` é definido pela extensão `spring-restdocs-asciidoctor`.
- O texto escrito à mão explica fluxos, assincronia, idempotência, códigos de erro e decisões do contrato; os exemplos e tabelas vêm sempre dos snippets gerados, nunca de JSON copiado à mão.

```asciidoc
= API de emissão de notas fiscais
:toc: left
:sectanchors:

== Consultar nota fiscal

include::{snippets}/invoices/consultar/path-parameters.adoc[]
include::{snippets}/invoices/consultar/http-request.adoc[]
include::{snippets}/invoices/consultar/response-fields.adoc[]
include::{snippets}/invoices/consultar/http-response.adoc[]
```

## Verificação

- Confirmar que nenhum arquivo de `src/main` importa `io.swagger`: `grep -rn "io.swagger" app/src/main domain/src/main` deve retornar vazio.
- Executar os testes de documentação afetados e `./gradlew :app:asciidoctor`; conferir que os snippets esperados foram gerados e que o HTML inclui as seções alteradas.
- Uma suíte que descobre zero testes ou um snippet ausente não valida a documentação.
- Manter `koverVerify` e os limites de [code-standards.md](code-standards.md) após a migração.
- Informar comandos executados, resultados e limitações de ambiente.

## Referências

- [Spring REST Docs — visão geral](https://docs.spring.io/spring-restdocs/index.html).
- [Primeiros passos](https://docs.spring.io/spring-restdocs/tutorial/getting-started/index.html).
- [Payloads de requisição e resposta](https://docs.spring.io/spring-restdocs/reference/documenting-your-api/request-response-payloads.html).
- [Parâmetros de caminho](https://docs.spring.io/spring-restdocs/reference/documenting-your-api/path-parameters.html).
- [Headers HTTP](https://docs.spring.io/spring-restdocs/reference/documenting-your-api/http-headers.html).
- [Customização de requisições e respostas](https://docs.spring.io/spring-restdocs/reference/customizing-requests-and-responses.html).
- [Requisitos do sistema](https://docs.spring.io/spring-restdocs/system-requirements.html).
