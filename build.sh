#!/bin/bash
set -Eeo pipefail

# Travis builds won't have a peer build dir
VERSION=x.x.x
if [ -e ../build/version.sh ]; then
    . ../build/version.sh
fi

IMAGE=kappnav-apis

echo "Building ${IMAGE} ${VERSION}"
# docker build --pull --build-arg VERSION=$VERSION --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') -t ${IMAGE} .
