vijava-ant
==========

VMWare (vSphere) Ant tasks

Allows virtual machines to be created and controlled from Apache Ant.
Uses vijava api (http://vijava.sourceforge.net/).

Installation
============

1. Either compile from source or take JAR from downloads (https://github.com/wizecore/vijava-ant/downloads).
2. Place in ant library path or in your custom project.
3. Download vijava JAR from http://vijava.sourceforge.net/
4. Done

Examples
========

`<vm vcenter="test1" username="buildbot" password="123">`

`  <vmCreate vm="testvm1" host="test1host1"/>`

`</vm>`