# alike
Project for Alike Backup, a BDR solution for XenServer, XCP-ng, and Hyper-V virtualization platforms. 
Download the prebuilt platform with the Docker image, or you can use our pre-made virtual appliance (below)

## Xen Virtual Appliance (A3)
The simplest and fastest way to get up and running with Alike is to use our pre-made, Alpine Linux based virtual appliance.
To install, [download the XVA image](https://github.com/quadricsoftware/alike/raw/main/binaries/A3_v1.0.5.xva.7z), and import into your Xen environment
Boot the VM, follow the command line menu steps to add storage, and start the Alike services.
Once the Alike services start, switch to the Web UI to continue the setup and get started.

## Prebuilt Docker Images
Please use the docker-compose example file to set up your container quickly and easily. There are two main images to choose from:

Public Preview (Alike v7.5): 
quadricsoftware/alike-v7:preview 

Longterm Stable (Alike v7.2): 
quadricsoftware/alike-v7:LTR


## Building from Sources

### Java

The Java Data Engine manages Alike global data deduplication and can be built with Ant. The "lib" directory contains a jarlist that needs to be satisfied prior to building.

### Appliance

These PHP 7.5 scripts run from in /usr/local/sbin of the A3 Linux appliance (quadricsoftware/alike-v7) and can also run on any Linux installation. 

### WebUI

The WebUI project is the web root of the Alike PHP/Ajax Web listener. Alike uses Nginx in its reference implementation and use of Nginx is recommended.

### Fuse

These Fuse filesystem components allow instant restore of files and disks through a virtual Fuse filesystem, and are built using make. After building, the resulting executables can be deployed to /usr/local/sbin.

