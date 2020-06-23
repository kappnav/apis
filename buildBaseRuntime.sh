#!/bin/bash
set -Eeo pipefail

IMAGE=base-runtime

echo "Building ${IMAGE}"
docker build --pull -t ${IMAGE} -f Dockerfile-base-runtime .
