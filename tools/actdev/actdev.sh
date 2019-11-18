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
# This scripts tests for actdev service and opens the browser
# to it's url address if found. 

arg=$1

# check if user asked for help
if [ x$arg = 'x--?' ]; then 
    echo "actdev.sh launches the actdev service in your browser"
    echo ""
    echo "syntax: actdev.sh"
    echo ""
    exit 0
fi

# get kubeEnv
kubeEnvFile="$HOME/.actdev/kubeenv"
if [ -e $kubeEnvFile ]; then
    kubeEnv=$(cat $kubeEnvFile)
else
    echo "Error: file $kubeEnvFile not found.  Did you install actdev?  See installActDev.sh script."
    echo ""
    exit 1 
fi

# get [k]appnav namespace 
namespaceFile="$HOME/.actdev/namespace"
if [ -e $kubeEnvFile ]; then
    namespace=$(cat $namespaceFile)
else
    echo "Error: file $namespaceFile not found.  Did you install actdev?  See installActDev.sh script."
    echo ""
    exit 1 
fi

# make sure kubectl command is installed 
kubectl=$(kubectl)
if [ $? -ne 0 ]; then
    echo "Error: kubectl not found.  You must install kubectl before using actdev.sh"
    echo ""
    exit 1
fi

# make sure a kubernetes cluster is accessible 
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


# make sure actdev is installed 
actdev=$(kubectl get deployment actdev -n actdev)
if [ $? -ne 0 ]; then
    echo "Error: actdev is not installed on your cluster."
    echo "       Please install first with installActDev.sh."
    echo ""
    exit 1
fi

# get actdev url 
if [ x$kubeEnv = 'xminikube' ]; then
    # make sure minikube is installed 
    mk=$(minikube)
    if [ $? -ne 0 ]; then
        echo "Error: minikube not found.  You must install minikube before using actdev.sh"
        echo ""
        exit 1
    fi
    url=$(minikube service actdev -n actdev --url --format "http://{{.IP}}:{{.Port}}/openapi/ui")
else
    # check if route is available on okd/ocp
    host=$(kubectl get route actdev -n actdev -o=jsonpath={@.spec.host})
    if [ -z host ]; then
        echo "Could not retrieve host from actdev route. Confirm install is correct."
        exit 1 
    fi 
    url="http://$host/openapi/ui/"
fi 

# check if url responding 
curl=$(curl $url >/dev/null 2>/dev/null)
if [ $? -ne 0 ]; then
    echo "Error: $url not responding. Check that initialization is complete and successful."
    echo ""
    exit 1
fi

# everything checks out, open browser
open $url
        
exit 0