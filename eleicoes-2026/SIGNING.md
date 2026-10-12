# Correção de segurança — assinatura Android

A chave usada até o build 15 foi publicada no repositório junto com a senha. Ela deve ser considerada comprometida.

## O que fazer no repositório
1. Remover `eleicoes-2026/android/keystore/central-eleicoes.jks` da árvore atual.
2. Adicionar ao `.gitignore`: `keystore/`, `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `signing.properties`.
3. Remover senhas e caminho fixo dos dois `build.gradle`.
4. Fazer release somente com GitHub Actions Secrets.
5. Debug deve usar a chave debug padrão do Gradle, nunca a chave de release.

## Secrets
Criar em Settings > Secrets and variables > Actions:
- ANDROID_KEYSTORE_B64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS

O workflow deve reconstruir o PKCS#12 em `$RUNNER_TEMP`, com chmod 600, exportar ANDROID_KEYSTORE_PATH/PASSWORD/ALIAS e só então executar assembleRelease.

## Rotação
A chave antiga não volta a ser segura só porque foi apagada do Git. APKs até o build 15 precisam ser desinstalados uma vez antes da primeira instalação assinada pela nova chave. Depois disso, as atualizações com a nova chave funcionam normalmente.

## Importante
Não publicar a nova chave, senha ou Base64 em commit, Issue, log, release asset ou pasta pública.
