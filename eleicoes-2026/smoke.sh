#!/usr/bin/env bash
# Teste de fumaça no emulador (Android 15): instala e abre os dois apps, confere que seguem vivos,
# percorre as abas do app nativo (via extras de intent) e tira prints.
set -u
fail=0
N=br.com.centraleleicoes.nativeapp
W=br.com.centraleleicoes.webview
# testes principais com os APKs de debug (permitem extrair as imagens geradas via run-as); no fim, o de release é instalado por cima
adb install -r dist/test-WebView-debug.apk || fail=1
adb install -r dist/test-Nativo-debug.apk || fail=1

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
# aba Estado: abre no DF e também em outro estado (SP) — cartões e imagens de status do estado escolhido
adb shell am start -S -W -n $N/.MainActivity --es tab df --es luf SP > /dev/null; sleep 28; alive $N; shot native-df-sp; crashes
adb shell am start -S -W -n $N/.MainActivity --es tab df --es luf SP --ez genstatus true > /dev/null; sleep 45; alive $N
for t in 10 11 12 13 14; do adb exec-out run-as $N cat cache/share/test-$t-0.png > dist/status-sp-$t-0.png || true; done
# 2º turno e modo TV
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --ei turn 2 > /dev/null; sleep 20; alive $N; shot native-turno2
adb shell am start -S -W -n $N/.MainActivity --ez tv true > /dev/null; sleep 20; alive $N; shot native-tv
# ficha do candidato (registro TSE, mandato, manchetes, checagens)
adb shell am start -S -W -n $N/.MainActivity --es tab brasil --es ficha 0 > /dev/null; sleep 50; alive $N; shot native-ficha; crashes
# painel de UF e estúdio de imagens
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es sel SP --es sheet SP > /dev/null; sleep 22; alive $N; shot native-sheet-sp
adb shell am start -S -W -n $N/.MainActivity --es tab brasil --ez studio true --ez genstatus true > /dev/null; sleep 45; alive $N; shot native-studio
# imagens de status geradas pelo app (6 variantes) — puxadas do cache do app (build debug)
for t in 0 1 2 10 11 12 13 14; do for f in 0 1; do adb exec-out run-as $N cat cache/share/test-$t-$f.png > dist/status-$t-$f.png || true; done; done
# --- Minijogo escondido
adb logcat -c
adb shell am start -S -W -n $N/.GameActivity > /dev/null; sleep 3; shot game-1-pronto
W_=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); X=$(( ${W_%x*} / 2 )); Y=$(( ${W_#*x} / 2 ))
adb shell input tap $X $Y; sleep 1
for i in 1 2 3 4 5 6; do adb shell input swipe $((X - 300)) $((Y + 600)) $((X + 300)) $((Y + 600)) 300; done
sleep 4; shot game-2-jogando; alive $N; crashes
# --- APKs de RELEASE (só existem quando o CI tem os Secrets de assinatura): chave diferente da debug, então desinstala antes
if [ -f dist/CentralEleicoes2026-Nativo.apk ] && [ -f dist/CentralEleicoes2026-WebView.apk ]; then
  adb uninstall $N > /dev/null; adb uninstall $W > /dev/null
  for apk in CentralEleicoes2026-WebView CentralEleicoes2026-Nativo; do adb install -r dist/$apk.apk || { echo "FALHA ao instalar $apk"; fail=1; }; done
  adb logcat -c
  adb shell am start -S -W -n $N/.MainActivity > /dev/null; sleep 20; alive $N; shot release-nativo; crashes
  adb shell am start -S -W -n $W/.MainActivity > /dev/null; sleep 20; alive $W; shot release-webview; crashes
else
  echo "Sem APKs de release neste build (Secrets de assinatura ausentes): teste feito só com os APKs de debug."
fi
exit $fail
