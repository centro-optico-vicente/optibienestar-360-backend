package com.fenixcore.optisaludplus.modules.scheduling.mapper;

import com.fenixcore.optisaludplus.modules.scheduling.dto.ScheduledJobDto;
import com.fenixcore.optisaludplus.modules.scheduling.entity.ScheduledJob;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScheduledJobMapper {

    ScheduledJobDto toDto(ScheduledJob job);
}
