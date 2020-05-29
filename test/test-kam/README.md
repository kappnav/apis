KindActionMapping (KAM) Tests

These KAMs and Maps test the KAM functionalities for actions, status-mapping, and sections config maps.

Prereqs: 

Action Developer Tool (actdev) installed into 'actdev' namespace, https://github.com/kappnav/apis/tree/master/tools/actdev

Install test kams and configmaps (in "test" namespace): 

   kubectl apply -f . 

   Or

   kubectl apply -f kam1.yaml
   kubectl apply -f map1-action.yaml
   kubectl apply -f map1-status.yaml 
   kubectl apply -f map1-section.yaml

   kubectl apply -f kam2.yaml
   kubectl apply -f map2-action.yaml
   kubectl apply -f map2-status.yaml 
   kubectl apply -f map2-section.yaml 

Run the test via Action Developer Tool (actdev):
