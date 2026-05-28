package com.codedgiraffe.smartvolt.edge.buffer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BufferedReadingRepository extends JpaRepository<BufferedReading, Long> {

    List<BufferedReading> findTop100BySyncedFalseOrderByTimestampAsc();

    long countBySyncedFalse();
}
