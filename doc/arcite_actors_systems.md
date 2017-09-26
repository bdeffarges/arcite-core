### Arcite-core actor systems (not including the actual workers AS)

"ArcTransfActClustSys": the arcite cluster worker system


"ArcWorkerActSys" : the wrapper AS for the workers but the worker implementation live in their own AS 


"rest-api": the rest Api AS (akka-http)


"experiments-actor-system": manages the experiments life-cycle
    "experiments_manager": actor to add, delete, etc. exp.
    "event_logging_info": actor for log, last events, etc.
    "file_service": to upload files...
    "define_raw_data": actor to define raw data from mounts
     

"meta-info-actor-system": for meta information, like finding out all the categories of experiments 
