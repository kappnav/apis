# Action Developer Tool (actdev)

Use this tool to help you develop {k}AppNav actions.  

## Install

Run ./installActDev.sh

```
-> ./installActDev.sh --?

Syntax: installActDev.sh {platform} {namespace} {image} {tag}

Where:

1. {platform} is one of: okd, ocp, minishift, minikube. Default is okd.
2. {namespace} is namepace in which kAppNav (or Application Navigator) is installed. Default is kappnav.
3. {image} is the container image repo/org from which to obtain the actdev image. The default is docker.io/kappnav/apis.
4. {tag} is the image tag value to install. The default is operator/releases/latest.
5. {policy} is the image pull policy - e.g. Always.  The default is IfNotPresent.

A period ('.') may be specified in the place of any of the preceding positional parameters to request the default value.
```

## Start 

Run ./actdev.sh

## Uninstall 

Run ./uninstallActDev.sh 
