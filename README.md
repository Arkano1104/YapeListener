# 💸 Yape Listener

App Android que escucha notificaciones de Yape y anuncia por voz:
**"¡Yape! De [nombre], [monto]"** + notificación emergente.

---

## ¿Qué hace?

- Escucha en segundo plano las notificaciones de la app Yape
- Cuando detecta un Yape recibido, habla en voz alta: *"¡Yape! De Juan, 50 soles"*
- Muestra una notificación emergente (heads-up) con el nombre y monto
- Se reactiva automáticamente al reiniciar el teléfono

---

## Cómo compilar e instalar

### Opción A — Android Studio (recomendado)

1. **Instala Android Studio** desde https://developer.android.com/studio
2. **Abre el proyecto**: File → Open → selecciona la carpeta `YapeListener`
3. Espera que Gradle sincronice (primera vez tarda ~2 min)
4. Conecta tu celular por USB con **Depuración USB activada**:
   - Ajustes → Acerca del teléfono → toca "Número de compilación" 7 veces
   - Ajustes → Opciones de desarrollador → Depuración USB: ON
5. Presiona ▶ **Run** (o Shift+F10)
6. La app se instalará automáticamente

### Opción B — Línea de comandos

```bash
# En la carpeta del proyecto:
./gradlew assembleDebug

# El APK quedará en:
app/build/outputs/apk/debug/app-debug.apk

# Instalar en el celular conectado por USB:
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Configuración en el celular (obligatorio)

1. Abre la app **Yape Listener**
2. Toca **"Dar permiso de notificaciones"**
3. En la pantalla del sistema, busca **"Yape Listener"** y actívala
4. Vuelve a la app — debe mostrar ✅ Activo

Eso es todo. La app ya está escuchando.

---

## Permisos que usa

| Permiso | Para qué |
|---|---|
| Acceso a notificaciones | Leer notificaciones de Yape (el usuario lo otorga manualmente) |
| Vibrar | Vibración al recibir Yape |
| Publicar notificaciones | Mostrar la alerta emergente |
| Recibir al iniciar | Que el servicio se reactive al reiniciar |

> La app **solo procesa** notificaciones cuyo paquete sea de Yape (`pe.com.bcp.innovacxion.yapeapp`). No lee nada más.

---

## Estructura del proyecto

```
YapeListener/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/yapelistener/
│   │   ├── MainActivity.kt              ← Pantalla principal + permisos
│   │   ├── YapeNotificationService.kt   ← Núcleo: escucha y habla
│   │   └── BootReceiver.kt             ← Reactiva al reiniciar
│   └── res/
│       ├── layout/activity_main.xml
│       ├── values/strings.xml
│       └── values/themes.xml
├── build.gradle
└── settings.gradle
```

---

## Requisitos

- Android 8.0 (API 26) o superior
- Android Studio Hedgehog (2023.1) o superior
- JDK 8+
