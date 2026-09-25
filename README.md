# CRMToperty

> Borrador: se completa al final del desarrollo.

## Qué asumí

_(pendiente)_

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

## Qué me preocupa de mi solución

_(pendiente)_
