package com.dsip.backend.whitelist;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface WhitelistedEmailMapper {

    Optional<WhitelistedEmail> findById(@Param("id") UUID id);

    Optional<WhitelistedEmail> findByEmail(@Param("email") String email);

    boolean existsByEmail(@Param("email") String email);

    List<WhitelistedEmail> findAll();

    int insert(WhitelistedEmail whitelistedEmail);

    int update(WhitelistedEmail whitelistedEmail);

    int deleteById(@Param("id") UUID id);

    int deleteByEmail(@Param("email") String email);
}
