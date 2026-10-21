# Publicar Ink Notes en Play Store

Lista de pasos en orden. Textos de la ficha: [listing.md](listing.md). Política de privacidad: [privacy-policy.md](privacy-policy.md).

## 1. Clave de subida (una sola vez)

Crea la clave en la raíz del proyecto. `keytool` te pide una contraseña: elígela tú y guárdala en tu gestor de contraseñas. Si pierdes la clave, Google puede restablecerla, pero tarda días.

```bash
"/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" -genkeypair -v -keystore ink-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Luego copia `keystore.properties.example` a `keystore.properties` y pon tu contraseña. Los dos archivos están en `.gitignore`. Haz una copia de `ink-upload.jks` fuera del proyecto (por ejemplo, en tu gestor de contraseñas).

## 2. Generar el bundle

Antes de cada subida, sube `versionCode` en `app/build.gradle.kts` (1, 2, 3…) y, si quieres, `versionName`.

```bash
./gradlew bundleRelease
```

Resultado: `app/build/outputs/bundle/release/app-release.aab`. Sin `keystore.properties` el bundle queda firmado con la clave de depuración y Play lo rechaza.

## 3. Crear la app en Play Console

Crear app → nombre "Ink Notes", idioma predeterminado, App, Gratis. Acepta las declaraciones.

Play App Signing queda activado por defecto: Google guarda la clave de firma final y tú solo subes con la clave de subida.

## 4. Contenido de la app (Política → Contenido de la app)

| Sección | Respuesta |
| --- | --- |
| Política de privacidad | URL pública de `privacy-policy.md` |
| Acceso a la app | Toda la funcionalidad está disponible sin acceso especial |
| Anuncios | No contiene anuncios |
| Clasificación de contenido | Categoría "Utilidad, productividad, comunicación u otro"; todas las respuestas "No" |
| Público objetivo | 13 años o más (evita los requisitos de apps para niños) |
| Apps de noticias, salud, finanzas, gobierno | No |
| Seguridad de los datos | Ver abajo |

### Seguridad de los datos

Respuesta conservadora, porque las notas salen del dispositivo cuando el usuario activa la sincronización (aunque van a su propio Drive):

- ¿Recopila o comparte datos? **Sí**
- ¿Datos cifrados en tránsito? **Sí**
- ¿El usuario puede pedir que se borren? **Sí** (desactivar sincronización y borrar los datos ocultos en Drive; desinstalar)
- Tipos de datos:
  - **Información personal → Dirección de correo**: recopilado, no compartido, opcional, finalidad "Funcionalidad de la app" y "Administración de la cuenta"
  - **Mensajes / contenido del usuario → Otro contenido generado por el usuario** (las notas): recopilado, no compartido, opcional, finalidad "Funcionalidad de la app"
- No se procesan de forma efímera.

## 5. Ficha de Play Store

Textos, categoría y gráficos en [listing.md](listing.md). Necesitas icono de 512 × 512, gráfico de 1024 × 500 y al menos 2 capturas.

## 6. Prueba cerrada (cuentas personales nuevas)

Las cuentas personales de desarrollador creadas después del 13 de noviembre de 2023 deben hacer una prueba cerrada con **al menos 12 testers que la tengan instalada durante 14 días seguidos** antes de poder pedir acceso a producción. Las cuentas de organización no tienen este requisito.

1. Prueba → Prueba cerrada → crear pista, subir el `.aab`.
2. Agrega los testers por lista de correos (cuentas de Google) o por un Grupo de Google.
3. Comparte el enlace de inscripción; cada tester acepta y la instala desde Play.
4. Después de 14 días, Panel → Solicitar acceso a producción.

Para probar tú solo antes de eso, usa la **prueba interna** (hasta 100 testers, disponible en minutos, sin revisión larga).

## 7. Sincronización con Drive en la versión de Play

La versión de Play la firma Google, así que necesita su propio cliente OAuth.

1. Play Console → Configuración → Integridad de la app → Firma de apps → copia el **SHA-1 del certificado de la clave de firma de apps**.
2. Google Cloud Console (proyecto de Ink Notes) → Google Auth Platform → Clientes → Crear cliente → Android, paquete `app.inknotes`, ese SHA-1.
3. Google Auth Platform → Público → **Publicar app**. Mientras esté en modo de prueba, solo las cuentas de usuarios de prueba pueden sincronizar, y su autorización caduca a los 7 días.
4. En Desarrollo de marca, pon la página principal y la URL de la política de privacidad. `drive.appdata` no es un permiso restringido; si Google pide verificación de marca, necesitará que esas URLs estén en un dominio que verifiques (por ejemplo, inknotes.app).

## 8. Cada actualización

1. Sube `versionCode`.
2. `./gradlew bundleRelease`
3. Sube el `.aab` a la pista que corresponda y escribe las novedades.
