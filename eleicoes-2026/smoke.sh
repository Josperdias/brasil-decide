#!/usr/bin/env bash
# Teste de fumaça no emulador: instala e abre os dois apps, confere que o processo segue vivo e tira print.
set -u
fail=0
adb install -r dist/CentralEleicoes2026-WebView.apk || fail=1
adb install -r dist/CentralEleicoes2026-Nativo.apk || fail=1
for pkg in br.com.centraleleicoes.webview br.com.centraleleicoes.nativeapp; do
  adb logcat -c
  adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 > /dev/null
  sleep 25
  if adb shell pidof "$pkg" > /dev/null; then echo "OK: $pkg em execucao"; else echo "FALHA: $pkg nao esta rodando"; fail=1; fi
  adb exec-out screencap -p > "dist/shot-$pkg.png"
  adb logcat -d -b crash | head -40
done
exit $fail
