# CRMToperty

Versión mínima de un sistema que recibe aplicaciones de vivienda por dos caminos (un CSV y
un webhook), las lleva a un mismo modelo, las evalúa con reglas configurables, notifica al
aplicante y deja todo listo para que el equipo comercial lo consulte. Corre solo cada pocos
minutos y es seguro repetirlo.

Stack: Java 17, Spring Boot 4, PostgreSQL 18, Flyway, Thymeleaf.

## Cómo levantarlo

Requisitos: Docker y un JDK 17 o superior (Maven viene incluido con `mvnw`).

```bash
docker compose up -d          # Postgres en localhost:5432
./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
```

Al arrancar, Flyway crea el esquema y el proceso automático corre a los 10 segundos y
luego cada 2 minutos: lee `data/fuente-a.csv`, evalúa lo pendiente y registra las notificaciones.

**Pantalla del equipo comercial:** http://localhost:8080 (redirige a `/ui/aplicaciones`).

**Enviar los webhooks de ejemplo** (cada elemento de `data/webhooks.json` es el cuerpo de un POST;
el tercero es un reintento del segundo):

```bash
.\scripts\enviar-webhooks.ps1     # Windows (PowerShell)
scripts/enviar-webhooks.sh        # Linux / macOS (usa python3)
```

O uno a mano:

```bash
curl -X POST localhost:8080/aplicaciones -H "Content-Type: application/json" -d '{
  "submission_id": "sub_3b81de20", "submitted_at": "2026-09-09T09:41:33Z", "program": "bahia",
  "applicant": {"first_name": "Valentina", "last_name": "Cárdenas Ruiz",
                "email": "vcardenas@gmail.com", "phone": "+57 320 445 1290"},
  "answers": {"city": "Barranquilla", "monthly_income": 8900000, "savings": 41500000}}'
```

**Correr el proceso sin esperar:** `curl -X POST localhost:8080/procesos/ejecutar`

**Pruebas:** `./mvnw test`. Corren contra Postgres real, en la base `crm_toperty_test`, que se
crea sola la primera vez que se levanta el volumen. Si el volumen ya existía:

```bash
docker exec crm-toperty-db psql -U crm_user -d crm_toperty -c "CREATE DATABASE crm_toperty_test OWNER crm_user"
```

| Variable | Por defecto | Para qué |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | la base de `compose.yml` | Conexión |
| `PROGRAMAS_PATH` | `data/programas.json` | Reglas por programa |
| `FUENTE_A_PATH` | `data/fuente-a.csv` | Archivo de la fuente A |
| `PROCESO_INTERVALO` | `PT2M` | Cada cuánto corre el proceso |
| `PROCESO_HABILITADO` | `true` | Apagar el proceso automático |

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/aplicaciones` | Webhook. `202` si es nueva; `200` con la misma aplicación si es un reintento; `400` sin `submission_id` |
| `POST` | `/procesos/ejecutar` | Dispara una corrida; `409` si ya hay una en curso |
| `GET` | `/ui/aplicaciones` | Pantalla: lista con pestañas por resultado (con contadores), filtro por programa y paginación |
| `GET` | `/ui/aplicaciones/{id}` | Pantalla: detalle con cada regla (valor frente a umbral), advertencias, notificación, otras aplicaciones de la persona, reglas aplicadas y registro original |

La pantalla se renderiza en el servidor con Thymeleaf, dentro de la misma aplicación: un solo
proyecto y un solo comando para levantarlo. Los filtros son parámetros de la URL, así que se
pueden compartir y el botón "atrás" funciona. El CSS es propio, sin CDN (funciona sin internet),
con modo claro y oscuro y diseño adaptable a celular.

## Cómo está armado

```
fuente-a.csv ──► LectorFuenteA ──┐
                                 ├─► AplicacionEntrante ─► Normalizador ─► IngestaService ─► aplicaciones (PENDIENTE)
POST /aplicaciones ─► Webhook ───┘

Cada 2 min (o POST /procesos/ejecutar), con un advisory lock de Postgres:
  leer CSV → cargar programas.json → por cada PENDIENTE, en una transacción:
  MotorReglas (APROBADA / RECHAZADA / INCOMPLETA) + INSERT notificación ON CONFLICT DO NOTHING
```

La idempotencia está en la base de datos, no en la memoria del proceso:

| Qué no se puede duplicar | Cómo se garantiza |
|---|---|
| La misma aplicación | `UNIQUE (fuente, id_externo)` |
| La misma persona | `UNIQUE (email)` |
| Una decisión | Solo se evalúa lo `PENDIENTE`; la entidad no deja salir de ese estado dos veces |
| Una notificación | `UNIQUE (aplicacion_id, tipo)` con `ON CONFLICT DO NOTHING` |
| Dos corridas a la vez | `pg_try_advisory_lock` |

## Qué asumí

**Identidad y duplicados**
- Los datos no traen documento de identidad. Una persona se identifica por su **email
  normalizado** (minúsculas, sin espacios). Sin email, se busca por celular, pero solo entre
  personas que tampoco tienen email. Sin ninguno de los dos, se crea una persona nueva que
  no se puede deduplicar (caso Natalia Quintero).
- El celular no es único, porque dos personas de una familia pueden compartir número. Si el
  email lleva a una persona y el celular a otra, gana el email.
- Una persona que aplica dos veces tiene dos aplicaciones independientes (caso Juan Camilo
  Torres: CSV y webhook). Sus datos de contacto toman el más reciente en orden de llegada;
  cada aplicación conserva lo que llegó en `payload_crudo`.
- El CSV no trae id por fila: el id es el SHA-256 del contenido de la fila. El webhook usa
  `submission_id`. Un reintento del webhook responde `200` con la aplicación existente, no un
  error, para que el emisor no siga reintentando.
- El webhook solo exige `submission_id`. Si faltan otros campos, se guarda igual y queda `INCOMPLETA`.

**Datos**
- Los montos aceptan dígitos (`8200000`), punto de miles (`$6.400.000`, `1.200.000,50`), coma de
  miles (`1,200,000`, `1,200,000.50`) y millones abreviados (`1.2M`, `2 millones`). Un separador
  seguido de 3 dígitos es de miles; seguido de 1 o 2, es decimal. Los abreviados quedan con
  advertencia. Lo que no encaja en ningún formato queda vacío con advertencia; no se adivina.
- **Vacío no es cero.** Ahorro `0` es un rechazo (Jorge Camargo); ahorro vacío es `INCOMPLETA`
  (Sandra Vargas).
- `ingresos_mensuales` y `monthly_income` son ingreso mensual, igual que el umbral de `programas.json`.
- Fechas aceptadas: ISO con zona (webhook), `aaaa-mm-dd` y `dd/mm/aaaa` (CSV). Es día/mes porque
  existe `15/09/2026`. Las fechas sin hora se toman a las 00:00 de Bogotá y todo se guarda en UTC.
- Una **fecha futura** (Felipe Santamaría, `2027-01-15`) no se rechaza: la fecha no es una de las
  reglas. Se evalúa normal y queda con advertencia para que el equipo la verifique.
- Las ciudades se comparan sin tildes ni mayúsculas, en los dos lados: `Bogota` del CSV coincide
  con `Bogotá` de `programas.json` (caso Andrés Lozano).

**Reglas y decisiones**
- "Mínimo" es `>=`: quien tiene exactamente el umbral cumple.
- Una aplicación queda `INCOMPLETA` si falta ingreso, ahorro o ciudad, o si su programa no existe.
  No es un rechazo de negocio.
- `programas.json` se lee en cada corrida: cambiar un umbral o agregar un programa no requiere
  código ni reinicio. Si el archivo está mal, la corrida falla completa y las aplicaciones siguen
  `PENDIENTE` hasta que se corrija; no se evalúa con reglas a medias.
- Una decisión no cambia si después cambian las reglas. Cada aplicación guarda en
  `umbrales_aplicados` la copia de las reglas con que se decidió.
- El webhook no decide: guarda y responde. La evaluación la hace solo el proceso, así hay un único
  lugar donde se decide y se notifica. El costo es que la respuesta tarda hasta un intervalo.

**Notificaciones** (confirmado con la empresa)
- Una notificación **por aplicación**, no por persona. Si Juan aplica dos veces recibe dos, y
  correr el proceso diez veces sigue dejando dos.
- Las aplicaciones `INCOMPLETA` también se notifican, con su propio tipo.
- Sin email, la notificación se registra como `SIN_DESTINATARIO` para que el equipo comercial
  contacte a la persona por otro medio.

## Qué dejé por fuera

### Limitar el ingreso de datos en el origen

El sistema acepta los datos "como vienen": el `Normalizador` interpreta formatos distintos
(`$6.400.000`, `1,200,000`, `1.2M`, `12/09/2026`, `Bogota` sin tilde) y marca con una
advertencia o deja `INCOMPLETA` lo que no puede leer con certeza. Eso es una defensa, no
una solución: cada interpretación es una suposición y cada aplicación incompleta es trabajo
manual para el equipo comercial.

Se recomienda que el ingreso de datos sea más limitado en el formulario o fuente que los
captura, ya sea mostrando un ejemplo del formato esperado o con opciones predeterminadas:

| Campo | Hoy llega | Recomendación |
|---|---|---|
| Programa | texto libre | Selector con los programas de `programas.json` |
| Ciudad | texto libre (`Bogotá`, `Bogota`) | Lista desplegable con las ciudades del programa elegido |
| Ingreso y ahorro | `8200000`, `$6.400.000`, vacío | Campo numérico con máscara de pesos y un ejemplo visible (`$ 6.400.000`); obligatorio, con `0` como valor explícito |
| Fecha de solicitud | `2026-09-01`, `12/09/2026`, fechas futuras | Selector de fecha que no permita fechas futuras, o que la registre el sistema al enviar |
| Celular | `3118887766`, `+57 311 888 7766` | Campo de 10 dígitos con indicativo fijo y ejemplo (`311 888 7766`) |
| Email | mayúsculas y minúsculas mezcladas | Validación de formato al escribir |

Para la fuente A (CSV), lo equivalente es acordar con quien lo genera una plantilla con
formatos fijos: fecha `aaaa-mm-dd`, montos solo con dígitos, ciudad y programa tomados de
una lista cerrada.

Con esto el `Normalizador` se mantiene como red de seguridad para lo que aún llegue mal,
pero dejaría de ser el camino normal.

### Otros

- **Envío real de notificaciones.** Hoy "notificar" es escribir en la tabla, que funciona como
  bandeja de salida. Un envío real sería un proceso aparte que lee esa tabla y marca cada fila
  `ENVIADA` o `FALLIDA`. Para eso habría que quitar `@Immutable` de `Notificacion`.
- **"Trabajar" las aplicaciones.** El equipo puede verlas y filtrarlas, pero no asignarlas, agregar
  notas ni completar una `INCOMPLETA`. Completarla sería guardar el dato faltante, devolverla a
  `PENDIENTE` y dejar que el proceso la evalúe; la llave `(aplicacion_id, tipo)` ya permite que
  reciba después su notificación de `DECISION`.
- **Seguridad.** No hay autenticación en la pantalla ni en `/procesos/ejecutar`, y el webhook no
  valida firma (lo normal sería HMAC con un secreto compartido).
- **Búsqueda en la pantalla.** Se filtra por programa y resultado, como pide el enunciado; no hay
  búsqueda por nombre o email ni orden por columna.
- **Reglas nuevas.** Los umbrales, ciudades y programas se configuran sin código; un tipo de regla
  nuevo (por ejemplo, edad máxima) sí requiere código en `MotorReglas`.
- **Alias de ciudades.** `Bogotá D.C.` no coincidiría con `Bogotá`. Se resolvería con un mapa de
  alias en `programas.json`.
- **Reprocesar.** El registro original queda en `payload_crudo`, pero no hay una herramienta para
  volver a normalizarlo si se corrige un bug.
- **Monitoreo.** Las corridas quedan en `ejecuciones_proceso` y los errores en el log, pero nada
  avisa si una corrida falla.
- **Testcontainers.** Las pruebas dependen del Postgres de `compose.yml`; con Testcontainers cada
  corrida de pruebas levantaría su propia base.

## Qué me preocupa de mi solución

- **El id de las filas del CSV es un hash de su contenido.** Si alguien corrige una fila (por
  ejemplo, completa el ahorro de Sandra), entra como una aplicación nueva y la anterior queda
  incompleta. Al revés, dos filas idénticas legítimas se tomarían como una sola. Lo ideal es que
  la fuente A traiga su propio id.
- **Identidad solo por email.** Dos personas que comparten email quedarían fusionadas, y alguien
  que cambia de email quedaría duplicado. Con un documento de identidad esto se resolvería.
- **Los datos de contacto toman lo último que llega, no lo más reciente según la fecha de solicitud.**
  Si llega tarde una aplicación vieja, pisa un celular más nuevo.
- **Notificaciones repetidas.** Juan aplicó dos veces al mismo programa con el mismo resultado y
  recibe dos mensajes casi iguales. La alternativa sería la llave `(persona, programa, decisión)`.
- **Una aplicación que falla siempre se reintenta en cada corrida, sin límite.** No hay contador de
  intentos ni estado de error visible: solo queda en el log.
- **Se evalúan hasta 500 pendientes por corrida.** Un volumen grande tardaría varias corridas.
- **El CSV se relee completo en cada corrida.** Es simple y seguro, pero el costo crece con el
  tamaño del archivo; con archivos grandes convendría guardar la huella del archivo y saltarlo si
  no cambió.
- **Interpretaciones que pueden estar mal.** `1.2M` se lee como 1.200.000 y una fecha futura se
  aprueba igual. Quedan con advertencia, pero nadie está obligado a revisarlas.
- **`@Immutable` solo protege dentro de la aplicación.** Alguien con acceso directo a la base puede
  modificar una notificación.
- **El candado ocupa una conexión del pool durante toda la corrida.**
- **Credenciales de desarrollo por defecto** en `compose.yml` y `application.properties`. En un
  ambiente real vendrían de variables de entorno o de un gestor de secretos.
