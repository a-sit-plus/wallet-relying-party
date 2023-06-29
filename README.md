# Terminal Service Point

Setup E-ID:
Private IDA credentials verwenden um eine Testidentität für das Testsystem zu erstellen: https://www.a-trust.at/testidentitaetenmanagement/Login/Default.aspx
EINSTELLUNGEN > URLs setzen: A-SIT DEV
EINSTELLUNGEN > VDA Component: A-Trust Abnahme
VDA > AKTIVIEREN (und anmelden über private **Test**-Credentials aus Test-identitätenmanager)
BINDUNG > ERZEUGEN
https://www.a-trust.at/testidentitaetenmanagement/Login/Default.aspx --> hier mit private IDA credentials anmelden, Test-identität erzeugen, App bindingen via VDA > AKTIVIEREN



Aktiv angemeldete/wartende user können dann über folgenden Link angesehen werden:
http://localhost:8080/HYUhYQpV5LsKxe7fQ5l84oOVyTDyHqMZy3PJGuVC9hc/



Deployment: ssh stefan.kreiner@asitapps-demo.iaik.tugraz.at
Build executable *.jar
copy to root@asitapps-demo:/opt/spring/terminal_sp/config

Start server
systemctl start terminal_sp
systemctl stop terminal_sp
systemctl status terminal_sp

debugging for non-application issues:
journalctl -u(f) terminal_sp.service


Puppet managed:
cat /etc/systemd/system/terminal_sp.service



Test:
asitapps-demo.iaik.tugraz.at:8093
https://apps.egiz.gv.at/terminal_sp

Home: SFTP: asitapps-demo.iaik.tugraz.at
/home/stefan.kreiner



Wallet app: eid.a-sit.at/wallet
wallet app implicit intent url: https://wallet.a-sit.at/mobile

implementation group: 'at.asitplus.wallet', name: 'vclib', version: '1.7.2'

DEPLOYMENT:
1. prepare build
   1. push version in build.gradle
1. build with gradle task "buildJar"
1. authenticate via sftp to server
   1. `sftp stefan.kreiner@asitapps-demo.iaik.tugraz.at`
1. push jar to server
   1. `put build/libs/terminal_sp_server-2.0.0.jar terminal_sp.jar`
1. authenticate via ssh to server and act as superuser 
   1. `ssh stefan.kreiner@asitapps-demo.iaik.tugraz.at`
   2. `sudo -i`
1. make server jar executable and move to terminal_sp spring folder
   1. `cd /opt/spring/terminal_sp`
   1. `mv /home/stefan.kreiner/terminal_sp.jar .`
   2. `chmod +x terminal_sp.jar`
1. restart service
    1. `systemctl stop terminal_sp.service`
    1. `systemctl start terminal_sp.service`
1. make sure the service runs on https://apps.egiz.gv.at/terminal_sp/
   1. `journalctl -u terminal_sp.service -f`


MDOC -> Android library für 18013-5

MDOC-Credentials erstellen und validieren 

Android Referenz: Client: 
- Annehmen & in google wallet ablegen
- Presentation von document 

Android library für:
 - MDOC- Struktur (ISO/IEC 18013-5) verarbeiten/erstellen/validieren
 - 


## Getting started

To make it easy for you to get started with GitLab, here's a list of recommended next steps.

Already a pro? Just edit this README.md and make it your own. Want to make it easy? [Use the template at the bottom](#editing-this-readme)!

## Add your files

- [ ] [Create](https://docs.gitlab.com/ee/user/project/repository/web_editor.html#create-a-file) or [upload](https://docs.gitlab.com/ee/user/project/repository/web_editor.html#upload-a-file) files
- [ ] [Add files using the command line](https://docs.gitlab.com/ee/gitlab-basics/add-file.html#add-a-file-using-the-command-line) or push an existing Git repository with the following command:

```
cd existing_repo
git remote add origin https://gitlab.iaik.tugraz.at/a-sit/terminal-service-point.git
git branch -M main
git push -uf origin main
```

## Integrate with your tools

- [ ] [Set up project integrations](https://gitlab.iaik.tugraz.at/a-sit/terminal-service-point/-/settings/integrations)

## Collaborate with your team

- [ ] [Invite team members and collaborators](https://docs.gitlab.com/ee/user/project/members/)
- [ ] [Create a new merge request](https://docs.gitlab.com/ee/user/project/merge_requests/creating_merge_requests.html)
- [ ] [Automatically close issues from merge requests](https://docs.gitlab.com/ee/user/project/issues/managing_issues.html#closing-issues-automatically)
- [ ] [Enable merge request approvals](https://docs.gitlab.com/ee/user/project/merge_requests/approvals/)
- [ ] [Automatically merge when pipeline succeeds](https://docs.gitlab.com/ee/user/project/merge_requests/merge_when_pipeline_succeeds.html)

## Test and Deploy

Use the built-in continuous integration in GitLab.

- [ ] [Get started with GitLab CI/CD](https://docs.gitlab.com/ee/ci/quick_start/index.html)
- [ ] [Analyze your code for known vulnerabilities with Static Application Security Testing(SAST)](https://docs.gitlab.com/ee/user/application_security/sast/)
- [ ] [Deploy to Kubernetes, Amazon EC2, or Amazon ECS using Auto Deploy](https://docs.gitlab.com/ee/topics/autodevops/requirements.html)
- [ ] [Use pull-based deployments for improved Kubernetes management](https://docs.gitlab.com/ee/user/clusters/agent/)
- [ ] [Set up protected environments](https://docs.gitlab.com/ee/ci/environments/protected_environments.html)

***

# Editing this README

When you're ready to make this README your own, just edit this file and use the handy template below (or feel free to structure it however you want - this is just a starting point!). Thank you to [makeareadme.com](https://www.makeareadme.com/) for this template.

## Suggestions for a good README
Every project is different, so consider which of these sections apply to yours. The sections used in the template are suggestions for most open source projects. Also keep in mind that while a README can be too long and detailed, too long is better than too short. If you think your README is too long, consider utilizing another form of documentation rather than cutting out information.

## Name
Choose a self-explaining name for your project.

## Description
Let people know what your project can do specifically. Provide context and add a link to any reference visitors might be unfamiliar with. A list of Features or a Background subsection can also be added here. If there are alternatives to your project, this is a good place to list differentiating factors.

## Badges
On some READMEs, you may see small images that convey metadata, such as whether or not all the tests are passing for the project. You can use Shields to add some to your README. Many services also have instructions for adding a badge.

## Visuals
Depending on what you are making, it can be a good idea to include screenshots or even a video (you'll frequently see GIFs rather than actual videos). Tools like ttygif can help, but check out Asciinema for a more sophisticated method.

## Installation
Within a particular ecosystem, there may be a common way of installing things, such as using Yarn, NuGet, or Homebrew. However, consider the possibility that whoever is reading your README is a novice and would like more guidance. Listing specific steps helps remove ambiguity and gets people to using your project as quickly as possible. If it only runs in a specific context like a particular programming language version or operating system or has dependencies that have to be installed manually, also add a Requirements subsection.

## Usage
Use examples liberally, and show the expected output if you can. It's helpful to have inline the smallest example of usage that you can demonstrate, while providing links to more sophisticated examples if they are too long to reasonably include in the README.

## Support
Tell people where they can go to for help. It can be any combination of an issue tracker, a chat room, an email address, etc.

## Roadmap
If you have ideas for releases in the future, it is a good idea to list them in the README.

## Contributing
State if you are open to contributions and what your requirements are for accepting them.

For people who want to make changes to your project, it's helpful to have some documentation on how to get started. Perhaps there is a script that they should run or some environment variables that they need to set. Make these steps explicit. These instructions could also be useful to your future self.

You can also document commands to lint the code or run tests. These steps help to ensure high code quality and reduce the likelihood that the changes inadvertently break something. Having instructions for running tests is especially helpful if it requires external setup, such as starting a Selenium server for testing in a browser.

## Authors and acknowledgment
Show your appreciation to those who have contributed to the project.

## License
For open source projects, say how it is licensed.

## Project status
If you have run out of energy or time for your project, put a note at the top of the README saying that development has slowed down or stopped completely. Someone may choose to fork your project or volunteer to step in as a maintainer or owner, allowing your project to keep going. You can also make an explicit request for maintainers.
