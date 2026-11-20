package dev.aymen.etaye.practices.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PracticeRepository extends JpaRepository<Practice, UUID> {
}
