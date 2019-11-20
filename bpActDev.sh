#!/bin/bash

./build.sh

docker login docker.io -u $DOCKER_USER -p $DOCKER_PWD
docker tag kappnav-apis docker.io/juniarti/kappnav-apis:0.1.2
docker push docker.io/juniarti/kappnav-apis:0.1.2