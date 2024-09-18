quiet=0
alikeUpdate=0
sysUpdate=1

while test $# -gt 0
do
    case "$1" in
        -alike) alikeUpdate=1
            ;;
        -sys) sysUpdate=1
            ;;
        -quiet) quiet=1
            ;;
        --*) echo "bad option $1"
            ;;
    esac
    shift
done

if [ ${quiet} -eq 0 ]; then
	echo "Welcome to the A3 updater"
	echo
	echo This script will update the A3 docker appliance- NOT the Alike software!
	echo 
	read -p "Press any key to continue" cont
	echo
fi

[ -f /home/alike/logs/ws.log ] && rm /home/alike/logs/ws.log

if ! id -nG "alike" | grep -qw "docker"; then                                                                                                                                                 
    if sudo addgroup -S "alike" "docker" ; then                                                                                                                                               
        echo "User alike successfully added to group docker."                                                                                                                                 
    else                                                                                                                                                                                      
        echo "Failed to add user alike to docker group."                                                                                                                                      
        exit 1                                                                                                                                                                                
    fi
fi


echo "Downloading latest A3 Host updates"
wget -qO /tmp/a3.sys.tgz https://raw.githubusercontent.com/quadricsoftware/alike/main/host/a3.sys.tgz
tar -zxf /tmp/a3.sys.tgz -C /usr/local/sbin/
dos2unix /usr/local/sbin/makeSupportTar
mv /usr/local/sbin/bashrc /home/alike/.bashrc
chown alike.alike /home/alike/.bashrc
if [ -f /usr/local/sbin/sudoers ]; then
	mv -f /usr/local/sbin/sudoers /etc/
	chown root.root /etc/sudoers
	chmod 440 /etc/sudoers
fi
touch /home/alike/configs/node_mode
chown alike.alike /home/alike/configs/node_mode
rm /tmp/a3.sys.tgz
echo "Completed updating A3 system"
echo

ln -sfn /usr/local/sbin/update_a3.sh /etc/periodic/daily/
ln -sfn /usr/local/sbin/update-check.sh /etc/periodic/daily/

echo "0 2 * * *    /usr/local/sbin/docker-clean.sh 2&>1 > /dev/null" | crontab -

if [ ! -f "/home/alike/docker-compose.yml" ]; then
	echo "Downloading Alike default contigs"
	wget -qO /home/alike/docker-compose.yml https://raw.githubusercontent.com/quadricsoftware/alike/main/docker/docker-compose.yml 
else
	echo "Existing docker-compose.yml found.  Skipping update, using existing config."
fi
wget -qO - 'https://raw.githubusercontent.com/quadricsoftware/alike/main/host/a3.rev.num' > /usr/local/sbin/rev.num
echo 
if [ ${sysUpdate} -eq 1 ]; then
	echo "Updating A3 system packages"
	sudo apk update
	sudo apk add php7-pdo_sqlite
	sudo apk add --upgrade apk-tools
	sudo apk upgrade --available
	sudo apk upgrade openssh
	sudo apk add cifs-utils
	#sudo apk -U upgrade
	sudo /sbin/reboot
fi


if [ ${alikeUpdate} -eq 1 ]; then
	echo
	echo "Downloading Alike software updates..."
	cd /home/alike/
	sudo docker-compose pull
	echo Complete
	echo
fi
