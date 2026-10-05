#!/usr/bin/env bash
# Teste de fumaça no emulador (Android 15): instala e abre os dois apps, confere que seguem vivos,
# percorre as abas do app nativo (via extras de intent) e tira prints.
set -u
fail=0
N=br.com.centraleleicoes.nativeapp
W=br.com.centraleleicoes.webview
adb install -r dist/CentralEleicoes2026-WebView.apk || fail=1
adb install -r dist/CentralEleicoes2026-Nativo.apk || fail=1

shot() { adb exec-out screencap -p > "dist/$1.png"; }
alive() { if adb shell pidof "$1" > /dev/null; then echo "OK: $1 em execucao"; else echo "FALHA: $1 nao esta rodando"; fail=1; fi; }
crashes() { adb logcat -d -b crash | head -40; }

# --- WebView
adb logcat -c
adb shell am start -S -W -n $W/.MainActivity > /dev/null
sleep 25; alive $W; shot webview-1
crashes

# --- Nativo: uma captura por aba
for tab in mapa brasil ufs df news lives mais; do
  adb logcat -c
  adb shell am start -S -W -n $N/.MainActivity --es tab $tab > /dev/null
  sleep $([ "$tab" = news ] || [ "$tab" = lives ] && echo 30 || echo 22)
  alive $N; shot native-$tab
  crashes
done
# 2º turno e modo TV
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --ei turn 2 > /dev/null; sleep 20; alive $N; shot native-turno2
adb shell am start -S -W -n $N/.MainActivity --ez tv true > /dev/null; sleep 20; alive $N; shot native-tv
# painel de UF e estúdio de imagens
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es sel SP --es sheet SP > /dev/null; sleep 22; alive $N; shot native-sheet-sp
adb shell am start -S -W -n $N/.MainActivity --es tab brasil --ez studio true --ez genstatus true > /dev/null; sleep 30; alive $N; shot native-studio
# imagens de status geradas pelo app (6 variantes) — puxadas do cache do app (build debug)
for t in 0 1 2; do for f in 0 1; do adb exec-out run-as $N cat cache/share/test-$t-$f.png > dist/status-$t-$f.png || true; done; done
# --- Minijogo escondido
adb logcat -c
adb shell am start -S -W -n $N/.GameActivity > /dev/null; sleep 3; shot game-1-pronto
W_=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); X=$(( ${W_%x*} / 2 )); Y=$(( ${W_#*x} / 2 ))
adb shell input tap $X $Y; sleep 1
for i in 1 2 3 4 5 6; do adb shell input swipe $((X - 300)) $((Y + 600)) $((X + 300)) $((Y + 600)) 300; done
sleep 4; shot game-2-jogando; alive $N; crashes
exit $fail
