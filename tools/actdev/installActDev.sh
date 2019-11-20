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

# check if user asked for help
arg=$1
if [ x$arg = 'x--?' ]; then
    echo "Syntax: installActDev.sh"
    echo ""
    echo "Where:"
    echo ""
    echo " 1. is one of: okd, ocp, minishift, minikube. Default is okd."
    echo " 2. is namepace in which kAppNav (or Application Navigator) is installed."
    exit 0
fi

if [[ $# -ne 2 ]]; then
    echo "Error: wrong number of parameters."
    echo "Syntax: installActDev.sh"
    echo ""
    echo "Where:"
    echo ""
    echo " 1. is one of: okd, ocp, minishift, minikube. Default is okd."
    echo " 2. is namepace in which kAppNav (or Application Navigator) is installed."
    exit 1
fi

# To make sure kubectl is available
kubectl=$(kubectl)
if [ $? -ne 0 ]; then
echo "Error: kubectl not found. You must install kubectl before using actdev.sh"
echo ""
exit 1
fi

# make sure user login to the kubernetes cluster already
if [ x$kubeEnv = x"minikube" ]; then
    nodes=$(kubectl get nodes)
else
    nodes=$(oc get nodes)
fi
if [ $? -ne 0 ]; then
    echo "Error: you are not configured to access any kubernetes cluster."
    echo " Please ensure you can access your cluster with kubectl before running this script again."
    echo ""
    echo "Hint: \'kubectl get nodes\' should display your cluster\'s nodes."
    exit 1
fi

kubeEnv=$1
kappNavNS=$2
mkdir $HOME/.actdev
echo $kubeEnv > $HOME/.actdev/kubeenv
echo $kappNavNS > $HOME/.actdev/namespace
echo "Installing developer tool for action development..."
# check if actdev already installed
exist=$(kubectl get Deployment actdev -n actdev 2>/dev/null)
if [ "$exist" != "" ]; then
    echo "Action Development tool already installed, existing."
    exit 0
else
    kubectl create namespace actdev
    cat actdev.yaml | sed "s|value: okd|value: $kubeEnv|" \
                    | sed "s|value: kappnav|value: $kappNavNS|" \
                    | kubectl create -f - -n actdev
    if [ x$kubeEnv != 'xminikube' ]; then
        kubectl apply -f actdev-route.yaml -n actdev --validate=false
    fi

    if [ $? -eq 0 ]; then
        echo "Successfully installed the tool"
    else 
        echo "Install failed, cleaning up now"
        # cleaning up
        kubectl delete -f actdev.yaml -n actdev
        if [ x$kubeEnv != 'xminikube' ]; then
            kubectl delete -f actdev-route.yaml -n actdev
        fi
        kubectl delete namespace actdev
    fi
fi 


