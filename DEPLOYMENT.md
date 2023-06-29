# Deployment
1. prepare build
   1. push version in build.gradle
1. build with gradle task "buildJar"
1. authenticate via sftp to server
   1. `sftp stefan.kreiner@asitapps-demo.iaik.tugraz.at`
1. push jar to server
   1. `put build/libs/terminal_sp_server-2.1.0.jar terminal_sp.jar`
1. authenticate via ssh to server and act as superuser 
   1. `ssh stefan.kreiner@asitapps-demo.iaik.tugraz.at`
   2. `sudo -i`
1. make server jar executable, move to terminal_sp spring folder, restart service and listen:
```
cd /opt/spring/terminal_sp
mv /home/stefan.kreiner/terminal_sp.jar .
chmod +x terminal_sp.jar
systemctl stop terminal_sp.service
systemctl start terminal_sp.service
journalctl -u terminal_sp.service -f
```
1. make sure the service runs on https://apps.egiz.gv.at/terminal_sp/