package com.fenixcore.optisaludplus.modules.scheduling.mapper;

import com.fenixcore.optisaludplus.modules.scheduling.dto.ScheduledJobRunDto;
import com.fenixcore.optisaludplus.modules.scheduling.entity.ScheduledJobRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduledJobRunMapper {

    @Mapping(target = "jobUuid", source = "scheduledJob.uuid")
    @Mapping(target = "jobCode", source = "scheduledJob.code")
    ScheduledJobRunDto toDto(ScheduledJobRun run);
}
