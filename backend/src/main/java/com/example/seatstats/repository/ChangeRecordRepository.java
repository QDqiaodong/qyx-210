package com.example.seatstats.repository;

import com.example.seatstats.entity.ChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangeRecordRepository extends JpaRepository<ChangeRecord, Long> {

    List<ChangeRecord> findBySeatId(Long seatId);

    List<ChangeRecord> findByChangeType(String changeType);

    List<ChangeRecord> findByOperator(String operator);

    List<ChangeRecord> findBySeatIdOrderByChangeTimeDesc(Long seatId);

    List<ChangeRecord> findAllByOrderByChangeTimeDesc();
}