# KindActionMapping (KAM) Tests

These sample KAMs and ConfigMaps test the KAM functionalities for the actions, status-mapping, and sections config maps.

## Prereqs: 

1. Install a Kubernetes cluster 
1. Install the [Kubernetes command-line tool](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
1. Install [kubernetes Application Navigator](https://github.com/kappnav/README/blob/master/README.md)
1. Install [Action Developer Tool (actdev)](https://github.com/kappnav/apis/tree/master/tools/actdev)

## Run the test using the sample KAMs and ConfigMaps

### Install test kams and configmaps in the 'test' namespace: 

There are two sets of kams and configmaps yaml files:
1. kam1.yaml, map1-action.yaml, map1-section.yaml, map1-status.yaml
1. kam2.yaml, map2-action.yaml, map2-section.yaml, map2-status.yaml

To install all of the kams and configmaps, run the command in kappnav/apis/test/testkam: kubectl apply -f . 

To validate all kams and configmaps installed successfully: 

<ul>
<li> For kams, run 'kubectl get kam -n test', the result should be: </li>

      NAME   AGE
      kam1   20h
      kam2   20h

<li> For configmaps, run 'kubectl get configmap -n test' the result should be: </li>

      NAME                                   DATA   AGE
      test.actions.deployment.test1          1      20h
      test.actions.test2                     1      20h
      test.sections.deployment.test1         1      20h
      test.sections.test2                    1      20h
      test.status-mapping.deployment.test1   1      20h
      test.status-mapping.test2              1      20h
</ul>

### Run the test via Action Developer Tool (actdev):

Start the actdev tool.  Once the openapi/ui is fully initialized, drive the 'kindactionmapping' GET API: "GET ​/kindactionmapping​/{resource-kind} Computes config maps of a Kubernetes resource." with the following inputs:

resouce-kind: Deployment

      type: actions
      apiversion: tests/v1
      subkind: Liberty
      owner-apiversion: kappnav.operator.kappnav.io/v1
      owner-kind: Kappnav
      owner-id: 12345678-aaaa-bbbb-cccc-123456789abc
      namespace: test

Return data:
1. the candidate-maps list;
1. the content of map1-action.yaml and map2-action.yaml being merged into the "url-actions" section.

With the type value of 'status-mapping', you should see:
1. the candidate-maps list;
1. the content of map2-status.yaml.

With the type value of 'sections', you should see:
1. the candidate-maps list;
1. the content of map1-section.yaml and map2-section.yaml being merged into the "url-actions" section.

