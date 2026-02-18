package com.dsip.backend.mapper;

import com.dsip.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MyBatis mapper interface for User entity.
 * Maps to UserMapper.xml for SQL queries.
 */
@Mapper
public interface UserMapper {

    /**
     * Finds a user by their ID.
     *
     * @param id the user's UUID
     * @return Optional containing the user if found
     */
    Optional<User> findById(@Param("id") UUID id);

    /**
     * Finds a user by their email address.
     *
     * @param email the user's email
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Checks if a user exists with the given email.
     *
     * @param email the email to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * Retrieves all users ordered by creation date descending.
     *
     * @return list of all users
     */
    List<User> findAll();

    /**
     * Inserts a new user into the database.
     * The ID will be auto-generated if not provided.
     *
     * @param user the user to insert
     * @return number of rows affected (should be 1)
     */
    int insert(User user);

    /**
     * Updates an existing user's information.
     *
     * @param user the user with updated information
     * @return number of rows affected (should be 1)
     */
    int update(User user);

    /**
     * Deletes a user by their ID.
     *
     * @param id the user's UUID
     * @return number of rows affected (should be 1)
     */
    int deleteById(@Param("id") UUID id);
}
