KindActionMapping (KAM) Tests

These KAMs and Maps test the KAM owner field, which has form:

owner: 
   kind: 
   apiVersion: 
   uid: 

These KAMs test Deployments created by the OpenLiberty operator (CRD=OpenLibertyApplication)

Prereqs: 

1. OpenLiberty operator installed into 'liberty' namespace
2. liberty/OpenLibertyApplication uid stored in kam3.yaml and kam4.yaml
3. Create OpenLiberty instance through operator.  Name it 'demo-app'.

Install tests: 

kubectl apply -f . -n liberty 

Using api server's /openapi/ui, drive 'components' API for 'demo-app' in 'liberty' namespace.  The return data should include following actions in the 
'demo-app' Deployment's actions array: 

map1
map2
map3
map4
