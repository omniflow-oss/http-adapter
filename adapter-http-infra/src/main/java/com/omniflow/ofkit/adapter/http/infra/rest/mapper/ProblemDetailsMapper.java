package com.omniflow.ofkit.adapter.http.infra.rest.mapper;

import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.infra.rest.dto.ProblemDetailsDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface ProblemDetailsMapper {
    ProblemDetailsDto toDto(ProblemDetails domain);
    ProblemDetails toDomain(ProblemDetailsDto dto);
}

