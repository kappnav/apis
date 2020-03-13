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
    
Note, you may have to refresh browser after it first starts to see fully initialized openapi/ui.

## Uninstall 

Run ./uninstallActDev.sh 

## Tutorial - Create New Action

Note: this tutorial is designed for Openshift. 

In this mini tutorial, we will create an action to open the stock-trader application's loyalty-component home page.  We will take these steps: 

1. Create Route resource 
1. Design URL action
1. Install the action
1. Test URl action substitution
1. Test action in {k}AppNav

### 1. Create Route resource 

1. login to your Openshift cluster
1. download [loyalty_route.md](https://github.com/kappnav/apis/blob/master/tools/actdev/doc/loyalty_route.yaml)
1. create, with command: 

```
kubectl apply -f loyalty_route.md
```

### 2. Test home page URL

The target pattern we are creating is: 

```
http://{loyalty-route-hostname}
```

If we were forming the url manually, we would first retrieve {loyalty-route-hostname} from the route using this command: 

```
kubectl get route -n stock-trader loyalty-level -o json 
```

Pull out the spec.host value, and then form the full URL.  

---------

In a {k}AppNav action, we can form the URL using the following substitution pattern to retrieve the Route resource in JSON format: 

```
${func.kubectlGet(Route,-n,${resource.$.metadata.namespace},${resource.$.metadata.name},-o,json)}
```

Since the kubectlGet function above returns the entire Route JSON object, we will use a Javascript snippet to parse out the host value: 

```
function getRouteHost(route) { 
    var routeJSON = JSON.parse(route);
    var host = routeJSON.spec.host;
    return host;
}
```

### 3. Install the Action

Download [loyalty_action.md](https://github.com/kappnav/apis/blob/master/tools/actdev/doc/loyalty_actions.yaml) and install with command: 

```
kubectl apply -f loyalty_action.md
```

### 4. Test URl action substitution

![test](https://github.com/kappnav/apis/blob/master/tools/actdev/doc/actdev.test-action.png)

![result](https://github.com/kappnav/apis/blob/master/tools/actdev/doc/actdev.action-result.png)


### 5. Test action in {k}AppNav