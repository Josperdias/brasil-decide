# Central Eleições 2026

- `html/part-*.html` — HTML final dividido em partes (concatene em `central-eleicoes-2026.html`; o CI faz isso). HTML único (base antiga completa + aba **Mapa** SVG real + **2º turno** + notícias multi-fonte).
- `android/` — projeto Gradle com dois apps:
  - `webview-app` — fiel ao HTML final (empacotado em `assets/index.html`), com ponte HTTP nativa (evita CORS).
  - `native-app` — interface Android nativa, sem WebView, abre offline com o último snapshot.
- `.github/workflows/eleicoes-android.yml` — compila os dois APKs com Gradle/AGP oficiais, verifica assinatura com `apksigner` e abre os dois em emulador Android 15 (API 35).

Compilar localmente (JDK 17 + Android SDK 35 + Gradle 8.14): `cat html/part-*.html > central-eleicoes-2026.html && python3 android/gen_mapdata.py central-eleicoes-2026.html android/native-app/src/main/java/br/com/centraleleicoes/nativeapp/MapData.java && cd android && gradle assembleDebug`
APKs: `webview-app/build/outputs/apk/debug/` e `native-app/build/outputs/apk/debug/`.

Mapa: geometria de `@svg-maps/brazil` (CC BY 4.0).

## Divulgação / download

- **Link direto do APK** (sempre a última versão testada): https://github.com/Josperdias/concursos-df/releases/download/app-latest/CentralEleicoes2026.apk
- **Página da release** (para compartilhar): https://github.com/Josperdias/concursos-df/releases/latest
- O site (`central-eleicoes-2026.html`) mostra um banner "Baixar app" (escondido dentro do próprio app).
- A página de divulgação `docs/app/index.html` fica em `https://<usuario>.github.io/concursos-df/app/` depois de mesclada na `main` (o GitHub Pages serve a pasta `docs`).
- O CI publica/atualiza a release `app-latest` a cada build aprovado no emulador; o app consulta essa release e avisa quando há versão nova.
- **Assinatura:** a chave antiga (publicada no repositório até o build 15) está **comprometida** e não é mais usada. Release só é assinada com a chave privada guardada em GitHub Actions Secrets (`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`); sem eles o CI gera só build debug e **não publica release**. Veja `SIGNING.md`.
- **Atualização:** o app nativo baixa e instala a nova versão com um toque (permissão “instalar apps desconhecidos” na 1ª vez). O app WebView baixa sozinho o HTML novo (`central-eleicoes-2026.html` da release) ao abrir. Mudanças só no HTML não exigem novo APK no WebView; mudanças de código Java exigem APK novo (limitação do Android).
- Instalações até o build 15 (chave comprometida) precisam ser desinstaladas uma vez antes da primeira instalação assinada pela nova chave.
