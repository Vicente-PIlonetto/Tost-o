# Tostão — desenvolvimento

App Android de finanças pessoais, sem login e sem servidor: tudo fica no banco local do celular.

- Kotlin + Jetpack Compose (Material 3), MVVM simples com um `AppContainer` para as dependências
- Room para os dados, com migrações escritas à mão em `data/db/Migrations.kt` e esquemas exportados em `app/schemas/`
- DataStore para as configurações, WorkManager e alarmes exatos para os avisos
- minSdk 26, targetSdk 36, interface em português do Brasil
- O pacote ainda é `app.meubolso`, nome anterior do app, para quem já usava não perder os dados

## Estrutura

| Pasta | O que tem |
| --- | --- |
| `core/` | Dinheiro em centavos (`Money`) e datas (`Dates`). |
| `domain/` | Regras sem Android: resumo do mês, recorrência de contas, ciclo de fatura e de vale (`Wallets.kt`), sugestões da lista de compras. Tudo coberto por testes. |
| `data/` | Entidades, DAOs, migrações, repositórios e os dados de exemplo (`SampleData.kt`). |
| `ui/` | Telas em Compose, uma pasta por aba. |
| `notify/` | Lembretes de contas, confirmação de entradas e lembrete diário. |

## Compilar

Requisitos: JDK 17 ou 21 e Android SDK com platform 36. Em `local.properties`, na raiz (o arquivo não vai para o Git):

```properties
sdk.dir=/caminho/para/android-sdk
```

```bash
./gradlew assembleRelease        # APK otimizado (R8), para usar no celular
./gradlew assembleDebug          # APK de debug (pacote .debug, instala ao lado do release)
./gradlew assembleDemo           # versão de demonstração (pacote .demo), para prints e vídeos
./gradlew testDebugUnitTest      # testes unitários
python3 scripts/check_migrations.py   # confere as migrações do banco sem emulador
```

O release é assinado com a chave de debug, para instalar direto pelo arquivo.

A versão **demo** instala ao lado da normal e, ao aceitar os dados de exemplo, também cria um cartão de crédito e um vale-refeição com gastos espalhados entre as carteiras. A versão normal só traz conta e dinheiro, porque as carteiras de exemplo não seriam apagadas junto com os dados de exemplo.

## Banco de dados

Toda mudança no banco precisa de:

1. Subir a `version` em `AppDatabase.kt`.
2. Uma migração nova em `Migrations.kt`, adicionada a `Migrations.ALL`. O app nunca usa migração destrutiva: os dados do usuário não podem ser perdidos.
3. Compilar uma vez para o Room exportar o novo JSON em `app/schemas/`.
4. Rodar `python3 scripts/check_migrations.py`. Ele aplica todas as migrações a partir da versão 1 e compara o resultado com o esquema exportado.

## Instalar no celular

**Via cabo (adb)**
1. No celular, ative *Opções do desenvolvedor* (toque 7 vezes em *Sobre o telefone > Número da versão*) e depois *Depuração USB*.
2. Conecte o cabo e aceite a chave no celular.
3. `adb install -r app/build/outputs/apk/release/app-release.apk`

**Copiando o arquivo**
1. Copie o APK para o celular (cabo, Drive, Taildrop etc.) e abra pelo app *Arquivos*.
2. Quando o Android pedir, permita **instalar apps desconhecidos** para o app usado para abrir o APK.
