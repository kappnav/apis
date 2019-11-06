#!/bin/bash
set -Eeo pipefail

. ../build/version.sh
IMAGE=kappnav-apis

echo "Building ${IMAGE} ${VERSION}"
docker build --pull --build-arg VERSION=$VERSION --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') -t ${IMAGE} .
