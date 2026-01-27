package com.dsip.backend.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface UserMapper {

    Optional<User> findById(@Param("id") UUID id);

    Optional<User> findByEmail(@Param("email") String email);

    boolean existsByEmail(@Param("email") String email);

    List<User> findAll();

    int insert(User user);

    int update(User user);

    int deleteById(@Param("id") UUID id);
}
