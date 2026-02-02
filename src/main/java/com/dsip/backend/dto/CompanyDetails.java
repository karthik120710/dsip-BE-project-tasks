package com.dsip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for company information retrieved from external APIs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDetails {

    /**
     * Company name.
     */
    private String name;

    /**
     * Stock ticker symbol.
     */
    private String symbol;

    /**
     * Exchange where the stock is listed.
     */
    private String exchange;

    /**
     * Industry or sector.
     */
    private String industry;

    /**
     * Country of listing.
     */
    private String country;
}
