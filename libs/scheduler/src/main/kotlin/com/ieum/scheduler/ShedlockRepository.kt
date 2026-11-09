package com.ieum.scheduler

import org.springframework.data.jpa.repository.JpaRepository

interface ShedlockRepository : JpaRepository<Shedlock, String>
