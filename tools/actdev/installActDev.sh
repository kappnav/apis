#!/bin/sh
  
#*****************************************************************
#*
#* Copyright 2019 IBM Corporation
#*
#* Licensed under the Apache License, Version 2.0 (the "License");
#* you may not use this file except in compliance with the License.
#* You may obtain a copy of the License at

#* http://www.apache.org/licenses/LICENSE-2.0
#* Unless required by applicable law or agreed to in writing, software
#* distributed under the License is distributed on an "AS IS" BASIS,
#* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#* See the License for the specific language governing permissions and
#* limitations under the License.
#*
#*****************************************************************

# This script will install action development tool to the existing openshift cluster
# The script assume you have installed kappnav/appnav to the cluster 
# The script required 2 parameters:
# First parameter will be the environment such as minishift, minikube, okd or ocp
# Second parameter will be the namespace where you installed kappnav/appnav 

if [[ $# -ne 2 ]]; then
    echo "Illegal number of parameter. Required two parameters."
    echo "Specify where you want to install such as minishift, minikube, okd or ocp as first parameter."
    echo "And the namespace where you installed kappnav/appnav as second parameter."
    exit 1
else 
    KUBE_ENV=$1
    kappNavNS=$2
    cat actdev.yaml \
                | sed "s|value: okd|value: $KUBE_ENV|" \
                | sed "s|value: kappnav|value: $kappNavNS|" \
		        > actdev-internal.yaml
fi

env=$(oc get nodes)
if [ $? -ne 0 ]; then
    echo "You are not login to any okd cluster, please do oc login first"
    exit $?
else
    echo "Installing developer tool for action development on:"
    echo "          $env"
    # check if actdev already installed
    exist=$(kubectl get Deployment -n actdev)
    if [ "$exist" != "" ]; then
        echo "Action Development tool already installed, existing."
        exit 0
    else
        kubectl create namespace actdev
        kubectl apply -f actdev-internal.yaml -n actdev 
        if [ x$KUBE_ENV != 'xminikube' ]; then
            kubectl apply -f actdev-route.yaml -n actdev --validate=false
        fi

        if [ $? -eq 0 ]; then
            echo "Successfully installed the tool"
            if [ x$KUBE_ENV = 'xminishift' -o x$KUBE_ENV = 'xokd' -o x$KUBE_ENV = 'xocp' ]; then
                routeHost=$(kubectl get route actdev -n actdev -o=jsonpath={@.spec.host})
                if [ -z routeHost ]; then
                    echo "Could not retrieve host from route actdev service"
                else
                    echo 'Retrieved host name '$routeHost
                    echo "Sleeping for 60 seconds before opening the openapi/ui url"
                    sleep 60
                    actdevURL="http://$routeHost/openapi/ui/"
                    echo $actdevURL
                    open $actdevURL
                    
                fi
            else
                if [ x$KUBE_ENV = 'xminikube' ]; then
                    echo "Sleeping for 60 seconds before opening the openapi/ui url"
                    sleep 60
                    minikube service actdev -n actdev --format "http://{{.IP}}:{{.Port}}/openapi/ui"
                fi
            fi
        else 
            echo "Installing failing, cleaning up now"
            # cleaning up
            kubectl delete -f actdev.yaml -n actdev
            kubectl delete namespace actdev
        fi
    fi
fi 


