Arcite
======

Arcite aims to be a general purpose biological data management and processing engine based on Akka and Spark.

The purpose of Arcite is to enable scientists to run reproducible analysis on data produced by laboratory equipments 
like microrray scanner, sequencer, etc. 

### Key concepts and ideas
Ultimately, Arcite wants to share data, processing and analysis with everybody in a similar way as bitcoin transactions are 
known by every nodes through the blockchain!

Locally, Arcite is a pipeline processing engine based on its own concept of a \"transform\"
A transform is a unique operation from a state A to a state B (e.g. the transformation from multiple files produced by a 
machine to a unique matrix, or the production of a normalized matrix from raw data)

In Arcite everything is final and immutable. A transform produces something that is immutable and there is no way to change
it apart deleting it.

An experiment definition cannot be changed, it would have to be recreated. The reason is simple, as soon as something 
would have been produced with the data (e.g. even a simple matrix given values for X samples with Y features), changing
the experiment definition would corrupt the results. Thus, immutability is key to Arcite. 


