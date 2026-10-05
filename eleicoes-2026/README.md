# Central Eleições 2026

- `html/part-*.html` — HTML final dividido em partes (concatene em `central-eleicoes-2026.html`; o CI faz isso). HTML único (base antiga completa + aba **Mapa** SVG real + **2º turno** + notícias multi-fonte).
- `android/` — projeto Gradle com dois apps:
  - `webview-app` — fiel ao HTML final (empacotado em `assets/index.html`), com ponte HTTP nativa (evita CORS).
  - `native-app` — interface Android nativa, sem WebView, abre offline com o último snapshot.
- `.github/workflows/eleicoes-android.yml` — compila os dois APKs com Gradle/AGP oficiais, verifica assinatura com `apksigner` e abre os dois em emulador Android 15 (API 35).

Compilar localmente (JDK 17 + Android SDK 35 + Gradle 8.14): `cat html/part-*.html > central-eleicoes-2026.html && python3 android/gen_mapdata.py central-eleicoes-2026.html android/native-app/src/main/java/br/com/centraleleicoes/nativeapp/MapData.java && cd android && gradle assembleDebug`
APKs: `webview-app/build/outputs/apk/debug/` e `native-app/build/outputs/apk/debug/`.

Mapa: geometria de `@svg-maps/brazil` (CC BY 4.0).
