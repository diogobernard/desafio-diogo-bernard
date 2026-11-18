# Estrutura de pastas

Este documento define a organização de pastas para Kotlin, Spring Boot e Gradle, seguindo o projeto `si-mlc-invoices-consumer`. A estrutura esperada usa dois módulos na raiz: `app` e `domain`.

## Visão geral

```text
study-kubernetes/
├── .agents/rules/
├── .code_quality/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── docs/
│       │   └── asciidoc/
│       ├── main/
│       │   ├── kotlin/<pacote-base>/
│       │   └── resources/
│       └── test/
│           ├── kotlin/<pacote-base>/
│           └── resources/
├── domain/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   └── kotlin/<pacote-base>/
│       └── test/
│           ├── kotlin/<pacote-base>/
│           └── resources/
├── commands/
├── gradle/wrapper/
├── migrations/
│   └── <banco>/<base-ou-ambiente>/
├── mock/
├── scripts/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── Dockerfile
└── README.md
```

`<pacote-base>` representa o caminho do pacote Kotlin. Neste projeto, preservar `com/db/study_kubernetes`; o pacote corporativo da referência não deve ser copiado. O código dos dois módulos usa o mesmo pacote-base, com tipos distintos para evitar nomes totalmente qualificados duplicados.

A árvore descreve a organização alvo. Criar diretórios auxiliares quando houver conteúdo ou necessidade equivalente; não copiar arquivos, versões ou configurações corporativas da referência automaticamente.

## Módulo `domain`

```text
domain/src/main/kotlin/<pacote-base>/
├── config/
│   └── extension/
├── exception/
├── gateway/
├── model/
│   └── <conceito-do-dominio>/
└── usecase/
    ├── <funcionalidade>/
    │   └── impl/
    └── validations/
```

- `model/`: modelos, objetos de valor, estados e contratos de dados do domínio. Agrupar subpastas por conceito quando necessário.
- `usecase/`: casos de uso e coordenação das regras de negócio. Usar subpastas por funcionalidade, como a referência faz com `folio`, `reprocess` e `supervisor`; adaptar esses nomes ao domínio deste projeto.
- `usecase/validations/`: validações e políticas usadas pelos casos de uso.
- `usecase/<funcionalidade>/impl/`: implementações quando houver separação explícita entre contrato e implementação; não criar `impl/` para toda classe indiscriminadamente.
- `gateway/`: interfaces que expressam as operações externas necessárias aos casos de uso, como consultas, persistência ou publicação de mensagens.
- `exception/`: exceções que representam falhas de negócio.
- `config/extension/`: extensões e apoio interno do domínio, sem depender de implementações do módulo `app`.

O módulo `domain` não depende de `app`. Seus casos de uso recebem contratos de gateway, mantendo detalhes de HTTP, banco de dados e mensageria nas implementações de infraestrutura.

### Camadas dentro do módulo `domain`

O módulo `domain` é o **núcleo** da aplicação e reúne duas camadas conceituais, separadas por pacote, conforme DDD (Domain ≠ Application) e Clean Architecture (Entities ≠ Use Cases):

| Camada | Pacotes | Conteúdo |
| --- | --- | --- |
| Domínio | `model/`, `exception/`, `gateway/`, `usecase/validations/` | Modelos, invariantes, políticas e validações de negócio e portas de saída. |
| Aplicação | `usecase/<funcionalidade>/` | Casos de uso (application services/interactors): porta de entrada e implementação, coordenando regras e gateways. |

- A porta de entrada **e** a implementação de cada caso de uso ficam no `domain`. Somente as portas de saída (`gateway/`) são implementadas no `app`: a inversão de dependência só é necessária quando o núcleo precisa chamar algo de fora; o `entrypoint` já depende do `domain` e apenas consome a porta de entrada, cuja implementação contém regras de coordenação que pertencem ao núcleo.
- Um caso de uso não chama outro caso de uso. Lógica compartilhada vira política ou validação do domínio.
- Responsabilidades transversais da aplicação, como idempotência, ficam em `usecase/` como decoradores do contrato do caso de uso. Suas portas ficam em `gateway/` e as implementações técnicas (cache, criptografia, protocolo) no `app`.
- Proteções técnicas de infraestrutura que não mudam a semântica do contrato, como limite de concorrência (*bulkhead*) diante de uma dependência saturável, **não** pertencem ao `domain`. Ficam em `app/config/resilience/` como decoradores da porta de entrada, aplicados na composição em `app/config`. O decorador técnico só envolve a chamada; a lógica do caso de uso continua no `domain`. Critério: se a responsabilidade desapareceria ou mudaria de forma ao trocar a infraestrutura (por exemplo, o mecanismo de persistência), ela é técnica e fica no `app`.
- O módulo `domain` não usa o nome ou o formato de headers, rotas e status HTTP; o `entrypoint` traduz o protocolo para os tipos do domínio.

## Módulo `app`

```text
app/src/main/kotlin/<pacote-base>/
├── Application.kt
├── config/
│   ├── extension/
│   ├── properties/
│   └── resilience/
├── entrypoint/
│   ├── handlers/
│   ├── job/
│   ├── model/
│   └── <grupo-de-consumidores>/
├── exception/
├── gateway/
│   ├── middleware/
│   │   └── <integracao>/
│   └── mock/
├── model/
└── repository/
    └── entity/
```

- `Application.kt`: *SEMPRE* use `Application.kt` como ponto de inicialização Spring Boot, caso use outro nome com o `{PrefixoComOnomeDaApp}Application.kt` na referência. Renomeie o nome para `Application.kt` não esquecendo de alterar os arquivos que fazem referência a ele também.
- `config/`: configuração e composição dos componentes da aplicação; `properties/` contém propriedades tipadas, `resilience/` configura resiliência (retentativas, limite de concorrência e decoradores técnicos da porta de entrada) e `extension/` reúne extensões de suporte.
- `entrypoint/`: controllers HTTP e consumidores que recebem entradas e acionam casos de uso.
- `entrypoint/handlers/`: tradução de exceções para respostas do protocolo de entrada.
- `entrypoint/job/`: entradas de execução agendada ou de jobs.
- `entrypoint/model/`: DTOs de requisição, resposta e envelopes de mensagens. Converter esses tipos para modelos do domínio nas fronteiras.
- `entrypoint/<grupo-de-consumidores>/`: consumidores agrupados por fluxo, como `processedinvoices` na referência, quando houver necessidade equivalente.
- `gateway/`: implementações dos contratos definidos em `domain/gateway`, incluindo acesso a banco, APIs, configuração externa e mensageria.
- `gateway/middleware/<integracao>/`: adaptadores específicos de integrações externas.
- `gateway/mock/`: implementações simuladas selecionadas por configuração ou perfil, quando necessárias.
- `model/`: modelos específicos de infraestrutura ou de integrações; modelos compartilhados de negócio ficam em `domain/model`.
- `repository/`: interfaces de persistência e consultas; `repository/entity/` contém entidades e mapeamentos JPA.
- `exception/`: falhas específicas da aplicação e da infraestrutura.

A referência também possui `gateway/extentions/`. Para extensões novas, usar a grafia correta `extension/`, consistente com `config/extension/`.

## Dependências e fluxo

```text
Dependência Gradle: app → domain

Fluxo de execução:
entrypoint → usecase → contrato de gateway (domain)
                          ↓ implementação injetada
                    gateway (app) → repository/API/mensageria
```

As entradas devem cuidar do protocolo e delegar regras de negócio aos casos de uso. Implementações de gateway adaptam os contratos do domínio aos serviços externos. O domínio não importa controllers, entidades JPA ou repositórios do módulo `app`.

Exemplo de módulos em `../../settings.gradle.kts`:

```kotlin
rootProject.name = "study-kubernetes"
include("app", "domain")
```

Exemplo de dependência em `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":domain"))
}
```

`app` gera o executável Spring Boot; `domain` gera uma biblioteca. A configuração efetiva dos plugins e tarefas deve seguir as regras de `gradle.md` e as versões deste projeto.

## Recursos e testes

- Configurações de execução ficam em `app/src/main/resources/`, incluindo `application.yml` ou `application.properties` e arquivos por perfil quando necessários.
- Testes ficam em `app/src/test/kotlin/<pacote-base>/` e `domain/src/test/kotlin/<pacote-base>/`, espelhando os pacotes do código testado.
- Testes de casos de uso e modelos ficam em `domain`; testes de entradas, gateways e integrações ficam em `app`.
- Testes E2E ficam em `app/src/test/kotlin/<pacote-base>/e2e/`, como na referência.
- Testes de documentação da API (`<Controller>DocumentationTest`) ficam junto aos testes do controller em `app/src/test/kotlin/<pacote-base>/entrypoint/<funcionalidade>/`, e os fontes Asciidoctor em `app/src/docs/asciidoc/`, conforme [spring-rest-docs.md](spring-rest-docs.md).
- Fixtures, payloads e configurações de teste ficam em `src/test/resources/` do respectivo módulo. A referência usa subpastas como `__files/`, `db/` e `xml/`; criar somente as que fizerem sentido para os testes locais.
- Provedores de dados e classes de apoio aos testes ficam no código de teste do módulo que os utiliza.

## Diretórios auxiliares

- `../../gradle/wrapper`: arquivos do Gradle Wrapper compartilhado pelos módulos.
- `.code_quality/`: configurações de análise e qualidade, quando as ferramentas correspondentes estiverem adotadas.
- `migrations/<banco>/<base-ou-ambiente>/`: migrações de banco. A referência usa `mysql/` e subpastas por base; adaptar ao banco e à estratégia local.
- `mock/`: arquivos e definições usados por simuladores externos. Implementações Kotlin simuladas ficam em `app/gateway/mock/`.
- `scripts/`: scripts para preparar, executar e limpar o ambiente local.
- `commands/` e `edicom/`: diretórios auxiliares presentes na referência, sem registro como módulos Gradle. `commands/` contém apenas `.gitkeep`, e `edicom/` não contém código de aplicação; não atribuir responsabilidades novas a esses diretórios sem necessidade.
- `Dockerfile`, `Dockerfile.runtime` e arquivos Gradle compartilhados ficam na raiz.
- `build/`, `out/`, `../../.gradle` e `.idea/` são saídas ou arquivos locais de ferramentas; não servem como pastas de código-fonte.

## Convenções de organização

- Manter os nomes estruturais `app`, `domain`, `entrypoint`, `gateway`, `model`, `repository` e `usecase` conforme a referência.
- Organizar subpastas por responsabilidade ou funcionalidade, preservando o sentido de cada módulo.
- Manter cada arquivo Kotlin no diretório correspondente ao seu pacote.
- Não colocar testes junto aos arquivos de produção; usar os source sets de teste de cada módulo.
- Ao migrar a estrutura física, mover também recursos e testes, atualizar Gradle e caminhos de execução e verificar o build. Alterar este documento, por si só, não realiza essa migração.

## Referência utilizada

Estrutura e dependências verificadas em `/Users/diogobernard/dev_meli/java/workspace/si-mlc-invoices-consumer`, especialmente `../../settings.gradle.kts`, os builds de `app` e `domain` e seus diretórios `src/main` e `src/test`.
