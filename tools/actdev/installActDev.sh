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

# This script will install the action development tool to an existing cluster
# The script assumes you have installed kappnav/appnav to the cluster 
# The script supports 4 parameters - issue 'installActDev.sh --?' for syntax

# check if user asked for help
arg=$1

if [ x$arg = 'x--?' ]; then
    echo "Syntax: installActDev.sh {platform} {namespace} {image} {tag}"
    echo ""
    echo "Where:"
    echo ""
    echo "1. {platform} is one of: okd, ocp, minishift, minikube. Default is okd."
    echo "2. {namespace} is namepace in which kAppNav (or Application Navigator) is installed. Default is kappnav."
    echo "3. {image} is the container image repo/org from which to obtain the actdev image. The default is docker.io/kappnav/apis."
    echo "4. {tag} is the image tag value to install. The default is operator/releases/latest."
    echo "5. {policy} is the image pull policy - e.g. Always.  The default is IfNotPresent."
    echo ""
    echo "A period ('.') may be specified in the place of any of the preceding positional parameters to request the default value."
    exit 0
fi

# parameters 
# kubeEnv=$1
# kappNavNS=$2
# image=$3 
# tag=$4
# policy=$5 

if [ x$1 != 'x' ] && [ x$1 != x'.' ]; then # user specified repo/org
    kubeEnv=$1
else
    kubeEnv=okd
fi

if [ x$2 != 'x' ] && [ x$2 != x'.' ]; then # user specified repo/org
    kappNavNS=$2
else
    kappNavNS='kappnav'
fi

if [ x$3 != 'x' ] && [ x$3 != x'.' ]; then # user specified image
    image=$3
else
    image="docker.io/kappnav/apis"
fi

if [ x$4 != 'x' ] && [ x$4 != x'.' ]; then # user specified tag, use it 
    tag=$4
else 
    yaml="../../../operator/releases/latest"
    if [ -e $yaml ]; then
        tag=$(cat  $yaml/kappnav.yaml | grep image: | awk '{ split($2,t,":"); print t[2] }') 
    else
        echo 'Error! File' $yaml 'is not found. This script requires that:'
        echo ''
        echo '1. Current directory is $kappnav/apis/tools/actdev'
        echo '2. $kappnav/apis and $kappnav/operator are peer directories.'
        exit 1
    fi
fi

if [ x$5 != 'x' ] && [ x$5 != x'.' ]; then # user specified image
    policy=$5
else
    policy="IfNotPresent"
fi

echo 'Following install options selected:'
echo ''
echo 'env='$kubeEnv
echo 'ns='$kappNavNS 
echo 'image='$image
echo 'tag='$tag 
echo 'policy='$policy
echo ''

read -p 'Install actdev? (Y|n)' response 

if [ x$response == 'y' ] || [ x$response == 'x' ]; then
    echo "Installing..."
else
    echo "Install canceled."
    exit 0 
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
                    | sed "s|IMAGE|$image|" \
                    | sed "s|TAG|$tag|" \
                    | sed "s|POLICY|$policy|" \
                    | kubectl create -f - -n actdev
    if [ x$kubeEnv != 'xminikube' ]; then
        kubectl apply -f actdev-route.yaml -n actdev --validate=false
    fi

    if [ $? -eq 0 ]; then
        echo "Successfully installed the tool"
    else 
        echo "Install failed, cleaning up now"
        # cleaning up
        ./uninstallActDev.sh
    fi
fi 