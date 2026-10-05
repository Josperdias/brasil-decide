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
# rola a tela proporcionalmente ao tamanho real do aparelho
scroll() { s=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); w=${s%x*}; h=${s#*x}; adb shell input swipe $((w / 2)) $((h * 72 / 100)) $((w / 2)) $((h * 22 / 100)) 450; sleep 2; }
# confere que a tela mostra o texto esperado (uiautomator); fecha diálogos do sistema (ANR do launcher) e tenta 3 vezes
ui_has() {
  for i in 1 2 3 4; do
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    if adb shell cat /sdcard/ui.xml 2> /dev/null | grep -q "$1"; then echo "OK: a tela contem '$1'"; return 0; fi
    sleep 10
  done
  echo "FALHA: a tela nao contem '$1'"; adb shell cat /sdcard/ui.xml 2> /dev/null | grep -o 'text="[^"]*"' | head -20; fail=1; return 1
}
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
  [ "$tab" = brasil ] && ui_has "turno: domingo"
  [ "$tab" = mais ] && ui_has "Panorama político"
done
# aba Estado: abre no DF e também em outro estado (SP) — cartões e imagens de status do estado escolhido
adb shell am start -S -W -n $N/.MainActivity --es tab df --es luf SP > /dev/null; sleep 28; alive $N; shot native-df-sp; crashes
adb shell am start -S -W -n $N/.MainActivity --es tab df --es luf SP --ez genstatus true > /dev/null; sleep 45; alive $N
for t in 10 11 12 13 14; do adb exec-out run-as $N cat cache/share/test-$t-0.png > dist/status-sp-$t-0.png || true; done
# voto no exterior (aba Mapa > Mundo): 186 localidades; rola a tela para registrar as seções principais
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es msub mundo > /dev/null; sleep 70; alive $N; shot native-mundo-1; crashes; ui_has "VOTO DOS BRASILEIROS NO EXTERIOR"
for k in 2 3 4 5 6 7; do scroll; shot native-mundo-$k; done
# replay (série de DEMONSTRAÇÃO simulada, só para testar a tela): mapa a 55%, corrida a 40% e gráfico a 80%
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es msub replay --es rdir demo --ei rpct 55 > /dev/null; sleep 30; alive $N; shot native-replay-1; crashes; ui_has "MONSTRA"
scroll; shot native-replay-2
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es msub replay --es rdir demo --ei rmode 1 --ei rpct 40 > /dev/null; sleep 20; alive $N; shot native-replay-corrida
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es msub replay --es rdir demo --ei rmode 2 --ei rpct 80 > /dev/null; sleep 20; alive $N; shot native-replay-grafico
scroll; scroll; scroll; shot native-replay-eventos
# 2º turno e modo TV
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --ei turn 2 > /dev/null; sleep 20; alive $N; shot native-turno2
adb shell am start -S -W -n $N/.MainActivity --ez tv true > /dev/null; sleep 20; alive $N; shot native-tv
# ficha do candidato (registro TSE, mandato, manchetes, checagens)
adb shell am start -S -W -n $N/.MainActivity --es tab brasil --es ficha 0 > /dev/null; sleep 50; alive $N; shot native-ficha; crashes; ui_has "REGISTRO NO TSE"
# painel de UF e estúdio de imagens
adb shell am start -S -W -n $N/.MainActivity --es tab mapa --es sel SP --es sheet SP > /dev/null; sleep 22; alive $N; shot native-sheet-sp
adb shell am start -S -W -n $N/.MainActivity --es tab brasil --ez studio true --ez genstatus true > /dev/null; sleep 45; alive $N; shot native-studio
# imagens de status geradas pelo app (6 variantes) — puxadas do cache do app (build debug)
for t in 0 1 2 10 11 12 13 14; do for f in 0 1; do adb exec-out run-as $N cat cache/share/test-$t-$f.png > dist/status-$t-$f.png || true; done; done
# relatórios da Central de Análises (6 formatos), gerados pelo app com os dados reais e validados depois pelo CI
adb shell am start -S -W -n $N/.MainActivity --es tab mais --ez genreport true > /dev/null; sleep 55; alive $N; shot native-mais-analises; crashes
for e in pdf docx xlsx csv json png; do adb exec-out run-as $N cat cache/share/test-report.$e > dist/report.$e || true; done
ls -la dist/report.* || true
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
