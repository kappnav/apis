#!/bin/bash
set -Eeo pipefail

IMAGE=kappnav-apis
VERSION=0.1.1
ARCH=amd64

echo "Building ${IMAGE}"
docker build --build-arg VERSION=$VERSION --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') -t ${IMAGE} .
