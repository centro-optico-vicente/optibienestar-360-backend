package com.fenixcore.optibienestar360.modules.scheduling.mapper;

import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobDto;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduledJobMapper {

    // runnerClass / runnerRegistered aren't persisted columns — ScheduledJobsService
    // resolves them live against JobExecutionService and rebuilds the record with them set.
    @Mapping(target = "runnerClass", ignore = true)
    @Mapping(target = "runnerRegistered", ignore = true)
    ScheduledJobDto toDto(ScheduledJob job);
}
