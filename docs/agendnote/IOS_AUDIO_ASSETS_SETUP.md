# Integrar los WAV de audio en el proyecto iOS — pasos manuales

Operación Aniversario, "Sprint Final: notificaciones iOS + audios", 2026-08-11.

## Por qué esto es manual

Este entorno no tiene Xcode/macOS - no puedo editar `iosApp/iosApp.xcodeproj/project.pbxproj` de
forma segura (es un archivo generado que Xcode normalmente gestiona; editarlo a mano sin poder
abrir el proyecto para verificar el resultado arriesga corromperlo). Tampoco hay ningún archivo
`.wav` en la raíz del repo todavía en el momento de escribir esto - los 8 archivos que mencionas
en tu directiva no están presentes, así que este documento es la preparación para cuando los
coloques, no una confirmación de que ya están integrados.

## 1. Coloca los 8 WAV en la raíz del repo (como dijiste que harías)

```
voice_reminder_general.wav
voice_reminder_now.wav
voice_morning.wav
voice_deadline.wav
voice_personal_message.wav
message_anniversary.wav
message_encouragement.wav
message_always.wav
```

No cambies estos nombres - el código ya los referencia exactamente así (ver
`composeApp/src/iosMain/kotlin/com/franciscor/agendnote/core/notifications/IosSoundAssets.kt`).

## 2. Arrástralos al proyecto de Xcode (con Xcode abierto)

1. Abre `iosApp/iosApp.xcodeproj` en Xcode.
2. En el Project Navigator, arrastra los 8 archivos `.wav` dentro del grupo `iosApp` (junto a
   `Assets.xcassets`, no dentro de él - son archivos sueltos, no un asset catalog).
3. En el diálogo que aparece al soltarlos:
   - Marca **"Copy items if needed"** (para que Xcode copie los archivos dentro de la carpeta del
     proyecto en vez de solo referenciar la ruta de la raíz del repo).
   - Marca **"Create groups"** (no "Create folder references").
   - En **"Add to targets"**, marca el target `iosApp` (el target de la app, no el de tests si
     existe uno separado).
4. Repite/confirma para los 8 archivos - puedes seleccionarlos todos a la vez en el Finder antes
   de arrastrarlos, Xcode los añade juntos.

## 3. Verifica que quedaron en "Copy Bundle Resources" (el paso que de verdad importa)

Esto es lo que la directiva pedía comprobar explícitamente ("no supongas que por existir en una
carpeta ya están dentro del bundle - VERIFÍCALO"):

1. Selecciona el proyecto `iosApp` en el navigator → target `iosApp` → pestaña **Build Phases**.
2. Expande **Copy Bundle Resources**.
3. Confirma que los 8 `.wav` aparecen en esa lista. Si arrastraste los archivos como en el paso 2
   con "Add to targets" marcado, deberían aparecer solos - si no aparecen, añádelos manualmente
   con el botón `+` de esa sección.

## 4. Verifica en runtime (una vez compiles)

El código (`IosSoundAssets.resolveNotificationSoundFilename`/`resolveVoiceMessagePath`) ya
comprueba en runtime con `NSBundle.mainBundle.pathForResource(name, "wav")` si cada archivo
existe de verdad en el bundle instalado, no solo si está en el proyecto Xcode - así que si algo
quedó mal integrado, la app no crashea: cae al sonido por defecto (notificaciones) o esconde el
reproductor (audio largo), y lo verás como un `println` de diagnóstico en la consola de Xcode
("no bundled sound for ...", "no bundled asset found for ..."). Si ves alguno de esos logs
después de seguir los pasos de arriba, vuelve al paso 3 - casi siempre es un archivo que no quedó
marcado para el target correcto.

## 5. Mayúsculas/minúsculas

iOS (a diferencia de macOS por defecto) usa un sistema de archivos **sensible a
mayúsculas/minúsculas** en el simulador y dispositivo real para el bundle de la app. Los nombres
en el código están todos en minúsculas exactas (`voice_reminder_general.wav`, etc.) - si el
archivo que colocas en la raíz tiene una mayúscula distinta en cualquier posición, no lo
encontrará y caerá al fallback (sin crash, pero tampoco con el sonido/audio correcto). Verifica
que el nombre del archivo que arrastras coincide carácter por carácter.

## Qué NO he tocado

No he modificado `project.pbxproj`, `Info.plist`, ni ningún archivo del proyecto Xcode - todo lo
de arriba son pasos manuales para ti. El código Kotlin (iosMain) que resuelve/reproduce estos
assets ya está escrito y compila (ver `docs/OPERATION_ANNIVERSARY_STATUS.md`), pero no se ha
podido ejecutar contra un bundle real todavía.
