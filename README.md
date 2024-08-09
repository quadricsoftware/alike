# alike
Project for Alike Backup, a BDR solution for XenServer, XCP-ng, and Hyper-V virtualization platforms. Download the prebuilt platform with the Docker 

## Prebuilt Docker Images
Dev Preview: quadricsoftware/alike-v7:preview 

Current Longterm Stable: quadricsoftware/alike-v7:9631b


## Building from Sources

### Java

The Java Data Engine manages Alike global data deduplication and can be built with Ant. The "lib" directory contains a jarlist that needs to be satisfied prior to building.

### Appliance

These PHP 7.5 scripts run from in /usr/local/sbin of the A3 Linux appliance (quadricsoftware/alike-v7) and can also run on any Linux installation. 

### WebUI

The WebUI project is the web root of the Alike PHP/Ajax Web listener. Alike uses Nginx in its reference implementation and use of Nginx is recommended.

### Fuse

These Fuse filesystem components allow instant restore of files and disks through a virtual Fuse filesystem, and are built using make. After building, the resulting executables can be deployed to /usr/local/sbin.

