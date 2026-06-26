package com.iramil73.booking.repository;

import com.iramil73.booking.entity.Booking;
import com.iramil73.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Time-overlap test for a room: an existing booking with the given status
     * conflicts when {@code existing.start < newEnd AND existing.end > newStart}.
     */
    boolean existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
            Long roomId, BookingStatus status, LocalDateTime newEnd, LocalDateTime newStart);

    /** Same overlap test, excluding one booking (used when editing it). */
    boolean existsByRoomIdAndStatusAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(
            Long roomId, BookingStatus status, Long id, LocalDateTime newEnd, LocalDateTime newStart);

    List<Booking> findByUserIdOrderByStartTimeDesc(Long userId);

    @Query("""
            select b from Booking b
            where b.room.id = :roomId
              and b.status = :status
              and b.startTime >= :dayStart
              and b.startTime < :dayEnd
            order by b.startTime
            """)
    List<Booking> findForRoomOnDay(@Param("roomId") Long roomId,
                                   @Param("status") BookingStatus status,
                                   @Param("dayStart") LocalDateTime dayStart,
                                   @Param("dayEnd") LocalDateTime dayEnd);

    /** All bookings (any status, any room/user) starting within [from, to) — for the admin dashboard. */
    @Query("""
            select b from Booking b
              join fetch b.room
              join fetch b.user
            where b.startTime >= :from
              and b.startTime < :to
            order by b.startTime
            """)
    List<Booking> findAllInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
