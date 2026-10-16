@echo off
rem Lancador da CLI da padolang no Windows. No zip da Release o jar ja vem pronto;
rem a partir do repositorio, rode compilar.sh antes (no Git Bash ou no WSL).
rem Coloque esta pasta bin\ no PATH para usar "padolang check arquivo.padoin".
java -jar "%~dp0..\out\padolang.jar" %*
