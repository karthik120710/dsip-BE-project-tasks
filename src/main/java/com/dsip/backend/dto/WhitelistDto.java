package com.dsip.backend.dto;

import com.dsip.backend.entity.WhitelistedEmail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistDto {

    private UUID id;
    private String email;
    private String addedBy;
    private Instant createdAt;

    public static WhitelistDto fromEntity(WhitelistedEmail entity) {
        return WhitelistDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .addedBy(entity.getAddedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
