#!/bin/bash

if [ -z "$1" ] && [ "$1" = "-h" ] || [ "$1" = "help" ] || [ "$1" = "-?" ]; then
    echo "Exposes the OpenAPI Console by creating a Service and Route for the API server"
    echo ""
    echo "syntax:"
    echo "exposeOpenApi.sh [-n namespace]"
    echo ""
    echo "-n namespaces"
    echo "     Optional.  The namespace where the OpenAPI container is running.  Default value is kappnav."
    exit 0
fi

NAMESPACE="kappnav"

while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -n)
    NAMESPACE="$2"
    shift # past argument
    shift # past value
    ;;
esac
done

echo "Targeting namespace: $NAMESPACE"

scriptPath=`dirname "$0"`
kubectl create -f $scriptPath/service.yaml -n $NAMESPACE
kubectl create -f $scriptPath/route.yaml -n $NAMESPACE
