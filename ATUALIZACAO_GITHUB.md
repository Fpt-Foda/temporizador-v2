# Atualização automática — Temporizador V2

O aplicativo consulta a última **Release** deste repositório ao abrir. Se encontrar uma versão mais nova, ele mostra um botão para baixar. O Android sempre pede confirmação antes de instalar.

## Preparar o repositório no GitHub

1. Crie um repositório novo no GitHub, por exemplo `temporizador-v2`.
2. No arquivo `app/src/main/res/values/strings.xml`, troque `COLOQUE_SEU_USUARIO/temporizador-v2` por `seu-usuario/temporizador-v2`.
3. Envie todo este projeto V2 para esse repositório.

## Criar e guardar a chave de assinatura

No Android Studio, abra este projeto e use **Build > Generate Signed Bundle / APK > APK > Create new**. Guarde em local seguro o arquivo `.jks`, a senha do arquivo, o apelido da chave e a senha da chave. Use sempre esta mesma chave: sem ela, o Android não aceita uma atualização sobre a versão já instalada.

## Salvar as chaves no GitHub

No repositório, abra **Settings > Secrets and variables > Actions > New repository secret** e crie estes quatro segredos:

| Nome | Conteúdo |
| --- | --- |
| `KEYSTORE_BASE64` | arquivo `.jks` convertido para Base64 |
| `KEYSTORE_PASSWORD` | senha do arquivo `.jks` |
| `KEY_ALIAS` | apelido da chave |
| `KEY_PASSWORD` | senha da chave |

No PowerShell, para copiar o conteúdo Base64 do arquivo `.jks`, use:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\caminho\temporizador-v2.jks')) | Set-Clipboard
```

Cole o resultado em `KEYSTORE_BASE64`.

## Publicar uma versão

No GitHub, abra a aba **Actions**, escolha **Gerar APK de atualização**, clique em **Run workflow** e informe uma versão maior, por exemplo `v1.0`. A automação gera um APK assinado e cria uma Release.

Instale esta primeira versão pelo APK da Release. Depois, sempre que você enviar código novo, volte em **Actions**, execute o fluxo com uma versão maior (`v1.1`, depois `v1.2`...) e instale a atualização pelo próprio app, sem USB.
