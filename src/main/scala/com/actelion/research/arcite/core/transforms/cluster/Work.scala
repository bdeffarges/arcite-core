package com.actelion.research.arcite.core.transforms.cluster

case class Job(job: Any, jobType: String)
case class Work(workId: String, job: Job)

case class WorkResult(workId: String, result: Any)