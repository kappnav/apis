#!/bin/bash
set -Eeo pipefail

. ../build/version.sh

IMAGE=kappnav-apis

echo "Building ${IMAGE} ${VERSION}"
COMMIT=$(git rev-parse HEAD)
docker build --pull --build-arg VERSION=$VERSION --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') --build-arg COMMIT=$COMMIT -t ${IMAGE} .

# If this build is being done on an update to the master branch then tag the image as "dev" and push to docker hub kappnav org
if [ "$TRAVIS" == "true" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH"  == "master" ]; then
    echo $DOCKER_PWD | docker login docker.io -u $DOCKER_USER --password-stdin
    targetImage=docker.io/kappnav/apis:dev
    echo "Pushing Docker image $targetImage"
    docker tag ${IMAGE} ${targetImage}
    docker push ${targetImage}
fi
