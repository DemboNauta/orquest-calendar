# Mis Turnos → Google Calendar

App Android que lee los turnos de la pantalla **"Mis turnos"** de Orquest a partir de una
**captura de pantalla** (OCR on-device), calcula las **horas semanales** y sincroniza los turnos
con **Google Calendar** — sin necesidad de la API de Orquest ni de OAuth de Google.

## Cómo funciona

1. Haces una o varias capturas de la pantalla *Mis turnos* de Orquest.
2. En la app pulsas **Importar capturas** y eliges las imágenes.
3. La app usa **ML Kit** (reconocimiento de texto, funciona sin internet) para leer cada tarjeta.
4. Un **parser** convierte el texto en turnos estructurados (fecha, tramos, horas) y suma las
   horas por día y por semana.
5. Pulsas **Sincronizar con Google Calendar**: los turnos se escriben en el calendario del
   dispositivo (`CalendarContract`). Como tu cuenta de Google ya está sincronizada en Android,
   aparecen en Google Calendar automáticamente.

Los turnos partidos (`13:30 - 17:00 / 19:30 - 23:00`), los **días libres** y los días **sin
asignaciones** se reconocen y se clasifican. La sincronización es **idempotente**: cada tramo
lleva una clave estable, así que reimportar la misma semana actualiza los eventos en vez de
duplicarlos.

## Estructura

```
app/src/main/java/com/misturnos/
├── model/Shift.kt           # Modelo de datos (ShiftSegment, DayShift, WeekSchedule)
├── parser/ShiftParser.kt    # Cerebro: OCR -> turnos (con tests)
├── parser/SpanishDates.kt   # Meses/días en español
├── ocr/OcrService.kt        # ML Kit text recognition
├── calendar/CalendarSync.kt # Escritura en el calendario del dispositivo
├── ui/                      # Compose (MainScreen + MainViewModel)
└── MainActivity.kt
app/src/test/java/...         # Tests unitarios del parser (datos reales de Orquest)
.github/workflows/            # CI: tests + build + release del APK
```

## Compilar / descargar el APK

### Opción A — Descargar desde GitHub Releases (recomendado)

Cada vez que se lanza el workflow se genera un **APK instalable** y se publica como *Release*:

- Automático al crear un tag `v*` (p. ej. `git tag v1.0 && git push origin v1.0`).
- Manual desde la pestaña **Actions → Build & Release APK → Run workflow**.

Descarga `MisTurnos-*.apk` desde la release, ábrelo en el móvil y permite la instalación de
orígenes desconocidos si te lo pide.

> El APK que se publica es un *build de depuración*, firmado con la clave de debug, por lo que es
> instalable directamente. Para distribución en Play Store habría que firmarlo con una clave
> propia (vía secrets de GitHub).

### Opción B — Compilar en local

Requiere Android Studio o el SDK de Android.

```bash
./gradlew test           # ejecuta los tests del parser
./gradlew assembleDebug  # genera app/build/outputs/apk/debug/app-debug.apk
```

## Permisos

- **Lectura/Escritura de calendario**: para crear los eventos de los turnos.
- El OCR es **on-device**: las capturas no salen del teléfono.

## Notas

- Pensado para acceder a **tus propios datos** de turnos para uso personal.
- Si Orquest cambia mucho el diseño de la pantalla, puede que haya que ajustar el parser; los
  tests en `ShiftParserTest.kt` documentan el formato esperado y facilitan adaptarlo.
