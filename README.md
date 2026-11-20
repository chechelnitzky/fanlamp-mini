# FanLamp Mini

Aplicación Android experimental para controlar ventiladores compatibles con FanLamp Pro por Bluetooth LE Advertising, sin ESP32 ni Google Play.

La primera APK permite enviar órdenes manuales a luz y ventilador (velocidades 1–6) y diagnosticar el protocolo. Los horarios automáticos se incorporarán después de comprobar el control real.

**Advertencia:** el envío de órdenes Bluetooth no confirma que el ventilador las haya recibido. El emparejamiento puede afectar controles existentes. No es la app oficial FanLamp Pro.

El código está basado en los protocolos de [NicoIIT/ha-ble-adv](https://github.com/NicoIIT/ha-ble-adv) bajo licencia MIT.

Cuando GitHub Actions termine exitosamente, instala la APK desde [Releases](https://github.com/chechelnitzky/fanlamp-mini/releases).
