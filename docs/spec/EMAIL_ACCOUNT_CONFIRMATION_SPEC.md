# Especificação — confirmação de conta e envio de e-mail com Resend

## 1. Objetivo

Esta especificação descreve a implementação de confirmação de conta do SmartReport, incluindo:

- criação da conta inicialmente inativa;
- geração e persistência do token de confirmação;
- composição da URL pública de confirmação;
- envio do e-mail transacional pela API do Resend;
- confirmação pelo endpoint padrão `verifyURL` gerado pelo Gonthera;
- bloqueio de login e da criação do schema do tenant antes da confirmação;
- reenvio seguro do e-mail para contas pendentes;
- divisão entre código gerado pelo Gonthera e código de negócio escrito no projeto.

O princípio mais importante é: **uma conta não confirmada não pode autenticar nem provocar a criação/migração do schema do cliente**. O schema é criado somente após a confirmação válida.

## 2. Arquitetura

```text
Frontend /register
        |
        v
POST /register
        |
        +--> cria usuário no schema admin
        |      user_confirm = false
        |      active = false
        |
        +--> cria user_confirmation com token aleatório
        |
        +--> renderiza register.mo
        |
        +--> POST https://api.resend.com/emails
        |
        v
Usuário recebe link /user-confirmation/{token}
        |
        v
Frontend chama GET /verifyURL?token={token}
        |
        +--> localiza token no admin
        +--> ativa e confirma usuário
        +--> cria/migra schema do tenant
        v
Conta liberada para login
```

Todos os dados de autenticação e confirmação ficam no contexto administrativo. O tenant do cliente só passa a existir fisicamente depois da confirmação.

## 3. Arquivos envolvidos

### 3.1 Código de negócio

| Arquivo | Responsabilidade |
|---|---|
| `config/security/service/AuthenticationService.java` | Cadastro, login, reenvio, token e montagem da URL |
| `services/email/EmailService.java` | Cliente HTTP do Resend e carregamento do template |
| `handlers/userconfirmation/UserConfirmation.java` | Implementa `verifyURL` e `resendConfirmation` |
| `repository/userconfirmation/UserConfirmationCustomRepository.java` | Busca por hash e por usuário |
| `config/security/handler/AuthenticationHandlerImpl.java` | Endpoints existentes de registro e autenticação |
| `resources/models/email/register.mo` | Template HTML da marca SmartReport |
| `config/migration/DBMigration.java` | Criação/migração do schema após confirmação |

Os caminhos Java acima são relativos a `src/main/java/com/smartverse/smartreportbackend/`.

### 3.2 Código gerado pelo Gonthera

| Arquivo/artefato | Origem |
|---|---|
| `UserConfirmationEntity` | entidade `userConfirmation` no `.gonthera/project.json` |
| `UserConfirmationRepository` | repositório base gerado |
| `VerifyURL` e `VerifyURLOutput` | endpoint `verifyURL` |
| `ResendConfirmation`, input e output | endpoint `resendConfirmation` |

Não se deve criar manualmente uma segunda estrutura paralela para esses contratos. Alterações estruturais devem começar no `.gonthera/project.json` e ser regeneradas com o `gonthera-cli`. O código manual implementa/extende os contratos gerados.

## 4. Modelo Gonthera

### 4.1 Entidade `userConfirmation`

Tabela: `user_confirmation`.

| Campo | Tipo | Obrigatório | Finalidade |
|---|---:|---:|---|
| `id` | UUID | sim | chave primária gerada pelo JPA |
| `userId` | UUID | sim no banco | referência lógica ao usuário administrativo |
| `hash` | string | sim no banco | token público de confirmação |

A entidade está com `generateDefaultControllers=false`. A exposição HTTP ocorre apenas pelos endpoints específicos.

### 4.2 Endpoint `verifyURL`

Contrato Gonthera:

```text
GET verifyURL?token={token}
Anônimo: sim
Entrada: token: string
Saída: authorize: boolean
```

Exemplo de resposta válida:

```json
{
  "authorize": true
}
```

Token inexistente retorna HTTP 200 com:

```json
{
  "authorize": false
}
```

O nome `verifyURL` é mantido por ser o padrão usado nos projetos Gonthera.

### 4.3 Endpoint `resendConfirmation`

```text
POST resendConfirmation
Anônimo: sim
Entrada: { "email": "usuario@exemplo.com" }
Saída: { "accepted": true }
```

Esse endpoint sempre responde de forma neutra. Ele não revela se o e-mail está cadastrado, confirmado ou inexistente. O envio só ocorre internamente quando existe uma conta ainda não confirmada e inativa.

## 5. Configuração

### 5.1 Variáveis de ambiente

| Variável | Obrigatória | Padrão | Uso |
|---|---:|---|---|
| `RESEND_KEY` | sim | nenhum | chave da API do Resend |
| `RESEND_FROM` | não | `SmartReport <no-reply@smartverse.com.br>` | remetente validado no Resend |
| `FRONTEND_BASE_URL` | recomendado | `http://localhost:4200` pelo fallback da classe | base da URL de confirmação |

Produção define em `application-prod.properties`:

```properties
app.frontend.base-url=${FRONTEND_BASE_URL:https://app.smartverse.com.br/smart-report}
```

A resolução usada pelo serviço é:

```java
@Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:4200}}")
private String frontendBaseUrl;
```

Com isso:

- local: `http://localhost:4200/user-confirmation/{token}`;
- produção: `https://app.smartverse.com.br/smart-report/user-confirmation/{token}`.

O código remove barras finais da base antes de acrescentar o caminho.

### 5.2 Requisitos do Resend

- A chave deve possuir permissão de envio.
- O domínio de `RESEND_FROM` deve estar validado no Resend.
- A chave nunca deve ser enviada ao frontend ou gravada no repositório.
- A API utilizada é `POST https://api.resend.com/emails`.

## 6. Implementação do cliente Resend

`EmailService` usa o `RestClient` do Spring:

```java
this.resend = builder
    .baseUrl("https://api.resend.com")
    .defaultHeader("Authorization", "Bearer " + resendKey)
    .build();
```

Payload enviado:

```json
{
  "from": "SmartReport <no-reply@smartverse.com.br>",
  "to": "usuario@exemplo.com",
  "subject": "Confirmação de email",
  "html": "<html>...</html>"
}
```

A requisição também envia:

```http
Idempotency-Key: {token-de-confirmacao}
```

O token funciona como chave de idempotência. Uma repetição acidental da mesma tentativa não deve gerar múltiplos e-mails no provedor. No reenvio é gerado um token novo, portanto a nova mensagem possui uma nova chave.

A resposta esperada possui um `id` não vazio:

```json
{
  "id": "identificador-resend"
}
```

Resposta vazia ou erro HTTP é convertido em `ServiceException` com HTTP `502 Bad Gateway`.

## 7. Template do e-mail

O template fica em:

```text
src/main/resources/models/email/register.mo
```

Ele contém:

- identidade visual SmartReport em verde/teal;
- marca textual SmartReport e selo `SR`;
- botão principal “Confirmar minha conta”;
- fallback com a URL em texto;
- indicação de que o SmartReport é um produto SmartVerse;
- HTML em tabelas e estilos inline para compatibilidade com clientes de e-mail.

Placeholder obrigatório:

```text
{{url}}
```

Carregamento e interpolação:

```java
var emailContent = emailService.loadModel("register")
    .replace("{{url}}", confirmationUrl);
```

Ao replicar, mantenha o placeholder idêntico ou altere simultaneamente o template e o código.

## 8. Fluxo de cadastro

Método central: `AuthenticationService.onRegisterUser`.

### 8.1 Validações iniciais

Nome, senha e e-mail não podem ser vazios. Dados inválidos resultam em HTTP 400.

### 8.2 Conta já existente

- Se existe conta ainda não confirmada e inativa: lança HTTP 409 com causa `ACCOUNT_CONFIRMATION_PENDING`. O frontend usa essa causa para abrir o modal de reenvio.
- Nos demais casos de duplicidade: mantém a resposta de conta já cadastrada definida pelo fluxo atual.

### 8.3 Estado inicial

O usuário é salvo com:

```java
user.setUserConfirm(false);
user.setActive(false);
```

Isso impede login e provisionamento prematuro.

### 8.4 Criação do token

Depois de persistir o usuário:

```java
var confirmation = new UserConfirmationEntity();
confirmation.setUserId(user.getId());
confirmation.setHash(UUID.randomUUID().toString());
confirmation = userConfirmationRepository.save(confirmation);
```

Não atribua manualmente o `id` da entidade quando ele estiver configurado com `@GeneratedValue`; deixe o Hibernate gerar a chave primária.

### 8.5 Transação

O cadastro é transacional. Uma falha no envio do Resend lança exceção e deve provocar rollback da criação do usuário e da confirmação, evitando deixar uma conta pendente causada por falha do provedor.

O fluxo não chama `loadMigrateTenants` durante o cadastro.

## 9. Bloqueio de login

Após validar e-mail e senha, o login verifica:

```java
if (!user.isUserConfirm() || !user.isActive()) {
    throw new ServiceException(
        HttpStatus.FORBIDDEN,
        "Confirme sua conta antes de fazer login");
}
```

Assim, mesmo com credenciais corretas, não há geração de JWT para conta pendente.

A interface deve exibir a causa devolvida pelo backend, em vez de substituir tudo por uma mensagem genérica.

## 10. Confirmação da conta

`UserConfirmation.verifyURL` executa:

1. busca `user_confirmation` por `hash`;
2. inicia a resposta com `authorize=false`;
3. se o token existe, define `authorize=true`;
4. busca o usuário pelo `userId`;
5. define `userConfirm=true` e `active=true`;
6. salva o usuário;
7. chama `dbMigration.loadMigrateTenants(user.getTenant())`.

A ordem é deliberada: o schema só é criado depois que o token foi validado e a conta foi ativada.

## 11. Reenvio da confirmação

`AuthenticationService.resendConfirmation` aplica proteção contra enumeração de usuários:

1. e-mail vazio retorna `true` sem consultar detalhes ao cliente;
2. e-mail inexistente retorna `true` sem enviar;
3. conta ativa ou confirmada retorna `true` sem enviar;
4. conta pendente recebe token novo;
5. o registro existente é atualizado ou criado se estiver ausente;
6. um novo e-mail é enviado.

Rotacionar o token invalida logicamente o link anterior porque a busca passa a encontrar somente o hash novo persistido.

## 12. Banco e multi-tenancy

A tabela `user_confirmation` é criada pela migration:

```text
V20241203080000002__Create_user_confirmation.sql
```

O fluxo de autenticação usa:

```java
TenantContext.setCurrentTenant("admin");
```

O nome físico do schema é construído pelo `DBMigration` como:

```text
{DATABASE}_{TENANT_EM_MAIÚSCULAS}
```

A confirmação é o ponto de provisionamento do tenant.

## 13. Checklist de replicação

1. Declarar `userConfirmation` no `.gonthera/project.json`.
2. Declarar `verifyURL` e `resendConfirmation` como endpoints anônimos.
3. Executar validação e geração pelo `gonthera-cli`.
4. Criar o repositório customizado estendendo o repositório gerado.
5. Implementar o handler usando as interfaces geradas.
6. Criar o `EmailService` com `RestClient` e `RESEND_KEY`.
7. Adicionar um template HTML com `{{url}}`.
8. Criar usuário como inativo e não confirmado.
9. Gerar o token somente após obter o ID do usuário.
10. Bloquear login para `!confirmed || !active`.
11. Migrar/criar o tenant somente dentro da confirmação válida.
12. Implementar resposta neutra no reenvio.
13. Configurar `FRONTEND_BASE_URL`, `RESEND_KEY` e domínio remetente.
14. Testar rollback quando o Resend estiver indisponível.
15. Testar token inválido, token válido, reenvio e login pendente.

## 14. Casos de teste mínimos

| Cenário | Resultado esperado |
|---|---|
| Cadastro válido | usuário inativo, token persistido, e-mail enviado |
| Resend indisponível | HTTP 502 e rollback do cadastro |
| Login antes da confirmação | HTTP 403, sem JWT |
| Token inválido | `authorize=false`, sem schema criado |
| Token válido | usuário ativo/confirmado e schema migrado |
| Reenvio de pendente | token rotacionado e novo e-mail enviado |
| Reenvio de inexistente | `accepted=true`, sem revelar existência |
| Reenvio de ativo | `accepted=true`, sem novo envio |

## 15. Cuidados e melhorias futuras

A implementação atual deve ser replicada junto com estes cuidados:

- adicionar expiração do token caso o projeto exija validade temporal;
- remover ou marcar o token como consumido após confirmação para uso único explícito;
- normalizar e-mail (`trim` e lowercase) antes de persistir e consultar;
- adicionar índice único para `user_confirmation.user_id` e `hash` quando apropriado;
- aplicar rate limit no endpoint de reenvio;
- nunca registrar `RESEND_KEY` nem o HTML completo contendo dados pessoais;
- preservar a interface gerada pelo Gonthera e manter regras de negócio fora de `*_gen`.
