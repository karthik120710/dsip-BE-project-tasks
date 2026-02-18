package com.dsip.backend.mapper;

import com.dsip.backend.entity.WhitelistedEmail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MyBatis mapper interface for WhitelistedEmail entity.
 * Maps to WhitelistedEmailMapper.xml for SQL queries.
 */
@Mapper
public interface WhitelistedEmailMapper {

    /**
     * Finds a whitelisted email by its ID.
     *
     * @param id the whitelisted email's UUID
     * @return Optional containing the whitelisted email if found
     */
    Optional<WhitelistedEmail> findById(@Param("id") UUID id);

    /**
     * Finds a whitelisted email by the email address.
     *
     * @param email the email address
     * @return Optional containing the whitelisted email if found
     */
    Optional<WhitelistedEmail> findByEmail(@Param("email") String email);

    /**
     * Checks if an email address is whitelisted.
     *
     * @param email the email to check
     * @return true if email is whitelisted, false otherwise
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * Retrieves all whitelisted emails ordered by creation date descending.
     *
     * @return list of all whitelisted emails
     */
    List<WhitelistedEmail> findAll();

    /**
     * Inserts a new whitelisted email into the database.
     * The ID will be auto-generated if not provided.
     *
     * @param whitelistedEmail the whitelisted email to insert
     * @return number of rows affected (should be 1)
     */
    int insert(WhitelistedEmail whitelistedEmail);

    /**
     * Updates an existing whitelisted email's information.
     *
     * @param whitelistedEmail the whitelisted email with updated information
     * @return number of rows affected (should be 1)
     */
    int update(WhitelistedEmail whitelistedEmail);

    /**
     * Deletes a whitelisted email by its ID.
     *
     * @param id the whitelisted email's UUID
     * @return number of rows affected (should be 1)
     */
    int deleteById(@Param("id") UUID id);

    /**
     * Deletes a whitelisted email by its email address.
     *
     * @param email the email address to remove
     * @return number of rows affected (should be 1)
     */
    int deleteByEmail(@Param("email") String email);
}
