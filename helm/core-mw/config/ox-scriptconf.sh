LIBPATH=/opt/open-xchange/lib
PROPERTIESDIR=/opt/open-xchange/etc
OSGIPATH=/opt/open-xchange/osgi

# Define the Java options for the groupware Java virtual machine.
JAVA_OPTS_GC="-XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSInitiatingOccupancyOnly -XX:NewRatio=3"
JAVA_OPTS_LOG="-Dlogback.threadlocal.put.duplicate=false -XX:-OmitStackTraceInFastThrow"
JAVA_OPTS_MEM="-XX:MaxHeapSize={{ .Values.jvm.memory.maxHeapSize | default "512M" }} -XX:MaxPermSize={{ .Values.jvm.memory.maxPermSize | default "256M" }} -XX:+UseTLAB"
JAVA_OPTS_NET="-Dsun.net.inetaddr.ttl=3600 -Dnetworkaddress.cache.ttl=3600 -Dnetworkaddress.cache.negative.ttl=10"
JAVA_OPTS_OSGI="-Dosgi.compatibility.bootdelegation=false"
JAVA_OPTS_SERVER="-server -Djava.awt.headless=true {{ .Values.jvm.opts.server }}"
#JAVA_OPTS_SECURITY="-Dorg.osgi.framework.security=osgi -Djava.security.policy=/opt/open-xchange/etc/all.policy -Dopenexchange.security.policy=/opt/open-xchange/etc/security/policies.policy -Duser.dir=/opt/open-xchange/bundles -Djna.platform.library.path=/usr/lib/x86_64-linux-gnu:/usr/lib64"

JAVA_OPTS_OTHER={{ .Values.jvm.opts.other | quote }}

# Define options for debugging the groupware Java virtual machine, disabled by default.
#JAVA_OPTS_DEBUG="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/mnt/heapdump -Xloggc:/var/log/open-xchange/gc.log -verbose:gc -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintTenuringDistribution"

# Defines the Java options for all command line tools. CLTs need much less memory compared to the groupware process.
JAVA_OXCMD_OPTS="-Djava.net.preferIPv4Stack=true"

# Maximum number of open Files for the groupware. This value will only be
# applied when using sysv init. For systemd have a look at the drop-in configs
# at /etc/systemd/system/open-xchange.service.d
NRFILES=65536

# Maximum number of processes or more precisely threads for the groupware. This
# value will only be applied when using sysv init. For systemd have a look at
# the drop-in configs at /etc/systemd/system/open-xchange.service.d
NPROC=65536

# Specify the umask of file permissions to be created by ox, e.g. in the
# filestore.
# BEWARE: setting a nonsense value like 666 will make open-xchange stop working!
#         useful values are 006 or 066
UMASK=066