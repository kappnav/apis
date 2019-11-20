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

# get kubeEnv
kubeEnvFile="$HOME/.actdev/kubeenv"
if [ -e $kubeEnvFile ]; then
    kubeEnv=$(cat $kubeEnvFile)
else
    echo "Error: file $kubeEnvFile not found.  Did you install actdev?  See installActDev.sh script."
    echo ""
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

if [ $? -ne 0 ]; then
    echo "You are not login to any okd cluster, please do oc login first"
    exit $?
else
    echo "Uninstalling developer tool for action development from:"
    echo "          $nodes"
    # check if actdev installed
    exist=$(kubectl get Deployment -n actdev 2>/dev/null)
    if [ "$exist" == "" ]; then
        echo "Action Development tool does not appear to be installed on the cluster, existing."
        exit 0
    else
        cat actdev.yaml | sed "s|value: okd|value: $kubeEnv|" \
                    | kubectl delete -f - -n actdev
        if [ x$kubeEnv != 'xminikube' ]; then
            kubectl delete -f actdev-route.yaml -n actdev
        fi
        kubectl delete namespace actdev
        rm -fr ~/.actdev
    fi
fi 


