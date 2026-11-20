# FanLamp Mini v0.3.0

Aplicación Android para controlar un ventilador compatible con **FanLamp Pro** por BLE Advertising, sin ESP32 ni una conexión Bluetooth GATT.

## Cambio importante (20-11-2026)

Esta versión utiliza los dos perfiles reales **V3 y V1** extraídos de la captura HCI de FanLamp Pro del dispositivo del propietario. Se han recuperado y validado contra paquetes de ejemplo el ID de cada controlador, el índice, el *seed*, el prefijo y el contador. La aplicación ahora:

- Envía primero un anuncio V3 y luego un anuncio V1 para cada pulsación, con sus **identificadores distintos** tal como se observó en el HCI.
- Emplea la lista completa de 13 UUID de 16 bits (tipo AD `0x03`), 26 bytes de comando y advertising conectable; evita el formato distinto Service Data `0x16` que no coincidía con la app original.
- Corrige el contador de transmisión a 8 bits (0–255). El registro original incluye TX altos (V3 250/251 y V1 235/236).
- Controla luz ON/OFF y ventilador OFF / velocidades 1–6. La interfaz conserva el diagnóstico BLE y la configuración manual avanzada.
- Mantiene los perfiles capturados seleccionados por defecto, sin pedir que se vuelva a emparejar. La opción de ID nuevo permanece en el modo manual.

## Instalación

Descarga **FanLampMini-v0.3.0-debug.apk** de [Releases](https://github.com/chechelnitzky/fanlamp-mini/releases), instálala/actualízala y concede los permisos de Bluetooth. En la pantalla principal deja seleccionado **Control capturado**, acércate al ventilador y pulsa **Luz encender**.

La app no puede confirmar a distancia que el ventilador reaccionó: **éxito de publicidad Bluetooth ≠ ventilador funcionando**. Si no responde, obtén un nuevo *bugreport* con HCI para comparar los paquetes transmitidos con los de FanLamp Pro.

**Privacidad:** este repositorio es público y el código contiene las identidades de control BLE recuperadas de la captura. No contiene el número de serie del teléfono ni el informe Android completo. No publiques el informe ZIP original, que sí puede incluir datos personales.

**Licencia / atribución:** port del protocolo de [NicoIIT/ha-ble-adv](https://github.com/NicoIIT/ha-ble-adv) bajo MIT; consultar `THIRD_PARTY_NOTICES.md`. No es la app oficial FanLamp Pro.
