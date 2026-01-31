CodePad é um editor simples para textos e códigos feito em JavaFX. Ele suporta abas, modo Texto e modo Código, destaque de sintaxe, auto-complete com Ctrl+Space, snippets como psvm e sout, auto-fechamento de parênteses e aspas, busca e substituição e temas claro e escuro. Também permite executar arquivos Java, Python e JavaScript diretamente pelo botão Executar quando o modo Código está ativo.

Para usar, abra o aplicativo e crie ou abra arquivos. No modo Código, use Tab para expandir snippets, Ctrl+Space para sugestões e o botão Executar para rodar o arquivo atual. O executável portátil está em dist\CodePad e o arquivo compactado para release é dist\CodePad.zip.

Download do app: acesse a página de Releases do GitHub e baixe a versão mais recente do instalador (.exe).
Releases: https://github.com/juliareboucasleite/CodePad/releases

Para rodar pelo Maven, use mvnw javafx:run com JAVA_HOME apontando para um JDK 21+. Para empacotar o app-image, use package.ps1. O instalador .exe depende do WiX Toolset, então se quiser gerar o instalador use package-installer.ps1 após instalar o WiX.
