# Deployment
1. prepare build
   1. increase version in build.gradle
1. build
   1. `./gradlew clean bootJar`
1. push jar to server
   1. `scp build/libs/service*.jar asitapps-demo.iaik.tugraz.at:`
1. login to server
   1. `ssh asitapps-demo.iaik.tugraz.at`
1. deploy
```
sudo cp ~/service*.jar /opt/spring/terminal_sp/terminal_sp.jar
sudo systemctl restart terminal_sp
sudo journalctl -u terminal_sp.service -f
```
1. service will run at https://apps.egiz.gv.at/terminal_sp/