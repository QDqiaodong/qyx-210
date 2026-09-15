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

    /** 按封航批次号取出本次封航留下的整份挂载清单，复航逐把核对 */
    List<ChangeRecord> findBySuspendBatchNoOrderByIdAsc(String suspendBatchNo);

    /** 封航台账按时间倒序取出，服务层再按批次号聚合成“历次封航批次一览” */
    List<ChangeRecord> findByChangeTypeOrderByChangeTimeDescIdAsc(String changeType);
}
