# Especificação — planos, assinaturas e integração com Smart Payment

## 1. Objetivo

Esta especificação descreve o módulo de planos e pagamentos do SmartReport de forma replicável para outros produtos SmartVerse. Ela cobre:

- catálogo centralizado de planos;
- assinatura atual por tenant;
- medição e limite mensal de API;
- ciclos mensal, trimestral e semestral;
- descontos administrativos;
- criação de cobrança e link no `payment-backend`;
- confirmação assíncrona via RabbitMQ;
- validação, idempotência e retentativa de eventos;
- renovação sem perda do período já pago;
- troca futura de plano por períodos enfileirados;
- expiração automática e retorno ao plano gratuito;
- armazenamento de todos os dados no tenant administrativo;
- contratos e artefatos gerados pelo Gonthera.

## 2. Princípios da solução

1. **O backend calcula o preço.** O frontend envia apenas `planCode` e `billingCycle`.
2. **Descontos são administrativos.** Não existe endpoint público para editar ou consultar a tabela de descontos.
3. **Valores monetários enviados ao pagamento usam centavos inteiros.**
4. **Planos, assinaturas, consumo e pagamentos ficam no schema admin.** Cada registro carrega o identificador do tenant cliente.
5. **O webhook/evento não é confiado cegamente.** Serviço, cobrança, NSU e valores são comparados.
6. **Eventos são idempotentes.** O mesmo `paymentId` externo não pode aplicar o pagamento duas vezes.
7. **Renovação acrescenta tempo ao vencimento/cobertura existente.** O cliente não perde dias já pagos.
8. **Conta vencida retorna ao FREE**, salvo quando existir outro período pago válido para iniciar.
9. **Estruturas devem nascer no Gonthera.** Código manual implementa regras e repositórios customizados, sem duplicar entidades geradas.

## 3. Visão arquitetural

```text
Frontend seleciona plano + ciclo
          |
          v
POST /createPaymentLink
          |
          +--> TenantContext atual identifica cliente
          +--> troca temporária para tenant admin
          +--> valida plano e desconto
          +--> calcula preço em centavos
          +--> cria/reutiliza subscription_payment PENDING
          |
          v
POST payment-backend/paymentLink
  service=REPORT, value, client_id
          |
          v
Frontend redireciona para URL de checkout
          |
          v
Pagamento confirmado no payment-backend
          |
          v
RabbitMQ exchange smart.payment.events
routing key payment.confirmed.REPORT
          |
          v
fila do SmartReport -> PaymentConfirmedListener
          |
          +--> inbox idempotente
          +--> valida evento contra cobrança
          +--> marca pagamento PAID
          +--> calcula cobertura sem perder prazo
          +--> atualiza assinatura atual se o período começou
          v
scheduler sincroniza vencimentos e retenta falhas
```

## 4. Divisão entre Gonthera e código manual

### 4.1 Declarado no `.gonthera/project.json`

- entidades `subscriptionPlan`, `tenantSubscription`, `apiMonthlyUsage`;
- entidades `billingDiscount`, `subscriptionPayment`, `paymentEventInbox`;
- DTOs `planOption`, `apiUsageHistoryItem`, `paymentHistoryItem`, `paymentConfirmedEvent`;
- enums `billingCycle` e `paymentStatus`;
- endpoints `getPlanOverview`, `getApiUsageHistory`, `createPaymentLink`, `getPaymentHistory`;
- subscription RabbitMQ `paymentConfirmed`.

Após alterar qualquer item estrutural, validar e regenerar com `gonthera-cli`. Não editar como fonte de verdade as classes em `smartreportbackend_gen`.

### 4.2 Código manual

| Arquivo | Responsabilidade |
|---|---|
| `services/plan/PlanBusinessService.java` | visão de planos, consumo, limite e expiração |
| `services/payment/PaymentBusinessService.java` | cálculo, cobrança, evento, cobertura e retentativa |
| `services/payment/SmartPaymentClient.java` | chamada HTTP ao payment-backend |
| `handlers/plan/PlanHandler.java` | implementa endpoints gerados de plano |
| `handlers/payment/PaymentHandler.java` | implementa endpoints gerados de pagamento |
| `config/messaging/PaymentRabbitConfig.java` | exchange, fila, binding e conversor |
| `messaging/payment/PaymentConfirmedListener.java` | JSON Rabbit para DTO de confirmação |
| `scheduling/SubscriptionExpirationScheduler.java` | expiração e retry periódico |
| `repository/plan/*CustomRepository.java` | consultas específicas de plano/uso |
| `repository/payment/*CustomRepository.java` | consultas específicas de cobrança/inbox |
| `config/migration/DBMigration.java` | garante migrations no admin |

Os caminhos são relativos a `src/main/java/com/smartverse/smartreportbackend/`.

## 5. Enums e persistência

### 5.1 `BillingCycle`

```java
MONTHLY,      // ordinal 0
QUARTERLY,    // ordinal 1
SEMIANNUAL    // ordinal 2
```

### 5.2 `PaymentStatus`

```java
PENDING,      // ordinal 0
PAID,         // ordinal 1
FAILED,       // ordinal 2
CANCELLED,    // ordinal 3
EXPIRED       // ordinal 4
```

Os enums JPA são persistidos numericamente em colunas `INTEGER`. Como o valor é ordinal, **nunca reordenar nem remover valores existentes**. Novos valores devem ser acrescentados no final ou migrados explicitamente.

O `status` da tabela `payment_event_inbox` é texto porque representa o estado técnico do processamento (`RECEIVED`, `PROCESSED`, `FAILED`) e não o enum `PaymentStatus`.

## 6. Modelo de dados

### 6.1 `subscription_plan`

Catálogo administrativo dos planos.

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | UUID | PK |
| `code` | VARCHAR(40) | único; identificador estável |
| `name` | VARCHAR(80) | nome exibido |
| `description` | VARCHAR(255) | texto comercial |
| `monthly_price` | NUMERIC(12,2) | preço-base mensal; nulo em plano customizado |
| `api_monthly_limit` | INTEGER | limite; nulo representa ilimitado/customizado |
| `custom_plan` | BOOLEAN | impede checkout automático |
| `active` | BOOLEAN | controla disponibilidade |
| `display_order` | INTEGER | ordenação no frontend |

Seeds atuais:

| Código | Mensalidade | Limite API | Tipo |
|---|---:|---:|---|
| `FREE` | R$ 0,00 | 150 | gratuito |
| `STARTER` | R$ 19,99 | 500 | pago |
| `PRO` | R$ 39,99 | 1200 | pago |
| `CUSTOM` | nulo | nulo | negociação manual |

### 6.2 `tenant_subscription`

Estado materializado da assinatura atualmente ativa.

| Coluna | Tipo | Finalidade |
|---|---|---|
| `id` | UUID | PK gerada |
| `tenant` | VARCHAR(120) | único |
| `plan_code` | VARCHAR(40) | plano efetivamente ativo |
| `started_at` | TIMESTAMP | início do período ativo |
| `billing_cycle` | INTEGER | ciclo do período; nulo no FREE |
| `expires_at` | TIMESTAMP | vencimento; nulo no FREE |

Ela não substitui o histórico. É uma projeção do período vigente para consultas rápidas.

### 6.3 `api_monthly_usage`

| Coluna | Tipo | Finalidade |
|---|---|---|
| `id` | UUID | PK |
| `tenant` | VARCHAR(120) | cliente |
| `period` | VARCHAR(7) | `YYYY-MM` |
| `amount` | INTEGER | requisições consumidas |

Restrição única: `(tenant, period)`.

A consulta usa `PESSIMISTIC_WRITE` para evitar que requisições concorrentes percam incrementos.

### 6.4 `billing_discount`

Tabela exclusivamente administrativa, sem endpoint exposto.

| Coluna | Tipo | Finalidade |
|---|---|---|
| `id` | UUID | PK |
| `billing_cycle` | INTEGER | enum ordinal, único |
| `months` | INTEGER | meses adicionados, maior que zero |
| `discount_percentage` | NUMERIC(5,2) | entre 0 e 100 |
| `active` | BOOLEAN | disponibilidade do ciclo |

Configuração atual:

| Ciclo | Ordinal | Meses | Desconto |
|---|---:|---:|---:|
| MONTHLY | 0 | 1 | 0% |
| QUARTERLY | 1 | 3 | 10% |
| SEMIANNUAL | 2 | 6 | 15% |

A tabela é a fonte de verdade. O frontend pode apresentar os percentuais, mas o valor final sempre é recalculado pelo backend.

### 6.5 `subscription_payment`

Registro central da cobrança e do período adquirido.

| Coluna | Tipo | Finalidade |
|---|---|---|
| `id` | UUID | PK interna enviada como `client_id` ao payment-backend |
| `tenant` | VARCHAR(120) | proprietário da cobrança |
| `plan_code` | VARCHAR(40) | plano comprado |
| `billing_cycle` | INTEGER | enum ordinal |
| `months` | INTEGER | meses congelados na compra |
| `base_amount_cents` | INTEGER | valor antes do desconto |
| `discount_percentage` | NUMERIC(5,2) | desconto congelado na compra |
| `amount_cents` | INTEGER | total esperado |
| `status` | INTEGER | `PaymentStatus` ordinal |
| `order_nsu` | VARCHAR(255) | pedido no payment-backend |
| `transaction_nsu` | VARCHAR(255) | transação confirmada |
| `created_at` | TIMESTAMP | criação |
| `updated_at` | TIMESTAMP | última alteração |
| `paid_at` | TIMESTAMP | data informada pelo provedor |
| `coverage_start_at` | TIMESTAMP | início do direito adquirido |
| `coverage_end_at` | TIMESTAMP | fim do direito adquirido |

Os campos de preço, desconto e meses são snapshots. Mudanças futuras na tabela de desconto não alteram uma cobrança já criada.

O ID possui `@GeneratedValue`. Não deve ser preenchido com `UUID.randomUUID()` antes de `save()`: isso faz o Spring Data escolher `merge` para uma linha inexistente e pode provocar `StaleObjectStateException`.

### 6.6 `payment_event_inbox`

Inbox transacional para idempotência e retry.

| Coluna | Tipo | Finalidade |
|---|---|---|
| `id` | UUID | PK interna gerada pelo Hibernate |
| `payment_id` | UUID | ID externo do evento, único |
| `client_id` | UUID | ID da `subscription_payment` |
| `order_nsu` | VARCHAR(255) | correlação do pedido |
| `transaction_nsu` | VARCHAR(255) | correlação da transação |
| `status` | VARCHAR(30) | RECEIVED, PROCESSED ou FAILED |
| `amount` | INTEGER | valor cobrado informado no evento |
| `paid_amount` | INTEGER | valor efetivamente pago |
| `paid_at` | TIMESTAMP | momento do pagamento |
| `failure_reason` | VARCHAR(1000) | erro truncado para retry/diagnóstico |
| `received_at` | TIMESTAMP | recebimento |
| `processed_at` | TIMESTAMP | conclusão |

`payment_id` começou como PK, mas passou a ser uma chave única externa. A entidade precisa de uma PK interna gerada para que o JPA use `persist` corretamente.

## 7. Migrations

| Migration | Conteúdo |
|---|---|
| `V20260721210000001__Create_subscription_catalog.sql` | planos, assinatura, uso mensal e seeds |
| `V20260724010000001__Create_subscription_billing.sql` | ciclos, descontos, cobranças e inbox no admin |
| `V20260724013000002__Add_internal_payment_event_id.sql` | PK interna da inbox e unique no evento externo |
| `V20260724140000003__Convert_user_configuration_enums_to_integer.sql` | compatibilidade dos enums antigos do projeto |

As tabelas sensíveis de billing são criadas somente quando:

```sql
upper(current_schema()) LIKE '%\_ADMIN' ESCAPE '\'
```

Isso evita replicar dados financeiros em schemas individuais de cliente.

Nunca editar uma migration já aplicada. Criar uma migration incremental. Se o Flyway acusar “applied migration not resolved locally”, restaurar o arquivo ou reparar conscientemente o histórico; não ignorar a validação.

## 8. Contexto de tenant administrativo

Antes de entrar no admin, o serviço captura o tenant cliente:

```java
var tenant = requireTenant();
```

Depois executa operações financeiras em:

```java
private <T> T inAdmin(Supplier<T> operation) {
    var previous = TenantContext.getCurrentTenant();
    try {
        TenantContext.setCurrentTenant("admin");
        migration.loadMigrateTenants("admin");
        return operation.get();
    } finally {
        TenantContext.setCurrentTenant(previous);
    }
}
```

Regras:

- capturar o tenant cliente antes da troca;
- persistir esse valor na coluna `tenant`;
- iniciar a transação depois que o contexto foi alterado;
- restaurar o contexto no `finally`;
- garantir as migrations do admin antes da operação.

Isso explica por que os dados ficam centralizados sem perder a associação ao cliente.

## 9. Visão de planos e consumo

### 9.1 `GET getPlanOverview`

Retorna:

```json
{
  "currentPlan": {
    "id": "uuid",
    "code": "FREE",
    "name": "Free",
    "description": "...",
    "monthlyPrice": 0.0,
    "apiMonthlyLimit": 150,
    "customPlan": false,
    "currentPlan": true
  },
  "plans": [],
  "apiUsed": 10,
  "apiRemaining": 140
}
```

Se ainda não existir `tenant_subscription`, o serviço cria automaticamente uma assinatura FREE, sem vencimento.

### 9.2 `GET getApiUsageHistory`

Retorna o consumo ordenado por período decrescente:

```json
{
  "history": [
    { "period": "2026-07", "amount": 10 }
  ]
}
```

### 9.3 Consumo de API

`consumeApiRequest(tenant)`:

1. resolve o plano atual no admin;
2. obtém/cria o uso do mês corrente;
3. bloqueia a linha com `PESSIMISTIC_WRITE`;
4. compara o total atual com o limite;
5. lança HTTP 402 quando o limite foi atingido;
6. incrementa e salva.

Plano com `apiMonthlyLimit=null` é tratado como sem limite.

## 10. Criação do link de pagamento

### 10.1 Contrato público

```text
POST createPaymentLink
Autenticado: sim
```

Entrada:

```json
{
  "planCode": "PRO",
  "billingCycle": "QUARTERLY"
}
```

Saída:

```json
{
  "url": "https://checkout...",
  "orderNsu": "...",
  "status": "PENDING",
  "reused": false
}
```

O `Authorization` recebido pelo SmartReport é encaminhado ao payment-backend.

### 10.2 Validações

- tenant deve existir e não pode ser o próprio admin;
- header `Authorization` é obrigatório;
- `planCode` e `billingCycle` são obrigatórios;
- FREE não gera checkout;
- plano precisa existir e estar ativo;
- plano customizado não gera checkout automático;
- preço mensal precisa ser positivo;
- ciclo precisa existir e estar ativo na tabela administrativa.

### 10.3 Cálculo

```text
base = monthlyPrice × months
total = base × (1 - discountPercentage / 100)
```

Arredondamento: duas casas, `HALF_UP`.

Conversão para centavos ocorre somente depois do cálculo:

```java
amountCents = total.movePointRight(2).intValueExact();
```

Exemplo para PRO trimestral:

```text
39,99 × 3 = 119,97
119,97 × 0,90 = 107,973
arredondado = 107,97
centavos = 10797
```

### 10.4 Reutilização local

Antes de criar uma cobrança, procura-se a última cobrança `PENDING` com o mesmo tenant, plano e ciclo. Se existir, ela é reutilizada. Caso contrário é criada uma nova.

## 11. Integração HTTP com payment-backend

### 11.1 Configuração

```properties
PAYMENT_SERVICE_BASE_URL=https://app.smartverse.com.br/api/payment-service
```

Esse endereço já é o fallback de `SmartPaymentClient`.

### 11.2 Requisição

```http
POST {PAYMENT_SERVICE_BASE_URL}/paymentLink
Authorization: Bearer {jwt-do-cliente}
Content-Type: application/json
```

Payload:

```json
{
  "service": "REPORT",
  "value": 10797,
  "client_id": "uuid-da-subscription-payment"
}
```

Semântica:

- `service`: identifica o produto consumidor e define o routing key posterior;
- `value`: total em centavos calculado pelo consumidor;
- `client_id`: ID interno da cobrança no SmartReport, usado para localizar a linha quando o evento voltar.

O JWT precisa ser aceito pelo payment-backend. Em testes locais contra produção, ambos precisam compartilhar a configuração de confiança/autenticação esperada.

### 11.3 Resposta aceita

O cliente aceita `order_nsu` e também `orderNsu` por compatibilidade. Exige:

- `url`;
- `order_nsu`/`orderNsu`;
- `status`;
- `reused` opcional, assumido `false` quando ausente.

Falha HTTP ou resposta inválida vira HTTP 502 no SmartReport.

Depois da resposta, `orderNsu` é gravado na cobrança PENDING para validação do evento.

## 12. Mensageria RabbitMQ

### 12.1 Topologia

| Item | Valor |
|---|---|
| Exchange | `smart.payment.events` |
| Tipo | topic |
| Routing key | `payment.confirmed.REPORT` |
| Fila padrão | `smart.payment.confirmed.report` |
| Variável da fila | `PAYMENT_CONFIRMED_QUEUE` |
| Durabilidade | durable |

Manifesto Gonthera:

```json
{
  "messaging": {
    "RabbitMq": {
      "pub": [],
      "sub": [
        {
          "name": "paymentConfirmed",
          "queue": "${PAYMENT_CONFIRMED_QUEUE:smart.payment.confirmed.report}"
        }
      ]
    }
  }
}
```

### 12.2 Variáveis RabbitMQ

```properties
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=...
RABBITMQ_PASSWORD=...
PAYMENT_CONFIRMED_QUEUE=smart.payment.confirmed.report
```

Para consumir eventos reais localmente, use uma fila exclusiva:

```properties
PAYMENT_CONFIRMED_QUEUE=smart.payment.confirmed.report.local.nome-do-dev
```

Nunca use a mesma fila de produção para dois ambientes independentes: consumidores da mesma fila competem, e apenas um recebe cada mensagem. Filas diferentes vinculadas ao mesmo exchange/routing key recebem cópias independentes.

A credencial Rabbit precisa ter permissão para declarar a fila e criar o binding no exchange.

### 12.3 Conversão da mensagem

O payment-backend publica JSON com header semelhante a:

```text
__TypeId__=com.smartverse.payment.messaging.PaymentConfirmedEvent
contentType=application/json
```

O subscriber Gonthera recebe `String`. O `Jackson2JsonMessageConverter` padrão tenta instanciar o tipo externo ou converter um objeto JSON em `String`, causando `MismatchedInputException` antes do listener.

`PaymentRabbitConfig` sobrescreve o conversor de entrada para retornar o corpo UTF-8 bruto:

```java
@Override
public Object fromMessage(Message message) {
    return new String(message.getBody(), StandardCharsets.UTF_8);
}
```

A conversão de saída continua delegada ao `Jackson2JsonMessageConverter`.

`PaymentConfirmedListener` então controla a desserialização:

```java
payments.receive(
    mapper.readValue(message, PaymentConfirmedEventDTO.class));
```

Esse detalhe deve ser replicado quando produtor e consumidor possuem classes Java diferentes, mesmo com JSON compatível.

## 13. Contrato do evento confirmado

DTO:

```json
{
  "paymentId": "uuid-do-evento-no-payment-backend",
  "clientId": "uuid-da-subscription-payment",
  "service": "REPORT",
  "orderNsu": "pedido-123",
  "transactionNsu": "transacao-456",
  "amount": 10797,
  "paidAmount": 10797,
  "paidAt": "2026-07-24T13:39:00"
}
```

Distinção crítica:

- `paymentId`: identidade externa do pagamento/evento; chave idempotente da inbox;
- `clientId`: identidade interna da cobrança no sistema consumidor.

Não inverter os dois.

## 14. Processamento e validações do evento

### 14.1 Validação estrutural

O evento é rejeitado quando:

- evento ou IDs são nulos;
- NSUs são nulos;
- valores são nulos ou não positivos;
- `paidAt` é nulo;
- `service` não é exatamente `REPORT`.

### 14.2 Registro idempotente

Antes de aplicar o pagamento:

1. busca inbox por `paymentId`;
2. se já está `PROCESSED`, termina sem reaplicar;
3. se não existe, salva todos os dados como `RECEIVED`;
4. se existe como `RECEIVED` ou `FAILED`, permite nova tentativa.

A constraint unique em `payment_id` reforça a idempotência no banco.

### 14.3 Correspondência financeira

A cobrança é localizada por `event.clientId`. Depois são comparados:

- `payment.orderNsu == event.orderNsu`;
- `payment.amountCents == event.amount`;
- `event.paidAmount >= payment.amountCents`.

Somente então ela pode virar `PAID`.

## 15. Cobertura, renovação e troca de plano

A data-base é `event.paidAt`.

O serviço procura o maior `coverageEndAt` futuro entre pagamentos PAID do tenant. O início do novo período é o maior valor entre:

- fim da última cobertura paga futura;
- vencimento atual da assinatura;
- data do pagamento, quando não existe prazo futuro.

Depois:

```text
coverageEndAt = coverageStartAt + months
```

Consequências:

- renovar antes do vencimento não elimina os dias restantes;
- comprar períodos repetidos cria uma fila cronológica de cobertura;
- comprar outro plano antes do vencimento agenda esse plano para quando sua cobertura começar;
- `tenant_subscription` só muda imediatamente se `coverageStartAt <= now`;
- períodos futuros permanecem registrados em `subscription_payment` até o scheduler sincronizá-los.

## 16. Expiração automática

Scheduler:

```properties
SUBSCRIPTION_EXPIRATION_INTERVAL_MS=60000
```

A cada execução:

1. busca assinaturas com `expiresAt <= now`;
2. procura pagamento PAID cuja cobertura contenha o momento atual;
3. se encontra, ativa o plano desse período;
4. se não encontra, muda para FREE;
5. no FREE, limpa `billingCycle` e `expiresAt`.

A mesma rotina também chama `retryFailedEvents()`.

A sincronização também ocorre sob demanda ao consultar o plano atual, evitando depender exclusivamente do scheduler.

## 17. Retentativa e falhas

Se uma exceção ocorrer depois de registrar a inbox:

- o evento vira `FAILED`;
- a mensagem de erro é truncada em 1000 caracteres;
- o payload essencial permanece salvo;
- o scheduler reconstrói o DTO e chama `receive` novamente.

Quando conclui:

- inbox vira `PROCESSED`;
- `failureReason` é limpo;
- `processedAt` é preenchido.

O listener gerado captura exceções do método de negócio e registra log. A persistência em inbox garante que falhas de negócio possam ser retomadas mesmo se a mensagem Rabbit for reconhecida.

Erros de conversão ocorridos antes do listener não entram na inbox. Por isso o conversor de corpo bruto é obrigatório; mensagens anteriormente rejeitadas precisam ser republicadas ou recuperadas de DLQ, se configurada.

## 18. Histórico de pagamentos

```text
GET getPaymentHistory
Autenticado: sim
```

Resposta:

```json
{
  "payments": [
    {
      "id": "uuid",
      "planCode": "PRO",
      "billingCycle": "QUARTERLY",
      "amountCents": 10797,
      "status": "PAID",
      "createdAt": "2026-07-24T13:00:00",
      "paidAt": "2026-07-24T13:39:00",
      "coverageStartAt": "2026-07-24T13:39:00",
      "coverageEndAt": "2026-10-24T13:39:00"
    }
  ]
}
```

A consulta sempre filtra pela coluna `tenant` capturada da autenticação, embora a tabela esteja no admin.

## 19. Fluxo esperado no frontend

1. consultar `getPlanOverview`;
2. exibir planos ativos;
3. ao selecionar plano pago, abrir seleção de ciclo;
4. mostrar mensal, trimestral com 10% e semestral com 15%;
5. enviar somente `planCode` e `billingCycle`;
6. receber a URL do checkout;
7. redirecionar na mesma aba;
8. após retorno, atualizar overview e histórico;
9. permitir renovação também para usuário já pago.

O frontend não envia preço nem percentual como fonte de verdade.

## 20. Checklist de replicação

### Gonthera

1. Criar enums preservando a ordem documentada.
2. Criar as seis entidades persistidas.
3. Criar DTOs de visão, histórico e evento.
4. Criar os quatro endpoints autenticados.
5. Criar a subscription Rabbit parametrizada.
6. Validar e gerar via `gonthera-cli`.
7. Nunca manter entidades paralelas criadas manualmente.

### Banco

1. Criar catálogo, assinatura e consumo.
2. Criar billing somente no schema admin.
3. Persistir enums em `INTEGER`, nunca `VARCHAR`.
4. Criar unique de `(tenant, period)`.
5. Criar unique de `billing_cycle` nos descontos.
6. Criar PK interna na inbox e unique em `payment_id`.
7. Criar índices de histórico e cobertura.
8. Seedar planos e descontos.
9. Usar migrations incrementais; não reescrever versões aplicadas.

### Negócio

1. Capturar tenant antes de entrar no admin.
2. Calcular preço no backend com `BigDecimal`.
3. Congelar preço, meses e desconto na cobrança.
4. Deixar o Hibernate gerar UUIDs de entidades com `@GeneratedValue`.
5. Encaminhar JWT ao payment-backend.
6. Usar o ID interno da cobrança como `client_id`.
7. Validar serviço, NSU, valor e pagamento recebido.
8. Registrar inbox antes de aplicar efeitos.
9. Somar novos períodos ao vencimento existente.
10. Sincronizar períodos futuros e retornar ao FREE ao final.

### Mensageria

1. Usar exchange e routing key acordados com o produtor.
2. Declarar fila durable.
3. Usar fila exclusiva por ambiente.
4. Configurar conversor para corpo JSON bruto quando o subscriber for `String`.
5. Persistir falhas para retry.
6. Configurar DLQ em produção como evolução operacional.

## 21. Casos de teste mínimos

| Cenário | Resultado esperado |
|---|---|
| FREE no checkout | HTTP 400 |
| Plano inexistente/inativo | HTTP 404 |
| Plano customizado | HTTP 400 |
| Ciclo inativo | HTTP 400 |
| Trimestral | três meses e 10% calculados no backend |
| Semestral | seis meses e 15% calculados no backend |
| Repetição antes do pagamento | cobrança PENDING reutilizada |
| Resposta inválida do payment-backend | HTTP 502 |
| Evento de outro serviço | rejeitado |
| NSU diferente | rejeitado e inbox FAILED |
| Valor menor/incorreto | rejeitado e inbox FAILED |
| Evento duplicado processado | nenhum efeito adicional |
| Renovação antecipada | começa após o vencimento existente |
| Compra de outro plano antecipada | plano atual permanece até o próximo período |
| Vencimento com período seguinte | próximo plano é ativado |
| Vencimento sem período seguinte | plano FREE |
| Falha transitória | scheduler retenta a inbox |
| Dois consumos concorrentes | contador consistente pelo lock pessimista |
| Local + produção no Rabbit | filas distintas recebem cópias independentes |

## 22. Cuidados e evoluções recomendadas

- configurar DLQ e política explícita de retry Rabbit;
- adicionar métricas para inbox FAILED, atraso de processamento e divergência financeira;
- criar reconciliação periódica com o payment-backend para eventos perdidos;
- definir política para cobranças PENDING antigas e marcar como EXPIRED;
- validar moeda quando o contrato do payment-backend passar a suportá-la;
- usar UTC de ponta a ponta ou incluir offset no contrato de `paidAt`;
- considerar locking/constraint adicional para cliques simultâneos de checkout;
- não expor endpoint administrativo de descontos no serviço do cliente;
- preservar ordinais dos enums ou migrar explicitamente ao alterar sua ordem;
- testar as migrations em schema admin já populado antes do deploy.
