package com.ieum.scheduler

import org.springframework.data.jpa.repository.JpaRepository

interface SchedulerCheckpointRepository : JpaRepository<SchedulerCheckpoint, String>
