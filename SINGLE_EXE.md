Para gerar um executavel unico (apenas um .exe) no Windows, vamos usar um auto-extrator do 7-Zip.
Ele empacota a pasta completa do app e cria um unico arquivo que extrai e executa.

Requisitos
1) 7-Zip instalado (para ter 7z.exe e 7z.sfx).
2) JAVA_HOME apontando para um JDK 21+ (para gerar o app-image).

Passos
1) Execute o script:
   .\single-exe.ps1

Saida
dist\CodePad-Standalone.exe

Observacao
Esse exe unico extrai para uma pasta temporaria e executa o app. O primeiro start pode demorar um pouco.
