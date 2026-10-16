@echo off
rem Lancador da CLI da padolang no Windows (rode compilar.sh antes, no Git Bash ou no WSL).
rem Coloque esta pasta bin\ no PATH para usar "padolang check arquivo.padoin".
java -jar "%~dp0..\out\padolang.jar" %*
