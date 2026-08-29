package com.fenixcore.optibienestar360.core.audit.repository;

import com.fenixcore.optibienestar360.core.audit.entity.EntityConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntityConfigRepository extends JpaRepository<EntityConfig, Long> {

	Optional<EntityConfig> findByEntityKey(String entityKey);

	List<EntityConfig> findAllByOrderByDisplayNameAsc();

}
