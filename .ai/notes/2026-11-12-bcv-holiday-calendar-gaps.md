# Calendario de feriados BCV incompleto — afectaciones

- **Fecha:** 2026-11-12
- **Estado:** pendiente, sin PR. Se documentó al cambiar la "fecha de la tasa" a la Fecha Valor (`fix/rate-date-uses-value-date`, V246).
- **Alcance:** módulo `exchangerate`: tabla `holidays`, `BusinessDayCalculator`, `ExchangeRateIngestionService`, `ExchangeRateWriter`.

## Contexto

BCV publica la tasa de lunes a viernes alrededor de las 4:00 pm (hora de Venezuela). La tasa entra en vigor a las 8:00 am del siguiente día hábil, su **Fecha Valor**. Si publica el viernes, la Fecha Valor es el lunes.

La API externa (`rates-api`) devuelve solo la Fecha Valor. La ingesta guarda:

| Columna | Origen | ¿Depende de `holidays`? |
|---|---|---|
| `valid_from` | Fecha Valor (de BCV) + 08:00 `America/Caracas` | **No** |
| `operation_date` | `previousBusinessDayBefore(Fecha Valor)`: día hábil anterior según nuestra tabla `holidays` | **Sí** |

La tabla `holidays` solo tiene 12 reglas: los feriados nacionales de fecha fija (01-01, 19-04, 01-05, 24-06, 05-07, 24-07, 12-10, 24-12, 25-12, 31-12) y Jueves y Viernes Santo **solo de 2026** (`recurrence_type = NONE`). Faltan:

- Carnaval (lunes y martes, fecha móvil).
- Semana Santa de 2027 en adelante (fecha móvil, hoy cargada solo para 2026).
- Feriados bancarios del calendario SUDEBAN que BCV respeta y que no son feriados nacionales.
- Días no laborables decretados sobre la marcha.

## Afectaciones

### 1. Feriado faltante → `operation_date` incorrecta (impacto bajo)

Ejemplo, Carnaval 2027 (lunes 08-02 y martes 09-02): BCV publica el viernes 05-02 con Fecha Valor del miércoles 10-02. Como el martes 09-02 no figura como feriado, se guarda `operation_date = 09-02` en vez de `05-02`.

- **Sí afecta:** la columna "Fecha operación" del admin de tasas (`/dashboard/settings/exchange-rates`) y cualquier reporte o filtro que use `operation_date`.
- **No afecta:** el valor de la tasa, `valid_from`, qué tasa rige en cada momento ni la "fecha de la tasa" que ven los usuarios. Desde V246 esa fecha es la Fecha Valor, que viene directo de BCV. Antes de V246 este error sí llegaba a la web y a los datos guardados.

### 2. Feriado de más (en nuestra tabla, pero BCV lo trabaja) → se pierde una tasa (impacto alto, poco probable)

`operation_date` es la clave de idempotencia de la ingesta: índice único `(base, quote, operation_date)` más el chequeo `existsBy...OperationDate` en `ExchangeRateWriter`. Si nuestra tabla marca como feriado un día que BCV trató como hábil, dos Fechas Valor distintas dan la misma `operation_date`:

- Fecha Valor D1 (el "feriado" de nuestra tabla): `operation_date` = día hábil anterior = X.
- Fecha Valor D2 (el siguiente día hábil): como D1 se considera inhábil, `operation_date` también da X.

La segunda tasa se descarta como `ALREADY_HAD_TODAY` y **la tasa de D1 sigue vigente en lugar de la de D2**, hasta la próxima publicación. Los pagos, consumos y comisiones de ese intervalo se calculan con la tasa anterior.

Hay que verificar contra el histórico de BCV si 24-12 y 31-12 son realmente días sin publicación.

### 3. Lo que NO afecta el calendario

- El job `FETCH_EXCHANGE_RATES` corre de lunes a viernes (07:00 y 16:00–21:00) aunque sea feriado. Si BCV no publicó, la API devuelve la misma Fecha Valor y la ejecución queda como `UP_TO_DATE`, sin consecuencias.
- La regla de vigencia (cuál tasa rige en un instante) solo usa `valid_from`.

## Opciones (por decidir)

1. **Sembrar los feriados faltantes**: Carnaval y Semana Santa como reglas calculadas (dependen de la Pascua; `Holiday` no tiene recurrencia "relativa a Pascua") y el calendario bancario SUDEBAN cada año. Exige mantenimiento manual anual.
2. **Dejar de derivar `operation_date` del calendario**: tomarla de la fecha real de publicación si `rates-api` la expone, o usar la fecha en que se obtuvo la tasa (`fetched_at` en hora de Caracas) cuando el job la captura el mismo día. Con eso `holidays` deja de intervenir en la ingesta.
3. **Mover la idempotencia a `(base, quote, valid_from)`**: elimina la afectación 2 sin importar el calendario. Requiere migrar el índice único.

La combinación 2 + 3 elimina la dependencia del calendario para las tasas. `holidays` seguiría disponible para otros usos.
