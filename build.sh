#!/bin/bash
set -Eeo pipefail
if [ $(arch) = "ppc64le" ]; then
  ARCH=ppc64le
elif [ $(arch) = "s390x" ]; then
  ARCH=s390x
else
  ARCH=amd64
fi
#IMAGE=kappnav-init-$ARCH
IMAGE=kappnav-apis

echo "Building ${IMAGE}"

docker build -t ${IMAGE} . 
