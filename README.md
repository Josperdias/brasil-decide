# Brasil Decide 🤖

Um site que **se atualiza sozinho todo dia**, de graça, mostrando as novidades de concursos do Distrito Federal. Ele roda no GitHub (sem servidor, sem cartão, sem chave de API) e publica uma página que atualiza automaticamente.

## Como funciona
- Um agendamento do **GitHub Actions** roda todo dia de manhã.
- O script `scripts/coletar.py` busca notícias de concursos do DF no **Google Notícias (RSS)** e gera o arquivo `docs/data.json`.
- A página `docs/index.html` lê esse arquivo e mostra a lista, sempre atualizada.
- Tudo publicado grátis pelo **GitHub Pages**.

---

## Passo a passo (leva ~10 minutos)

### 1. Crie uma conta no GitHub
Acesse https://github.com e crie uma conta gratuita (se ainda não tiver).

### 2. Crie um repositório
- Clique em **New repository**.
- Nome: por exemplo `brasil-decide`.
- Marque **Public** (recomendado: deixa o GitHub Actions e o Pages ilimitados e grátis).
- Clique em **Create repository**.

### 3. Envie estes arquivos
Na página do repositório, clique em **Add file → Upload files** e **arraste tudo o que está nesta pasta**, mantendo a estrutura:
```
.github/workflows/atualizar.yml
scripts/coletar.py
docs/index.html
docs/data.json
README.md
```
Depois clique em **Commit changes**.

### 4. Dê permissão de escrita ao robô
- Vá em **Settings → Actions → General**.
- Em **Workflow permissions**, escolha **Read and write permissions** e salve.
(É isso que permite o robô salvar as atualizações sozinho.)

### 5. Ligue a página (GitHub Pages)
- Vá em **Settings → Pages**.
- Em **Source**, escolha **Deploy from a branch**.
- Em **Branch**, escolha **main** e a pasta **/docs**. Salve.
- Em alguns minutos aparece o endereço do seu site, algo como:
  `https://SEU-USUARIO.github.io/brasil-decide/`
  Guarde esse link — é o seu site que se atualiza sozinho.

### 6. Rode a primeira coleta
- Vá na aba **Actions**.
- Se aparecer um aviso pedindo para habilitar workflows, clique em **I understand... enable**.
- Abra **Atualizar concursos DF** → **Run workflow** → **Run workflow**.
- Em ~1 minuto ele coleta as novidades e atualiza a página.

Pronto! Daqui pra frente ele roda **sozinho todo dia por volta das 6h** (horário de Brasília). Você também pode clicar em **Run workflow** quando quiser forçar uma atualização.

---

## Personalizar
- **Mudar o que ele busca:** edite a lista `QUERIES` no topo de `scripts/coletar.py` (ex.: acrescente `"concurso Sedes DF"`).
- **Mudar o horário:** edite a linha `cron` em `.github/workflows/atualizar.yml` (o horário está em UTC; Brasília é UTC−3).

## (Opcional) Receber por e-mail
Dá para o robô te mandar um e-mail a cada atualização usando a action `dawidd6/action-send-mail` com uma **senha de app** do Gmail guardada em *Secrets*. Se quiser, peça que eu te passo esse trecho pronto — deixei de fora para manter a instalação simples.

## Aviso
As informações vêm de notícias e servem como radar. **Confirme sempre no edital oficial** do órgão antes de tomar qualquer decisão.
