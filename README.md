# LLM Connector (Java + Quarkus + Maven)

Conector de IA com arquitetura hexagonal para receber dados/arquivos via REST, rotear por configuracao em Oracle, integrar com OpenAI e Anthropic, e processar fila RabbitMQ.

## Stack e padrao

- Java 21 (LTS)
- Quarkus 3.20.5 (LTS)
- Maven
- Oracle JDBC + Agroal
- REST (Quarkus REST)
- RabbitMQ (SmallRye Reactive Messaging)

## Arquitetura hexagonal

- `application/usecase`
  - `InferenceUseCase`: regra de negocio de inferencia
  - `TrainingJobUseCase`: regra de negocio para fila/job
- `application/port/out`
  - Portas para Oracle, catalogo de providers e publicacao de eventos
- `adapter/in/rest`
  - Endpoints REST
- `adapter/in/scheduler`
  - Disparo periodico do job
- `adapter/in/queue`
  - Consumo de mensagens RabbitMQ de treinamento
- `adapter/out/oracle`
  - Implementacao JDBC Oracle das portas
- `adapter/out/llm`
  - Catalogo de providers
- `provider`
  - Conectores de IA (OpenAI e Anthropic)
- `adapter/out/queue`
  - Publicacao de eventos no RabbitMQ

## Endpoints REST

- `POST /v1/connector/query`
- `PUT /v1/connector/query`
- `POST /v1/connector/query/stream`
- `POST /v1/connector/file` (multipart)
- `POST /v1/connector/training/enqueue`
- `GET /v1/connector/providers`
- `GET /v1/connector/health`

Headers obrigatorios para inferencia:
- `X-Data-Type`
- `X-Data-Characteristic`

Headers opcionais para personalizacao:
- `X-Module-Key` (ex.: `LEARNING`, `TEACHING`, `STUDENT`, `TEACHER`)
- `X-Profile-Id` (ex.: id do aluno/perfil)

Seguranca opcional:
- Defina `CONNECTOR_API_KEY` e envie `X-Connector-Key` em todas as chamadas.

## RabbitMQ

### Entrada (consumo)

Canal: `training-input`

Formato esperado da mensagem JSON:
```json
{
  "dataType": "SUPPORT",
  "dataCharacteristic": "FAQ",
  "content": "Texto para atualizacao de contexto",
  "routeKey": "SUPPORT_DEFAULT",
  "moduleKey": "LEARNING",
  "profileId": "ALUNO_123",
  "sourceType": "RABBIT"
}
```

### Saida (publicacao)

Canal: `connector-events`

Eventos publicados:
- `INFERENCE_COMPLETED`
- `TRAINING_ITEM_PROCESSED`

## Oracle

Execute:
```sql
@sql/schema.sql
```

Tabelas:
- `llm_route_config`
- `llm_route_context`
- `llm_training_queue`
- `llm_module_context`
- `llm_profile_context`
- `llm_request_log`

## Configuracao

Veja `src/main/resources/application.properties` e `.env.example` para:
- Oracle
- OpenAI
- Anthropic
- Scheduler do job
- RabbitMQ

## Execucao

```bash
mvn quarkus:dev
```

## Verificacao de vulnerabilidades (CVE)

```bash
mvn org.owasp:dependency-check-maven:check
```

## Estrategia de aprendizado continuo

O objetivo do JOB e manter o conhecimento sempre atualizado para cada dominio de negocio. A estrategia recomendada neste projeto e:

- Atualizar continuamente a base de conhecimento por modulo (ex.: aprendizagem, ensino, aluno, professor).
- Manter contexto especifico por perfil/aluno para personalizacao (historico, caracteristicas, nivel, lacunas).
- Usar RAG para recuperar contexto certo no momento da inferencia.
- Opcionalmente, usar modelo especializado por tema/modulo quando fizer sentido.
- O JOB grava o conhecimento processado em `llm_module_context` e `llm_profile_context`, e a inferencia consulta essas fontes.

Exemplo (Sistema Tutor Inteligente):
- Modulos com bases separadas: `LEARNING`, `TEACHING`, `STUDENT`, `TEACHER`.
- Perfil individual por aluno: contexto proprio para adaptar resposta, trilha e explicacao.
- O JOB ingere novos dados periodicamente, enriquece a base e deixa o conector pronto para responder com contexto de modulo + perfil.
