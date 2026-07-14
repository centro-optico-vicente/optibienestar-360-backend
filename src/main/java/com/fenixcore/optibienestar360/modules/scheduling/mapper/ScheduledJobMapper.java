package com.fenixcore.optibienestar360.modules.scheduling.mapper;

import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobDto;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScheduledJobMapper {

    ScheduledJobDto toDto(ScheduledJob job);
}
