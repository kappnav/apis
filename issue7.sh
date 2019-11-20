#!/bin/bash
./Users/jasuryak/GoodScripts/deleteAllDockerImages
echo -Dkappnav.disable.trust.all.certs=true > ./target/liberty/wlp/usr/servers/defaultServer/jvm.options
echo KAPPNAV_CONFIG_NAMESPACE=juniarti > ./target/liberty/wlp/usr/servers/defaultServer/server.env
./target/liberty/wlp/bin/server run defaultServer --clean
