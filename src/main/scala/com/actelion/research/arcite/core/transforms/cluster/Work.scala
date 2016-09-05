package com.actelion.research.arcite.core.transforms.cluster

// todo should probably be removed as we will only keep the transform version
case class Job(job: Any, jobType: String)
case class Work(workId: String, job: Job)

case class WorkResult(workId: String, result: Any)


// for the transform case
