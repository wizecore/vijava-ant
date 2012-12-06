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

    <!-- Creates VM from kickstart ISO (must automatically install VMWare tools) -->
    <vm vcenter="test1" username="buildbot" password="123">
      <vmCreate vm="testvm1" host="test1host1" os="ubuntuGuest"
        iso="/iso/ubuntu-11.04-ks86.iso" bootorder="cdrom,disk"
        wait="true" cpu="2" memory="2048" disksize="5000"/>
      <vmWait vm="testvm1" powerOn="true" boot="true" ipAddress="true"/>
    </vm>    
    
Quick help
==========

vmCreate os="" - VMWare guestId. List of values can be taken from 
http://www.doublecloud.org/2011/03/finding-out-guest-os-running-on-a-virtual-machine/