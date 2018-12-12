Arcite
======

Arcite aims to be a general purpose biological data processing engine written in scala and based on Akka.

The purpose of Arcite is to enable scientists to run analysis on data produced by laboratory equipments 
like microrray scanner, NGS sequencer, etc.

##### Warning

This project should still be seen as Alpha or Proof of Concept (POC). There are many aspects that should be improved and 
probably done differently. 
However, Arcite can still be used almost in a productive setting as every step is traceable.  
 

### Key concepts and ideas
Arcite is a pipeline processing engine based on its own concept of a \"transform\".
A transform is a unique operation from a state A to a state B (e.g. the transformation from multiple files produced by a 
machine to a unique matrix, or the production of a normalized matrix from raw data).

A transform is performed by a worker that is running on a node of the cluster. Workers are usually able to perform
a few different transforms, usually in the same domain (e.g. preprocessing of NGS data). A worker can contain any dependencies 
that the transforms need (version of an OS, python, java, R, scala, spark, Bioconductor libraries, etc.).

The Arcite core modules take care of scheduling the workers on the cluster. If a transform breaks down (e.g. in case 
of an exception in some Python code) it should have no real effect on the whole Arcite cluster which will continue to run 
without trouble. If nothing comes back from the worker, the core will assume an exception and consider 
the worker to be dead (after a defined time out). It will eventually try to restart an instance if none for this worker
type would be available.      

In Arcite, everything is final and therefore immutable. 
A transform produces something that is immutable and there is no way back. That is one of the guarantee for reproducibility. 

Once a transform has been completed, an experiment definition cannot be changed, it would have to be recreated.
The reason is simple, as soon as something would have been produced
with the data (e.g. even a simple matrix given values for X samples with Y features), changing
the experiment definition might totally change the results. 


### Architecture
Arcite is written in Scala using the akka library. 
It should be deployed to a cluster of machines running docker. 
It's initial deployment target is DCOS but adapting it to another system like Kubernetes 
should not be a problem.  

### Building Arcite
To build Arcite you need sbt and an easy way to get it is to install sdkman (http://sdkman.io/)

### Deployment
Arcite needs a home mapped in each docker container, it can either be achieved using a NFS mount that can be reached
from every node or through solutions like CEPH.
 
There are some examples of DCOS Json config files in /deploy.

To be able to run as a cluster, three instances of Arcite have to be started :

-for the rest-api (example in cluster-rest.json): it will enable to serve Rest requests
-for the backend management :manages the cluster worker nodes
-for the helper functions : manages the experiments, their dependencies, etc.

The rest end point has to be configured in the Arcite web application 




