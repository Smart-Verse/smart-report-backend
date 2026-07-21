# smart-report-backend — Handoff

## Responsabilidade

API principal do SmartReport. Autentica usuários, resolve o tenant, executa migrações por schema, mantém os cadastros relacionais, persiste templates no MongoDB e coordena a geração de PDF pelo `smart-report-core`.

## Stack

> A build exige um JDK 25 completo, incluindo `javac`. O runtime Java isolado não é suficiente.

- Java 25, Spring Boot 3.5.16 e Spring Cloud 2025.0.2.
- Spring MVC, Data JPA, HATEOAS, OpenFeign e SpringDoc.
- PostgreSQL + Flyway para dados relacionais e schemas por tenant.
- MongoDB driver síncrono para conteúdo dos templates e inscrições beta.
- `authorization-backend:1.0.2` para JWT, tenant e anotações de anonimato.
- Gonthera CLI Maven plugin `2.0.1` para código `_gen`.

## Modelo de dados

Entidades declaradas em `.gonthera/project.json`:

- `repository`: agrupador de relatórios;
- `report`: nome, quantidade gerada e relacionamento one-to-one com repository;
- `userConfiguration`: nome, foto, tema, idioma, email e hash do usuário;
- `userConfirmation`: confirmação de cadastro.

Enums gerados incluem `Language` e `Theme`. O PostgreSQL usa schemas `<DB_NAME>_<TENANT>`. Templates são documentos MongoDB em coleção nomeada com o tenant em minúsculas.

## Fluxos principais

### Template e PDF

- `GeneratorReport` implementa os endpoints gerados `SaveTemplate`, `GetTemplate`, `GenerateReport` e `GetMetrics`.
- `ReportService` grava/busca o documento do template no MongoDB.
- Na geração, o serviço carrega as propriedades, monta o conteúdo e chama `ReportClient`.
- `ReportClient` envia `POST http://localhost:5071/report` ao core.
- A contagem/métricas usa PostgreSQL e é devolvida pelos endpoints gerados.

### Autenticação e tenant

- API Keys são criadas em `POST /createApiKey`, listadas em `GET /getApiKeys` e revogadas em `POST /revokeApiKey`; os contratos são gerados pelo Gonthera e o handler customizado apenas os implementa.
- O segredo `sr_live_<prefix>_<secret>` é retornado somente na criação; o banco mantém SHA-256 e metadados no schema `admin`.
- `InterceptorConfig` aceita `X-API-Key` exclusivamente em `POST /generateReport`, resolve o tenant no catálogo admin, define `TenantContext`, migra o schema correspondente e limpa o contexto ao final.
- Chaves revogadas ou expiradas retornam 401; uso fora do escopo retorna 403.


- `AuthenticationHandlerImpl`: `/authenticate` e `/register`.
- `InterceptorConfig`: valida requisições, define `TenantContext` e dispara migração do tenant.
- `MultiTenantConnectionProviderImpl`: seleciona o schema da conexão.
- `DBMigration`: aplica Flyway no schema do tenant.
- `UserConfirmation`: confirma cadastro e cria/migra o tenant.

### Metadados

`MetadataHandler` expõe `/metadata`, consumido pelo frontend para construir formulários dinâmicos.

## Configuração local

Variáveis documentadas no projeto:

```text
DATABASE_SCHEMA_NAME=smartreport
DB_NAME=POSTGRES
DB_PASSWORD=<senha>
DB_USERNAME=postgres
SECRET_JWT=<segredo>
SERVER_PORT=5070
SERVICE_NAME=smartreport
MONGO_USER=smartreport
MONGO_PASSWORD=password
```

Executar:

```bash
./mvnw spring-boot:run
```

Swagger esperado em `http://localhost:5070/smartreport/swagger-ui/index.html`.

## Geração Gonthera

Fonte de verdade: `.gonthera/project.json`. Saída gerada: `src/main/java/com/smartverse/smartreportbackend_gen` e recursos gerados em `src/main/resources`.

Comandos corretos:

```bash
./mvnw gonthera-cli:validate
./mvnw gonthera-cli:generate-sources
```

Nunca trate `_gen` como fonte manual: regeneração substitui esses arquivos.

## Estado da migração em 2026-07-21

O worktree já contém mudanças do usuário: `properties.json` foi movido para `.gonthera/project.json`, o plugin passou a `2.0.1`, há diversos arquivos customizados modificados e uma árvore `_gen` nova ainda não rastreada. Preserve tudo durante a correção.

Quebras previstas pela skill `docs/skill/entity-generator-project/SKILL.md`:

- imports e heranças antigos de `handlers/*Handler` devem migrar para `controllers/*Controller`;
- regras CRUD, repositórios, conversores, filtros e transações agora estão nos `*Service` gerados;
- `generateDefaultHandlers` e `handlerAbstract` ainda aparecem no JSON, mas estão depreciados;
- `UserConfigurationController` e `ReportController` são abstratos na configuração atual e exigem subclasses concretas;
- configurações de service abstrato precisam de uma única subclasse `@Service` fora de `_gen`;
- entidades com controller concreto não podem ter outro controller expondo os mesmos mappings.

Arquivos customizados diretamente afetados incluem:

- `handlers/reports/ReportHandlerImpl.java`;
- `handlers/userconfiguration/UserConfigurationHandlerImpl.java`;
- repositórios customizados que estendem interfaces geradas;
- handlers de endpoints que importam contratos de `smartreportbackend_gen.endpoints`.

## Estratégia de correção

1. Rodar validação e compilação para obter a lista real de falhas.
2. Atualizar as flags do JSON para os nomes novos, decidindo separadamente controller e service abstratos.
3. Regenerar com `generate-sources`.
4. Corrigir apenas código customizado; não editar `_gen`.
5. Manter HTTP nos controllers/endpoints e regras de negócio/persistência em services.
6. Confirmar que existe um único bean por service abstrato e nenhum mapping duplicado.
7. Validar os contratos usados pelo Angular e a chamada ao core.

## Limitações e riscos existentes

- O POM pula testes via Surefire; uma compilação bem-sucedida não representa cobertura funcional.
- `ReportClient` tem URL fixa, dificultando ambientes diferentes.
- O segredo de API e as configurações RabbitMQ estão fixos em `application.properties`.
- A troca de tenant exige cuidado para restaurar `TenantContext` em `finally` e evitar reutilização de entidades entre schemas.
- A filtragem Java tem dialeto limitado; `order` não é aplicado e não existe limite superior gerado para `size`.
- `spring.main.allow-bean-definition-overriding=true` pode mascarar colisões de beans durante a migração.

- `enumConfigContext` é declarado em `.gonthera/project.json` e gera `EnumConfigContext`; não recriar esse enum manualmente.
- O interceptor apenas orquestra os fluxos: `ApplicationAuthenticationFlow` autentica usuários/rotas públicas e `IntegrationAuthenticationFlow` autentica `X-API-Key` com escopo de geração.
