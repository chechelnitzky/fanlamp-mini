# FanLamp Mini 0.5.0

Control Android local del ventilador y lámpara compatible con **FanLamp Pro** usando anuncios **Bluetooth Low Energy**. Basado en [NicoIIT/ha-ble-adv](https://github.com/NicoIIT/ha-ble-adv) (MIT), con los perfiles **V3 + V1** reconstruidos del HCI de FanLamp Pro del propietario.

## Cambios de la versión 0.5

- **Todo en una pantalla**: encabezado compacto, luz (encendido, dimmer, tres tonos), ventilador (encendido y velocidad 0–6 con dial adaptable) y acceso a horarios, sin desplazamiento vertical en la pantalla principal. Los ajustes avanzados siguen tras el engranaje.
- **Firma persistente**: la APK de entrega usa un certificado fijo. Las versiones anteriores 0.3 y 0.4 estaban firmadas con distintas claves de depuración generadas por los runners de GitHub, por lo que **NO es posible actualizar esas versiones directamente**: hay que desinstalar una vez para pasar a la nueva firma. A partir de 0.5, conservar la clave de firma permite instalar 0.6 y posteriores encima sin desinstalar.
- **Publicación segura**: GitHub Actions ya no publica una APK con firma debug aleatoria. Solo publica releases cuando existen los secretos `FANLAMP_KEYSTORE_B64` y `FANLAMP_KEYSTORE_PASSWORD`, y comprueba que el SHA-256 del certificado coincide con la identidad fija `F04FF09BB97592EE9D4DD92B6A869BF7040E50EB98B064A775C3CB037000F1D7`. Los secretos deben configurarse en *Settings → Secrets and variables → Actions*; el archivo de clave privada **nunca debe subirse al repositorio público**. La firma se conserva fuera del código fuente.

## Qué cambió

- **Interfaz diaria** de aspecto premium: paneles de luz y ventilador, dial de velocidad 0–6, botones rápidos y acceso a horarios.
- **Luz real por BLE**: ON/OFF; dimmer 1–100%; temperatura cálida, neutra o fría. Los comandos de canales frío/cálido `0x21` se verificaron con paquetes reales capturados: V3 `arg1=cold, arg2=warm`, V1 `arg0=cold, arg1=warm`.
- **Horarios semanales** editables: lunes a domingo, hora exacta, luz (encender/apagar, intensidad y tono), ventilador (encender/apagar, velocidad) o ambos.
- **Ajustes avanzados**: los campos técnicos, escaneo BLE, importación HEX y emparejamiento siguen en la pantalla accesible desde el engranaje, no en la pantalla principal.
- **Ejecución de horarios**: `AlarmManager` + `BroadcastReceiver` + servicio foreground `connectedDevice` para transmitir sin tener abierta la pantalla. Se reprograman al reiniciar o cambiar la hora/zona horaria. Si no hay permiso de horario exacto, usa alarma aproximada y advierte al usuario.

## Instalar y usar

1. Descarga el APK 0.5 **con firma estable** desde el enlace proporcionado por ChatGPT (o desde Releases cuando se hayan configurado los secretos de firma), no un APK debug de GitHub Actions(https://github.com/chechelnitzky/fanlamp-mini/releases).
2. Si Android indica conflicto de firma al actualizar la 0.3/0.4, **desinstala solo FanLamp Mini** e instala 0.5; los perfiles capturados vienen integrados. No desinstales FanLamp Pro.
3. Permite Bluetooth al abrir. En la portada pulsa Luz / Ventilador, ajusta brillo o temperatura. La app presenta **última orden transmitida**, no un estado real leído del ventilador.
4. En **Ver horarios**, crea una regla y selecciona días, hora y acciones. En Android 12+, si aparece la alerta, concede **Alarmas y recordatorios / horarios exactos** para evitar retrasos de Doze.
5. Si quieres ver el diagnóstico original, toca el engranaje.

## Advertencias importantes

- Un anuncio BLE **no confirma recepción**. Los controles básicos ON/OFF/velocidad ya se probaron físicamente con el ventilador; los nuevos niveles de luz y la ejecución automática deben probarse en el dispositivo.
- Los horarios son **locales al teléfono**: para ejecutarse, debe estar encendido, dentro de alcance Bluetooth, con Bluetooth activado y sin las restricciones de ejecución del fabricante que impidan el servicio. Si el móvil está apagado o lejos, el ventilador no recibirá comandos. No hay controlador en la nube ni ESP32.
- **Privacidad**: el repositorio público contiene únicamente el código y los identificadores de control BLE incluidos en el módulo `CapturedControl`. No contiene el ZIP completo del bugreport, números de serie ni direcciones MAC del teléfono. No publiques el bugreport.
- El código fuente se encuentra en `app/src/main/java/cl/fanlampmini`. Las pruebas unitarias del códec y los paquetes HCI se ejecutan en GitHub Actions.

## Lógica técnica

La app mantiene los perfiles cifrados V3 y V1 **con IDs distintos**, prefijo V3 capturado `20 82 00` y contadores de transmisión de 8 bits. Cada acción se manda en dos anuncios secuenciales; los horarios que incluyen luz y ventilador serializan todas las acciones. La pantalla avanzada conserva el modo manual para diagnóstico, pero la pantalla principal siempre utiliza los perfiles reales capturados.
