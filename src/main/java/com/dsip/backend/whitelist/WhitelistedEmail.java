package com.dsip.backend.whitelist;

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
public class WhitelistedEmail {

    private UUID id;
    private String email;
    private String addedBy;
    private Instant createdAt;
}
