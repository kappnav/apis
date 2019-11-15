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

# This script will uninstall action development tool from the existing openshift cluster
# The script required 1 parameter such as minishift, minikube, okd or ocp

if [[ $# -ne 1 ]]; then
    echo "Illegal number of parameter. Required one parameter."
    echo "Specify where you want to uninstall from such as minishift, minikube, okd or ocp."
    exit 1
else 
    KUBE_ENV=$1
    cat actdev.yaml \
                | sed "s|value: okd|value: $KUBE_ENV|" \
		        > actdev-internal.yaml
fi

env=$(oc get nodes)
if [ $? -ne 0 ]; then
    echo "You are not login to any okd cluster, please do oc login first"
    exit $?
else
    echo "Uninstalling developer tool for action development from:"
    echo "          $env"
    # check if actdev installed
    exist=$(kubectl get Deployment -n actdev)
    if [ "$exist" == "" ]; then
        echo "Action Development tool does not appear to be installed on the cluster, existing."
        exit 0
    else
        kubectl delete -f actdev-internal.yaml -n actdev
        if [ x$KUBE_ENV != 'xminikube' ]; then
            kubectl delete -f actdev-route.yaml -n actdev
        fi
        echo "Sleeping for 30 seconds before deleting actdev namespace"
        sleep 30
        kubectl delete namespace actdev
    fi
fi 


